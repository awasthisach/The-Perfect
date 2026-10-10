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

## Mobile transport plan — do not enable direct calls yet

Preferred production option: add a small authenticated proxy under the existing trusted backend. The proxy verifies Firebase ID tokens, applies per-user rate limits, validates request size/model/version, and calls the existing embedding worker with a server-controlled allowed Origin. The proxy secret must live only in server-side secret storage.

Alternative: extend the worker to verify Android App Check (Play Integrity) in addition to Firebase Auth and explicitly allow the mobile client path. Do not accept a caller-provided Origin as proof of app identity. Do not weaken the existing 401/403 checks.

Until one of those server-side options is deployed and tested, the Android client must not call `/embed` directly. Keep the consent UI and local search usable while embedding is unavailable.

## Release acceptance tests

1. Consent defaults to off on a fresh install; turning it off stops new embedding requests.
2. Direct worker call without an allowed Origin is rejected; the app does not try to bypass this.
3. Proxy/worker rejects missing, expired, or invalid Firebase ID tokens.
4. The request uses the Firebase ID token, never the Drive access token.
5. No API key is present in source, resources, BuildConfig, APK, logs, backups, or exported index.
6. Embedding timeout/401/403/5xx leaves local keyword and metadata search operational.
7. Consent revocation prevents future requests and removes local vectors if the user chooses to delete them.
