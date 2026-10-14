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

- `GET /` should report `{ ok, model, version, dimension }`; expected current values are model `gemini-embedding-2`, version `4`, dimension `768`.
- `POST /embed` authenticates the Firebase ID token and rejects missing or invalid JWTs with 401.
- The currently deployed Worker rejects a missing or unapproved Origin with 403. The repository Worker source adds a native path that accepts a missing Origin only when Firebase Auth and Android App Check are valid; the source must be deployed before native requests can succeed.

## Mobile transport — implementation vs deployment

The native client now sends both the Firebase Auth ID token and an Android Firebase App Check (Play Integrity) token. It deliberately sends no `Origin` header and never sends the Drive access token or Gemini API key.

The current candidate uses embedding format version 4 because Gemini retrieval now prefixes document text consistently; all version-3 cached vectors must be regenerated. The repository includes the Worker-side App Check path in `cloudflare/drive-semantic-embed/src/index.ts`, including Firebase JWT verification, per-user Durable Object rate limiting, bounded request bodies, model/version/dimension validation, JWKS caching, and transient upstream retries. The production Worker has **not** been updated by this source change. Until the Worker source is deployed and the real authenticated mobile request is tested, native embedding requests may still be rejected by the currently deployed Origin-only check. This is fail-closed: local keyword/full-text/metadata search must continue to work.

Do not spoof an allowed `Origin` header to bypass the deployed worker's current policy. Origin is a browser CORS control, not proof of app identity. Native calls without Origin must pass both Firebase Auth and App Check verification.

## Release acceptance tests

1. Consent defaults to off on a fresh install; turning it off stops new embedding requests.
2. A native request without Origin succeeds only with valid Firebase Auth and App Check; a missing/invalid token is rejected. Browser requests remain limited to the configured CORS origin.
3. Proxy/worker rejects missing, expired, or invalid Firebase ID tokens.
4. The request uses the Firebase ID token, never the Drive access token.
5. No API key is present in source, resources, BuildConfig, APK, logs, backups, or exported index.
6. Embedding timeout/401/403/5xx leaves local keyword and metadata search operational.
7. Consent revocation cancels background embedding work, prevents future requests, clears the in-memory cache, and deletes persisted vectors from the encrypted SQLCipher Room table.
