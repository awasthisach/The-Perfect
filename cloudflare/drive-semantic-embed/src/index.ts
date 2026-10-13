export interface Env {
  FIREBASE_PROJECT_ID: string;
  FIREBASE_PROJECT_NUMBER: string;
  FIREBASE_APP_ID?: string;
  GEMINI_API_KEY: string;
  ALLOWED_ORIGIN: string;
  EMBEDDING_MODEL?: string;
  EMBEDDING_VERSION?: string;
  EMBEDDING_DIMENSION?: string;
  RATE_LIMITER: DurableObjectNamespace;
}

const FIREBASE_JWKS = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
const APPCHECK_JWKS = "https://firebaseappcheck.googleapis.com/v1/jwks";
const MAX_BODY_BYTES = 300_000;
const MAX_TEXTS = 32;
const MAX_TEXT_CHARS = 8_000;
const MAX_REQUESTS_PER_MINUTE = 30;

function json(data: unknown, status = 200, extra: HeadersInit = {}): Response {
  const headers = new Headers(extra);
  headers.set("Content-Type", "application/json; charset=utf-8");
  headers.set("Cache-Control", "no-store");
  return new Response(JSON.stringify(data), { status, headers });
}

function corsHeaders(origin: string | null, env: Env): Headers {
  const headers = new Headers({ "Vary": "Origin" });
  if (origin && origin === env.ALLOWED_ORIGIN) {
    headers.set("Access-Control-Allow-Origin", origin);
    headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
    headers.set("Access-Control-Allow-Headers", "Authorization, Content-Type, X-Firebase-AppCheck");
    headers.set("Access-Control-Max-Age", "600");
  }
  return headers;
}

function decodeBase64Url(input: string): Uint8Array {
  const padded = input.replace(/-/g, "+").replace(/_/g, "/") + "=".repeat((4 - input.length % 4) % 4);
  const raw = atob(padded);
  return Uint8Array.from(raw, (char) => char.charCodeAt(0));
}

function decodeJsonPart<T>(part: string): T {
  return JSON.parse(new TextDecoder().decode(decodeBase64Url(part))) as T;
}


const jwksCache = new Map<string, { expiresAt: number; keys: JsonWebKey[] }>();

async function getJwks(url: string, forceRefresh = false): Promise<JsonWebKey[] | null> {
  const cached = jwksCache.get(url);
  if (!forceRefresh && cached && cached.expiresAt > Date.now()) return cached.keys;
  try {
    const response = await fetch(url, { headers: { Accept: "application/json" } });
    if (!response.ok) return null;
    const keys = (await response.json() as { keys?: JsonWebKey[] }).keys;
    if (!Array.isArray(keys) || keys.length === 0) return null;
    const cacheControl = response.headers.get("Cache-Control") ?? "";
    const maxAgeMatch = cacheControl.match(/max-age=(\d+)/i);
    const serverMaxAge = maxAgeMatch ? Number(maxAgeMatch[1]) : 300;
    const ttlMs = Math.min(21_600, Math.max(0, serverMaxAge)) * 1000;
    jwksCache.set(url, { keys, expiresAt: Date.now() + ttlMs });
    return keys;
  } catch {
    return null;
  }
}

async function verifyJwt(
  token: string,
  jwksUrl: string,
  expectedIssuer: string,
  expectedAudience: string,
  expectedSubject?: string
): Promise<Record<string, unknown> | null> {
  try {
    const parts = token.split(".");
    if (parts.length !== 3) return null;
    const header = decodeJsonPart<{ alg?: string; kid?: string; typ?: string }>(parts[0]);
    if (header.alg !== "RS256" || !header.kid || (header.typ && header.typ !== "JWT")) return null;

    let keys = await getJwks(jwksUrl);
    if (!keys) return null;
    let jwk = keys.find((key) => (key as JsonWebKey & { kid?: string }).kid === header.kid);
    if (!jwk) {
      // Refresh once when Google rotates signing keys.
      keys = await getJwks(jwksUrl, true);
      jwk = keys?.find((key) => (key as JsonWebKey & { kid?: string }).kid === header.kid);
    }
    if (!jwk) return null;

    const publicKey = await crypto.subtle.importKey(
      "jwk", jwk, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["verify"]
    );
    const signatureValid = await crypto.subtle.verify(
      "RSASSA-PKCS1-v1_5",
      publicKey,
      decodeBase64Url(parts[2]),
      new TextEncoder().encode(parts[0] + "." + parts[1])
    );
    if (!signatureValid) return null;

    const claims = decodeJsonPart<Record<string, unknown>>(parts[1]);
    const now = Math.floor(Date.now() / 1000);
    const audience = claims.aud;
    const audienceValid = Array.isArray(audience)
      ? audience.includes(expectedAudience)
      : audience === expectedAudience;
    if (
      claims.iss !== expectedIssuer ||
      !audienceValid ||
      typeof claims.exp !== "number" ||
      claims.exp <= now ||
      typeof claims.iat !== "number" ||
      claims.iat > now + 60 ||
      typeof claims.sub !== "string" ||
      claims.sub.length === 0 ||
      (expectedSubject && claims.sub !== expectedSubject)
    ) return null;
    return claims;
  } catch {
    return null;
  }
}

