# Drive Semantic Search embedding Worker

This directory contains the source intended for the existing production Worker:

`https://drive-semantic-embed.awasthi-sach.workers.dev`

## Security model

- Gemini API credentials exist only as Cloudflare Worker secrets; never place them in Android resources, `BuildConfig`, or source control.
- Every `POST /embed` request requires a Firebase Auth ID token in `Authorization: Bearer …`.
- Browser requests must send the exact configured `Origin`; unapproved origins are rejected.
- Native requests omit `Origin` and must include a valid `X-Firebase-AppCheck` token. The token must be issued for the configured Firebase project and exact Android Firebase App ID.
- Requests are rate-limited per Firebase UID and the request body is streamed with a 300 KB limit.
- Maximum batch: 32 texts; maximum text length: 8,000 characters; expected vector size: 768.
- The Android client never sends the Drive access token to this service.

## Production deployment

The repository has a manually triggered GitHub Actions workflow at
`.github/workflows/deploy-embedding-worker.yml`. It deploys only after an explicit `yes` confirmation.

Configure these repository Actions secrets before running it:

1. `CLOUDFLARE_API_TOKEN` — a narrowly scoped token that can edit Workers and Durable Objects for the target account.
2. `CLOUDFLARE_ACCOUNT_ID` — the Cloudflare account ID containing the existing Worker.
3. `GEMINI_API_KEY` — the server-side Gemini API key; this is copied to Cloudflare Worker secret storage.
4. `FIREBASE_APP_ID` — the exact Firebase App ID for Android package `com.vvf.smartmanager` (format similar to `1:PROJECT_NUMBER:android:...`); it is used to bind App Check tokens to this app.

The project ID and project number are configured in `wrangler.toml`. Review those values and the allowed web origin before deployment. The deployment workflow updates the Worker secrets, deploys the Worker, and checks the public health contract. A passing health check alone does not prove that authenticated embedding calls work.

## Firebase App Check setup

Before expecting native embedding requests to succeed:

1. In Firebase Console, register the Android app `com.vvf.smartmanager` under **App Check**.
2. Configure Play Integrity for the actual app signing certificate and distribution channel. Register the correct SHA-256 certificate fingerprint.
3. Confirm the app is using the same Firebase project as the Worker.
4. Add the exact Android Firebase App ID as the `FIREBASE_APP_ID` repository secret.
5. Deploy the Worker, then test a real signed-in Android device with embedding consent enabled.

Do not add a permissive fake `Origin` header to Android requests. If Firebase App Check or Firebase Auth verification fails, neural search must fail closed and local keyword/full-text search must remain available.

## Local contract check

The public `GET /` endpoint should return:

```json
{
  "ok": true,
  "model": "gemini-embedding-2",
  "version": "4",
  "dimension": 768
}
```

Authenticated `POST /embed` accepts `texts`, `mode` (`query` or `document`), and `version`. It returns an `embeddings` array with one finite 768-dimensional vector per input text.
