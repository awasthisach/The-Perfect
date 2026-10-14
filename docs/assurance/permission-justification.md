# Android permission and Google Drive scope justification

**Package:** `com.vvf.smartmanager`  
**Current product:** Drive Semantic Search — native Kotlin/Jetpack Compose Android app

## Android manifest permissions

The current `app/src/main/AndroidManifest.xml` requests only:

| Permission | Purpose |
|---|---|
| `INTERNET` | Google Drive sync and optional authenticated embedding requests over HTTPS |
| `ACCESS_NETWORK_STATE` | Network-aware sync/index work |
| `USE_BIOMETRIC` | User-initiated vault unlock |
| `USE_FINGERPRINT` (max SDK 28) | Legacy biometric compatibility on older Android versions |

The manifest does **not** request `MANAGE_EXTERNAL_STORAGE`, `READ_EXTERNAL_STORAGE`, or `READ_MEDIA_*`. Drive documents are accessed through Google OAuth; local indexes, extracted text, vector cache, and encrypted databases remain in app-private storage. No Google Play All-files-access declaration is required for this manifest. Re-review this conclusion if any Android storage permission is added later.

## Google Drive OAuth scopes

The app requests the least-privilege combination needed for its current product behavior:

- `https://www.googleapis.com/auth/drive.readonly` — list and download the user's Drive corpus for indexing/search.
- `https://www.googleapis.com/auth/drive.file` — access files created by this app, including user-requested cloud backup artifacts.

The app does not request the full `https://www.googleapis.com/auth/drive` management scope. Cloud search and restore must never trash or delete source Drive files.

Google classifies `drive.readonly` as a **restricted scope**. Before public distribution, the owner must complete the applicable Google OAuth restricted-scope verification. Because optional neural search sends user-consented query/extracted text through the Cloudflare Worker to Gemini, the owner must also confirm the applicable Google API User Data Policy, disclosure, and restricted-scope security-assessment requirements.

Official references:
- https://developers.google.com/workspace/drive/api/guides/api-specific-auth
- https://support.google.com/cloud/answer/13465431

## Fail-closed requirements

- If Google/Firebase identities do not match, clear the in-memory Drive access token and reject the session.
- If neural embedding consent is off or the authenticated Worker is unavailable, use local keyword/full-text/metadata search only.
- Revoking neural consent cancels pending embedding requests, cancels indexing work, and deletes persisted vectors.
- Drive index import/export is user-directed; it never exports OAuth tokens, Firebase tokens, API keys, or source file bytes.
- Cloud restore requires a trusted SHA-256 and must preserve existing data if verification/apply fails.

## Release evidence

- [ ] Confirm the deployed manifest still matches the permission inventory above.
- [ ] Confirm actual Google OAuth consent-screen scopes and the matching Android/web OAuth clients.
- [ ] Verify debug, upload, and Play App Signing certificate fingerprints as applicable.
- [ ] Complete restricted-scope verification and security assessment requirements before public release.
- [ ] Exercise sign-in, denial, token expiry, account switching, sign-out, consent revocation, and Drive read-only behavior on a real device.
