# Google Drive real API setup

## What is in the repo now

- `DriveApi` + `DriveNetwork` — Retrofit client for Drive REST v3
- `GoogleDriveServiceImpl` — still uses offline/simulated auth until OAuth client is configured

## Required secrets (never commit)

1. Google Cloud Console → OAuth 2.0 Client ID (Android + optional Web for Credential Manager)
2. Restrict API key / enable **Google Drive API**
3. Put client id in local `.env` / CI secret, e.g.:

```
GOOGLE_WEB_CLIENT_ID=xxxxx.apps.googleusercontent.com
```

## Production path

1. Credential Manager → Google ID token / access token
2. Pass `Authorization: Bearer <access_token>` into `DriveApi`
3. Replace simulated delays in `GoogleDriveServiceImpl` with `DriveApi` calls
4. Rotate any leaked keys from secret scanning (see Security tab)

## CI

Release APK builds without real OAuth. Connect flow requires a device/emulator with Play Services and a valid client id.
