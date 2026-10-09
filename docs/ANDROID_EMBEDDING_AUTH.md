# Android embedding authentication contract

## Client request contract

The Android app uses the existing embed worker:

- Base URL: `https://drive-semantic-embed.awasthi-sach.workers.dev`
- Health: `GET /`
- Embed: `POST /embed`
- Required authorization: `Authorization: Bearer <Firebase Auth ID token>`
- Required Android app attestation: `X-Firebase-AppCheck: <Firebase App Check token>`
- JSON body: `{"texts":["..."],"mode":"query|document","version":"3"}`

The client obtains the ID token from the currently signed-in Firebase user and the App Check token from the Firebase App Check SDK using Play Integrity. It verifies that the Firebase email matches the selected Google Drive account. It **does not** send the Drive OAuth access token, does not contain a Gemini API key, and does not spoof an `Origin` header.

## Worker requirements before Android embeddings can run

The worker must support native requests with no `Origin` header only when both the Firebase ID token and Firebase App Check token validate. Browser requests must continue to require an exact allow-listed `Origin` and a valid Firebase ID token. Missing or invalid mobile attestation must fail closed.

Configure these non-secret worker variables after registering the Android app in Firebase App Check:

- `FIREBASE_PROJECT_NUMBER`: the numeric project number from `project_info.project_number` in the matching `google-services.json`.
- `ANDROID_FIREBASE_APP_ID`: the Android app ID from `client[].client_info.mobilesdk_app_id` (format `1:<project-number>:android:<hash>`).

Enable Firebase App Check for the Android app with the Play Integrity provider and register the production signing certificate fingerprint. Do not enable an unverified debug provider for production. Deploy the worker only after its token-verification tests pass.

The worker health response must include `mobileAuthEnabled: true` in addition to `ok`, `model`, `version`, and `dimension`. The Android client refuses to call `POST /embed` unless that flag is true and the model contract matches `gemini-embedding-2`, version `3`, dimension `768`.

## Fail-closed behavior

If App Check cannot issue a token, account emails do not match, the worker omits `mobileAuthEnabled`, or the worker rejects authentication, the app sends no unauthenticated fallback request. Keyword and metadata search remain available. Tokens and embedding request bodies must never be written to logs or exported in local index backups.

## Deployment status

This document defines the Android side of the contract. The existing worker must be updated and its App Check variables configured before the neural feature can be enabled. The app intentionally treats the existing worker as **not Android-ready** until its health response confirms the new contract.
