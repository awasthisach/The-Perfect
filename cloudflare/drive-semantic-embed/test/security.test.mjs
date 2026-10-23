import { after, before, test } from "node:test";
import assert from "node:assert/strict";
import { webcrypto } from "node:crypto";
import worker from "../src/index.ts";

const AUTH_JWKS_URL = "https://www.googleapis.com/service_accounts/v1/jwk/securetoken@system.gserviceaccount.com";
const APPCHECK_JWKS_URL = "https://firebaseappcheck.googleapis.com/v1/jwks";
const PROJECT_ID = "worker-auth-test-project";
const PROJECT_NUMBER = "123456789";
const FIREBASE_APP_ID = `1:${PROJECT_NUMBER}:android:test-app-id`;
const ALLOWED_ORIGIN = "https://allowed.example.test";
const encoder = new TextEncoder();

let authKeys;
let appCheckKeys;
let authToken;
let appCheckToken;
let originalFetch;
let geminiCalls;

function base64Url(value) {
  return Buffer.from(value).toString("base64url");
}

async function createSigningKeys(kid) {
  const pair = await webcrypto.subtle.generateKey(
    { name: "RSASSA-PKCS1-v1_5", modulusLength: 2048, publicExponent: new Uint8Array([1, 0, 1]), hash: "SHA-256" },
    true,
    ["sign", "verify"]
  );
  const jwk = await webcrypto.subtle.exportKey("jwk", pair.publicKey);
  return {
    privateKey: pair.privateKey,
    jwk: { ...jwk, kid, alg: "RS256", use: "sig" }
  };
}

async function signJwt(privateKey, kid, claims) {
  const encodedHeader = base64Url(JSON.stringify({ alg: "RS256", typ: "JWT", kid }));
  const encodedPayload = base64Url(JSON.stringify(claims));
  const signingInput = `${encodedHeader}.${encodedPayload}`;
  const signature = await webcrypto.subtle.sign(
    "RSASSA-PKCS1-v1_5",
    privateKey,
    encoder.encode(signingInput)
  );
  return `${signingInput}.${base64Url(signature)}`;
}

function claims(issuer, audience, subject) {
  const now = Math.floor(Date.now() / 1000);
  return { iss: issuer, aud: audience, sub: subject, iat: now, exp: now + 300 };
}

function makeEnv() {
  return {
    FIREBASE_PROJECT_ID: PROJECT_ID,
    FIREBASE_PROJECT_NUMBER: PROJECT_NUMBER,
    FIREBASE_APP_ID,
    GEMINI_API_KEY: "test-only-not-a-real-secret",
    GEMINI_PRODUCTION_APPROVED: "true",
    ALLOWED_ORIGIN,
    EMBEDDING_MODEL: "gemini-embedding-2",
    EMBEDDING_VERSION: "4",
    EMBEDDING_DIMENSION: "768",
    RATE_LIMITER: {
      idFromName: () => "test-user",
      get: () => ({ fetch: async () => new Response(null, { status: 204 }) })
    }
  };
}

function makeRequest(headers = {}) {
  return new Request("https://worker.example.test/embed", {
    method: "POST",
    headers: { "Content-Type": "application/json", ...headers },
    body: JSON.stringify({ texts: ["safe test text"], mode: "query", version: "4" })
  });
}

async function responseError(response) {
  return (await response.json()).error;
}

before(async () => {
  const authPair = await createSigningKeys("test-auth-kid");
  const appCheckPair = await createSigningKeys("test-appcheck-kid");
  authKeys = authPair.jwk;
  appCheckKeys = appCheckPair.jwk;
  authToken = await signJwt(
    authPair.privateKey,
    authPair.jwk.kid,
    claims(`https://securetoken.google.com/${PROJECT_ID}`, PROJECT_ID, "test-user")
  );
  appCheckToken = await signJwt(
    appCheckPair.privateKey,
    appCheckPair.jwk.kid,
    claims(`https://firebaseappcheck.googleapis.com/${PROJECT_NUMBER}`, `projects/${PROJECT_NUMBER}`, FIREBASE_APP_ID)
  );

  geminiCalls = 0;
  originalFetch = globalThis.fetch;
  globalThis.fetch = async (input) => {
    const url = String(input);
    if (url === AUTH_JWKS_URL) return Response.json({ keys: [authKeys] });
    if (url === APPCHECK_JWKS_URL) return Response.json({ keys: [appCheckKeys] });
    if (url.includes("generativelanguage.googleapis.com")) {
      geminiCalls += 1;
      return Response.json({ embeddings: [{ values: Array(768).fill(0.125) }] });
    }
    throw new Error(`Unexpected outbound request: ${url}`);
  };
});