async function authenticateFirebase(request: Request, env: Env): Promise<string | null> {
  const authorization = request.headers.get("Authorization") ?? "";
  const match = authorization.match(/^Bearer\s+(.+)$/i);
  if (!match) return null;
  const claims = await verifyJwt(
    match[1],
    FIREBASE_JWKS,
    `https://securetoken.google.com/${env.FIREBASE_PROJECT_ID}`,
    env.FIREBASE_PROJECT_ID
  );
  return typeof claims?.sub === "string" ? claims.sub : null;
}

async function verifyAppCheck(request: Request, env: Env): Promise<boolean> {
  // Bind App Check to this exact registered Android app; fail closed when not configured.
  if (!env.FIREBASE_APP_ID) return false;
  const token = request.headers.get("X-Firebase-AppCheck");
  if (!token) return false;
  const claims = await verifyJwt(
    token,
    APPCHECK_JWKS,
    `https://firebaseappcheck.googleapis.com/${env.FIREBASE_PROJECT_NUMBER}`,
    `projects/${env.FIREBASE_PROJECT_NUMBER}`,
    env.FIREBASE_APP_ID
  );
  return claims !== null;
}


async function readLimitedBody(request: Request, limit: number): Promise<string | null> {
  const declaredLength = Number(request.headers.get("Content-Length"));
  if (Number.isFinite(declaredLength) && declaredLength > limit) return null;
  if (!request.body) return "";
  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      total += value.byteLength;
      if (total > limit) {
        await reader.cancel();
        return null;
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }
  const merged = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    merged.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return new TextDecoder("utf-8", { fatal: true }).decode(merged);
}

async function rateLimit(uid: string, env: Env): Promise<boolean> {
  const id = env.RATE_LIMITER.idFromName(uid);
  const response = await env.RATE_LIMITER.get(id).fetch("https://rate-limit.internal/check", {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ limit: MAX_REQUESTS_PER_MINUTE })
  });
  return response.ok;
}


async function callGeminiWithRetry(env: Env, model: string, requests: unknown[]): Promise<Response> {
  let response: Response | null = null;
  for (let attempt = 0; attempt < 3; attempt++) {
    try {
      response = await fetch(`https://generativelanguage.googleapis.com/v1beta/models/${model}:batchEmbedContents`, {
        method: "POST",
        headers: { "Content-Type": "application/json", "x-goog-api-key": env.GEMINI_API_KEY },
        body: JSON.stringify({ requests })
      });
    } catch {
      if (attempt === 2) throw new Error("embedding_upstream_unavailable");
      await new Promise((resolve) => setTimeout(resolve, 150 * (2 ** attempt)));
      continue;
    }
    if (response.ok || ![429, 500, 502, 503].includes(response.status)) return response;
    if (attempt < 2) {
      await response.body?.cancel();
      await new Promise((resolve) => setTimeout(resolve, 150 * (2 ** attempt)));
    }
  }
  if (!response) throw new Error("embedding_upstream_unavailable");
  return response;
}

export class UserRateLimiter {
  constructor(private readonly state: DurableObjectState) {}

