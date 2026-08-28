# Google Drive (production)

## Architecture

- `DriveApi` — Retrofit interface for Drive v3 (`files`, `about`, multipart upload, media download).
- `DriveNetwork` — OkHttp + Moshi; optional default Bearer via `setDefaultAccessToken`.
- `GoogleDriveServiceImpl` — production service: **no simulated files**. Requires OAuth access token.

## Wire OAuth (app module)

1. Create OAuth client in Google Cloud Console (Android package + SHA-1).
2. Use Credential Manager / Google Identity Services to obtain an access token with scope:
   - `https://www.googleapis.com/auth/drive.file` (recommended minimum), or
   - `https://www.googleapis.com/auth/drive` (full Drive — justify for Play review).
3. After sign-in:

```kotlin
val drive = (application as VVFApplication).googleDriveService as GoogleDriveServiceImpl
drive.setAccessToken(accessToken)
drive.authenticate() // verifies token via about.storageQuota
```

4. On sign-out: `drive.disconnect()`.

## Secrets

- Never commit OAuth client secrets or refresh tokens.
- Do not log Authorization headers (logging stays at NONE / BASIC only).
- Rotate any keys previously leaked in `google-services.json` (Secret Scanning).

## CI

Unit tests do not call live Drive. Integration tests need a test token injected via secrets (optional future job).
