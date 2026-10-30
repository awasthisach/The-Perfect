# Google Drive (production)

## Architecture

| Piece | Role |
|-------|------|
| `DriveApi` / `DriveNetwork` | Drive v3 REST (list, about, upload, download) |
| `GoogleDriveServiceImpl` | Service; requires access token via `setAccessToken` |
| `GoogleDriveAuth` | Credential Manager / Google Sign-In token acquisition; includes a session-checked silent refresh helper |

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


## Token refresh limitation (current implementation)

`GoogleDriveAuth.silentRefreshDriveAccessToken()` can obtain a fresh Play Services-managed access token for a matching existing Google + Firebase session. However, this helper is **not wired into `GoogleDriveServiceImpl` request execution**. `GoogleDriveServiceImpl.bearer()` is synchronous and fails closed once the installed token is older than 55 minutes, asking the user to sign in again. Therefore automatic refresh during Drive API calls is **not implemented**; the supported current behavior is explicit re-authentication after token expiry. Do not advertise seamless/background token refresh until the auth lifecycle is integrated and tested with a real Google account.