  async fetch(request: Request): Promise<Response> {
    if (request.method !== "POST") return new Response("Method not allowed", { status: 405 });
    const body = await request.json().catch(() => ({})) as { limit?: number };
    const limit = Math.min(60, Math.max(1, Number(body.limit) || MAX_REQUESTS_PER_MINUTE));
    const now = Date.now();
    const allowed = await this.state.storage.transaction(async (txn) => {
      let windowStart = await txn.get<number>("windowStart");
      let count = await txn.get<number>("count");
      if (!windowStart || now - windowStart >= 60_000 || now < windowStart) {
        windowStart = now;
        count = 0;
      }
      if ((count ?? 0) >= limit) return false;
      await txn.put("windowStart", windowStart);
      await txn.put("count", (count ?? 0) + 1);
      return true;
    });
    return new Response(null, { status: allowed ? 204 : 429 });
  }
}

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const origin = request.headers.get("Origin");
    const cors = corsHeaders(origin, env);
    const url = new URL(request.url);

    if (request.method === "OPTIONS") {
      if (!origin || origin !== env.ALLOWED_ORIGIN) return json({ error: "origin_not_allowed" }, 403);
      return new Response(null, { status: 204, headers: cors });
    }

    if (request.method === "GET" && url.pathname === "/") {
      const dimension = Number(env.EMBEDDING_DIMENSION || 768);
      const configured = Boolean(
        env.FIREBASE_PROJECT_ID &&
        env.FIREBASE_PROJECT_NUMBER &&
        env.FIREBASE_APP_ID &&
        env.GEMINI_API_KEY &&
        Number.isInteger(dimension) &&
        dimension === 768
      );
      return json({
        ok: configured,
        model: env.EMBEDDING_MODEL || "gemini-embedding-2",
        version: env.EMBEDDING_VERSION || "3",
        dimension
      }, configured ? 200 : 503, cors);
    }

    if (request.method !== "POST" || url.pathname !== "/embed") {
      return json({ error: "not_found" }, 404, cors);
    }

    // Browser calls must originate from the configured web origin. Native calls have no
    // browser Origin and must instead prove app authenticity with Firebase App Check.
    if (origin && origin !== env.ALLOWED_ORIGIN) return json({ error: "origin_not_allowed" }, 403, cors);
    const uid = await authenticateFirebase(request, env);
    if (!uid) return json({ error: "unauthorized" }, 401, cors);
    // Rate-limit authenticated identities before App Check/JWKS fetches and body parsing.
    if (!(await rateLimit(uid, env))) return json({ error: "rate_limited" }, 429, cors);
    if (!origin && !(await verifyAppCheck(request, env))) {
      return json({ error: "app_check_required" }, 403, cors);
    }

    let rawBody: string | null;
    try {
      rawBody = await readLimitedBody(request, MAX_BODY_BYTES);
    } catch {
      return json({ error: "invalid_body" }, 400, cors);
    }
    if (rawBody === null) return json({ error: "body_too_large" }, 413, cors);

    let body: { texts?: unknown; mode?: unknown; version?: unknown };
    try {
      body = JSON.parse(rawBody);
    } catch {
      return json({ error: "invalid_json" }, 400, cors);
    }
    if (!Array.isArray(body.texts) || body.texts.length < 1 || body.texts.length > MAX_TEXTS) {
      return json({ error: "invalid_text_count" }, 400, cors);
    }
    if (body.texts.some((value) => typeof value !== "string" || value.trim().length === 0 || value.length > MAX_TEXT_CHARS)) {
      return json({ error: "invalid_text" }, 400, cors);
    }
    const mode = body.mode === "query" ? "query" : body.mode === "document" ? "document" : null;
    if (!mode) return json({ error: "invalid_mode" }, 400, cors);
    const version = String(body.version ?? env.EMBEDDING_VERSION ?? "3");
    if (version !== "2" && version !== "3") return json({ error: "invalid_version" }, 400, cors);
    const model = env.EMBEDDING_MODEL || "gemini-embedding-2";
    const dimension = Number(env.EMBEDDING_DIMENSION || 768);
    if (!env.GEMINI_API_KEY || !env.FIREBASE_PROJECT_ID || !Number.isInteger(dimension) || dimension !== 768) {
      return json({ error: "server_not_configured" }, 503, cors);
    }

    const requests = (body.texts as string[]).map((text) => {
      const prefixedText = mode === "query"
        ? "task: search result | query: " + text
        : version === "2" ? "title: none | text: " + text : text;
      return {
        model: `models/${model}`,
        content: { parts: [{ text: prefixedText }] },
        outputDimensionality: dimension
      };
    });

    let upstream: Response;
    try {
      upstream = await callGeminiWithRetry(env, model, requests);
    } catch {
      return json({ error: "embedding_upstream_unavailable" }, 502, cors);
    }
    if (!upstream.ok) {
      const status = upstream.status === 429 ? 429 : upstream.status >= 500 ? 503 : 502;
      return json({ error: "embedding_upstream_failed", upstreamStatus: upstream.status }, status, cors);
    }

    const payload = await upstream.json().catch(() => null) as {
      embeddings?: Array<{ values?: unknown }>;
    } | null;
    const embeddings = payload?.embeddings?.map((item) => item.values);
    if (
      !Array.isArray(embeddings) ||
      embeddings.length !== requests.length ||
      embeddings.some((vector) =>
        !Array.isArray(vector) ||
        vector.length !== dimension ||
        vector.some((value) => typeof value !== "number" || !Number.isFinite(value))
      )
    ) return json({ error: "invalid_embedding_response" }, 502, cors);

    return json({ embeddings, model, version, dimension }, 200, cors);
  }
};
