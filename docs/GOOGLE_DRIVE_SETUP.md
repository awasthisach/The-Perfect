# Google Drive (production)

## Architecture

| Piece | Role |
|-------|------|
| `DriveApi` / `DriveNetwork` | Drive v3 REST (list, about, upload, download) |
| `GoogleDriveServiceImpl` | Service; requires access token via `setAccessToken` |
| `GoogleDriveAuth` | Credential Manager (ID token) + Google Sign-In (Drive access token) skeleton |

## OAuth setup (Google Cloud Console)

1. Create project → enable **Google Drive API**.
2. OAuth consent screen (External or Internal).
3. Credentials:
   - **Android** client: package `com.vvf.smartmanager` + release/debug SHA-1.
   - **Web** client: use its client ID as `serverClientId` / `requestIdToken`.
4. Scope (recommended): `https://www.googleapis.com/auth/drive.file`.

## Wire in Activity / Compose

```kotlin
// BuildConfig or secrets plugin — never hardcode production secrets in git
val serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
val auth = GoogleDriveAuth(context, serverClientId)
val drive = (application as VVFApplication).googleDriveService as GoogleDriveServiceImpl

val launcher = rememberLauncherForActivityResult(
    ActivityResultContracts.StartActivityForResult()
) { result ->
    lifecycleScope.launch {
        auth.extractAccessTokenFromSignInResult(result.data)
            .onSuccess { token ->
                drive.setAccessToken(token)
                drive.authenticate()
            }
            .onFailure { /* show snackbar */ }
    }
}

// On Connect button:
launcher.launch(auth.buildDriveSignInIntent())

// Optional: account picker only (ID token — not Drive REST):
// auth.requestGoogleIdToken()
```

## Sign-out

```kotlin
drive.disconnect()
auth.signOut()
```

## Secrets

- Do not commit OAuth client secrets or tokens.
- Rotate any keys previously exposed in `google-services.json` (Secret Scanning).
- OkHttp logging stays at NONE/BASIC — never BODY.

## CI

Unit tests do not call live Drive. Optional future: secret-backed integration job.