after(() => {
  globalThis.fetch = originalFetch;
});

test("rejects a missing Firebase Auth token", async () => {
  const response = await worker.fetch(makeRequest(), makeEnv());
  assert.equal(response.status, 401);
  assert.equal(await responseError(response), "unauthorized");
});

test("rejects missing App Check even when the allowed Origin is present", async () => {
  const response = await worker.fetch(makeRequest({
    Authorization: `Bearer ${authToken}`,
    Origin: ALLOWED_ORIGIN
  }), makeEnv());
  assert.equal(response.status, 403);
  assert.equal(await responseError(response), "app_check_required");
  assert.equal(geminiCalls, 0);
});

test("rejects an invalid App Check token even when the allowed Origin is present", async () => {
  const response = await worker.fetch(makeRequest({
    Authorization: `Bearer ${authToken}`,
    "X-Firebase-AppCheck": "not-a-valid-jwt",
    Origin: ALLOWED_ORIGIN
  }), makeEnv());
  assert.equal(response.status, 403);
  assert.equal(await responseError(response), "app_check_required");
  assert.equal(geminiCalls, 0);
});

test("rejects an unapproved Origin before any upstream call", async () => {
  const response = await worker.fetch(makeRequest({
    Authorization: `Bearer ${authToken}`,
    "X-Firebase-AppCheck": appCheckToken,
    Origin: "https://attacker.example.test"
  }), makeEnv());
  assert.equal(response.status, 403);
  assert.equal(await responseError(response), "origin_not_allowed");
  assert.equal(geminiCalls, 0);
});

test("accepts valid Firebase Auth plus App Check and returns validated embeddings", async () => {
  const response = await worker.fetch(makeRequest({
    Authorization: `Bearer ${authToken}`,
    "X-Firebase-AppCheck": appCheckToken
  }), makeEnv());
  const payload = await response.json();
  assert.equal(response.status, 200);
  assert.equal(payload.version, "4");
  assert.equal(payload.dimension, 768);
  assert.equal(payload.embeddings.length, 1);
  assert.equal(payload.embeddings[0].length, 768);
  assert.equal(geminiCalls, 1);
});

for (const body of ["null", "[]", "42", '"text"', "{}", "{", '{"texts":[null],"mode":"query"}', '{"texts":[],"mode":"query"}', '{"texts":["ok"],"mode":null}', '{"texts":["ok"],"mode":"query","version":4}']) {
  test(`rejects invalid authenticated JSON body: ${body}`, async () => {
    const beforeCalls = geminiCalls;
    const response = await worker.fetch(new Request("https://worker.example/embed", {
      method: "POST", headers: { Authorization: `Bearer ${authToken}`, "X-Firebase-AppCheck": appCheckToken }, body
    }), makeEnv());
    assert.equal(response.status, 400);
    assert.equal(typeof (await response.json()).error, "string");
    assert.equal(geminiCalls, beforeCalls);
  });
}

test("rejects invalid Firebase Auth even with valid App Check", async () => {
  const response = await worker.fetch(makeRequest({
    Authorization: "Bearer invalid", "X-Firebase-AppCheck": appCheckToken
  }), makeEnv());
  assert.equal(response.status, 401);
  assert.equal(await responseError(response), "unauthorized");
});


test("invalid App Check cannot exhaust the authenticated user's quota", async () => {
  const env = makeEnv();
  let consumed = 0;
  env.RATE_LIMITER.get = () => ({ fetch: async () => {
    consumed += 1;
    return new Response(null, { status: consumed <= 30 ? 204 : 429 });
  } });
  for (let i = 0; i < 31; i++) {
    const headers = { Authorization: `Bearer ${authToken}` };
    if (i % 2) headers["X-Firebase-AppCheck"] = "invalid";
    assert.equal((await worker.fetch(makeRequest(headers), env)).status, 403);
  }
  assert.equal(consumed, 0);
  const headers = { Authorization: `Bearer ${authToken}`, "X-Firebase-AppCheck": appCheckToken };
  assert.equal((await worker.fetch(makeRequest(headers), env)).status, 200);
  assert.equal(consumed, 1);
  consumed = 30;
  assert.equal((await worker.fetch(makeRequest(headers), env)).status, 429);
});
