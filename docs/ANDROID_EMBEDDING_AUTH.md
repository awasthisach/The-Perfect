# Android embedding authentication and consent contract

## Non-negotiable security rules

- The Android APK must never contain a Gemini API key or another server secret.
- The Drive OAuth access token is only for Google Drive APIs. It must never be sent to the embedding service.
- Embedding requests require the Firebase Auth ID token for the signed-in Firebase user as `Authorization: Bearer <firebase-id-token>`.
- Neural indexing and query embedding must remain disabled until the user accepts a clear, revocable consent screen.
- If consent is off, missing, or revoked, search must still return local keyword/full-text/metadata matches.
- Never log Google access tokens, Firebase ID tokens, credential payloads, or extracted document contents.

## Current worker contract

Base URL: `https://drive-semantic-embed.awasthi-sach.workers.dev`

- `GET /` should report `{ ok, model, version, dimension }`; expected candidate values are model `gemini-embedding-2`, version `4`, dimension `768`. Version 4 uses Gemini Embedding 2's retrieval document prefix; version-3 document vectors must be regenerated.
- `POST /embed` authenticates the Firebase ID token and rejects missing or invalid JWTs with 401.
- Protected deployment workflow [#38676382484](https://github.com/awasthisach/The-Perfect/actions/runs/38676382484) verified the Worker v4 health contract (`gemini-embedding-2`, 768 dimensions) and rejection of unauthenticated `/embed` requests. This is not evidence of a successful native request with valid Firebase Auth + App Check.
- Every `/embed` caller must present a valid Firebase Auth ID token and Firebase App Check token in separate headers, regardless of `Origin`. The Worker fails closed if `FIREBASE_APP_ID` is missing and rejects missing/invalid App Check using 403. Real valid-token acceptance and invalid-App-Check rejection still need a recorded device/API acceptance test.

## Mobile transport — implementation vs deployment

The native client now sends both the Firebase Auth ID token and an Android Firebase App Check (Play Integrity) token. It deliberately sends no `Origin` header and never sends the Drive access token or Gemini API key.

The repository includes the Worker-side App Check path in `cloudflare/drive-semantic-embed/src/index.ts`, including Firebase JWT verification, per-user Durable Object rate limiting, bounded request bodies, model/version/dimension validation, JWKS caching, and transient upstream retries. The Worker also has an operator approval gate: `GEMINI_PRODUCTION_APPROVED` defaults to `false`; while false, health returns HTTP 503 and `/embed` returns `production_approval_required`. Deployment with embeddings disabled is allowed. Enabling embeddings requires the configuration flag and explicit dispatch confirmation after verifying paid Gemini billing and provider data-handling terms. Current billing, deployed Worker revision, and account-level configuration require owner evidence; source code does not establish them. Keep the flag false until that evidence is available. Until billing/data handling is verified, App Check is registered with the actual Play App Signing SHA-256, and the reviewed Worker is deployed, neural embedding must remain unavailable. Local keyword/full-text/metadata search must continue to work.

Do not use or trust an `Origin` header as proof of app identity. Origin is a browser CORS control and can be spoofed by non-browser clients; every caller must pass both Firebase Auth and App Check verification. Native calls intentionally omit `Origin`.

## Release acceptance tests

1. Consent defaults to off on a fresh install; turning it off stops new embedding requests.
2. A native request without Origin succeeds only with valid Firebase Auth and App Check; a missing/invalid token is rejected. Browser requests remain limited to the configured CORS origin.
3. Proxy/worker rejects missing, expired, or invalid Firebase ID tokens.
4. The request uses the Firebase ID token, never the Drive access token.
5. No API key is present in source, resources, BuildConfig, APK, logs, backups, or exported index.
6. Embedding timeout/401/403/5xx leaves local keyword and metadata search operational.
7. Consent revocation cancels background embedding work, prevents future requests, clears the in-memory cache, and deletes persisted vectors from the encrypted SQLCipher Room table.
