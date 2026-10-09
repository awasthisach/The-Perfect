# Android embedding authentication contract

## Current release behavior

The native app records embedding consent, but it does not send embedding requests yet. The existing Cloudflare Worker currently requires an allow-listed browser `Origin` on `POST /embed`; a native Android request has no browser Origin and must not spoof one. The app therefore keeps neural embedding requests disabled until the Worker explicitly supports and verifies Android App Check.

Keyword, extracted-text, and metadata search continue to work locally without embeddings.

## Required native request contract

- Base URL: `https://drive-semantic-embed.awasthi-sach.workers.dev`
- Health: `GET /`
- Embed: `POST /embed`
- `Authorization: Bearer <Firebase Auth ID token>`
- `X-Firebase-AppCheck: <Firebase App Check token>`
- JSON: `{"texts":["..."],"mode":"query|document","version":"3"}`

The Android client must obtain the ID token from the currently signed-in Firebase user, obtain the App Check token from the Firebase App Check SDK using Play Integrity, and verify that the Firebase email matches the selected Google Drive account. The Drive OAuth access token must never be used as the Worker bearer token. No Gemini API key belongs in the APK.

## Worker changes required before neural search can be enabled

1. Keep browser requests restricted to the exact allow-listed `Origin`; do not allow browser requests to bypass CORS/authentication.
2. Permit a request without `Origin` only when it includes both a valid Firebase ID token and a valid Android Firebase App Check token.
3. Verify the App Check JWT signature with `https://firebaseappcheck.googleapis.com/v1/jwks`; require `alg=RS256`, `typ=JWT`, issuer `https://firebaseappcheck.googleapis.com/<project-number>`, audience containing `projects/<project-number>`, unexpired `exp`, and a `sub` matching the explicitly allow-listed production Android Firebase App ID.
4. Continue verifying Firebase ID token `aud`, `iss`, `sub`, `iat`, `exp`, and signature, and keep per-user rate limiting.
5. Add non-secret Worker variables `FIREBASE_PROJECT_NUMBER` and `ANDROID_FIREBASE_APP_ID`; leave mobile auth disabled if either is missing.
6. Return `mobileAuthEnabled: true` from `GET /` only after the native verification path is configured. The app must not infer readiness from the old `ok/model/version/dimension` fields alone.
7. Test missing/invalid App Check (401/403), invalid Firebase ID token (401), wrong app ID (401/403), allow-listed browser Origin, missing browser Origin without App Check (403), rate limiting, and a valid Android request. Never log tokens or request text.

Register the Android app in Firebase App Check, use Play Integrity for production, and register the production signing certificate fingerprint. Do not enable a debug provider in production.

## Release gate

Until the Worker health response advertises `mobileAuthEnabled: true` and the valid native path passes live tests, neural embedding generation and semantic vector ranking remain disabled. The app must continue to show local keyword/metadata results in that state. This fail-closed behavior is intentional, not a fake neural fallback.
