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

- `GET /` should report `{ ok, model, version, dimension }`; expected current values are model `gemini-embedding-2`, version `3`, dimension `768`.
- `POST /embed` authenticates the Firebase ID token and rejects missing or invalid JWTs with 401.
- The worker rejects a missing or unapproved Origin with 403. Native Android requests do not reliably have a browser Origin, so the app must not spoof an Origin header to bypass this control.

## Mobile transport — implementation vs deployment

The native client now sends both the Firebase Auth ID token and an Android Firebase App Check (Play Integrity) token. It deliberately sends no `Origin` header and never sends the Drive access token or Gemini API key.

The repository includes the Worker-side App Check path in `cloudflare/drive-semantic-embed/src/index.ts`, including Firebase JWT verification, per-user Durable Object rate limiting, bounded request bodies, model/version/dimension validation, JWKS caching, and transient upstream retries. The production Worker has **not** been updated by this source change. Until the Worker source is deployed and the real authenticated mobile request is tested, native embedding requests may still be rejected by the currently deployed Origin-only check. This is fail-closed: local keyword/full-text/metadata search must continue to work.

Do not spoof an allowed `Origin` header to bypass the deployed worker's current policy. Origin is a browser CORS control, not proof of app identity. Native calls without Origin must pass both Firebase Auth and App Check verification.

## Release acceptance tests

1. Consent defaults to off on a fresh install; turning it off stops new embedding requests.
2. Direct worker call without an allowed Origin is rejected; the app does not try to bypass this.
3. Proxy/worker rejects missing, expired, or invalid Firebase ID tokens.
4. The request uses the Firebase ID token, never the Drive access token.
5. No API key is present in source, resources, BuildConfig, APK, logs, backups, or exported index.
6. Embedding timeout/401/403/5xx leaves local keyword and metadata search operational.
7. Consent revocation prevents future embedding requests and clears the in-memory vector cache. The current implementation does not persist a vector index to disk.
