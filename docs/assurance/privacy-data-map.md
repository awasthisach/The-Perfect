# Privacy data map — Drive Semantic Search

**Package:** `com.vvf.smartmanager`  
**Product:** Native Android Drive Semantic Search  
**Release state:** Not production-ready until external-service and real-device acceptance gates pass.

## Data classes and flows

| Data class | Where it is processed/stored | Transmission and consent | Retention / controls |
|---|---|---|---|
| Google Drive file metadata (name, MIME type, size, timestamps, IDs, hashes) | Room backed by SQLCipher in app-private storage | Retrieved from Google Drive using OAuth; not sent to the embedding Worker by the indexing API | Reconciled by resumable Drive sync; account-specific index is cleared on sign-out/account change |
| Extracted document text | Room/FTS in SQLCipher, when supported and permitted | Local extraction is controlled by separate full-content consent; the user can keep content indexing off | Revoking full-content consent clears eligible text and rebuilds FTS |
| Search query and document text for neural ranking | Transiently in the app, Cloudflare Worker, and Google Gemini | Only after the user explicitly enables neural embedding consent. Query text and eligible indexed text are sent over HTTPS to the Worker, which calls Gemini. This is cloud processing, not on-device-only AI | Consent defaults off; disabling it cancels pending requests/indexing and deletes persisted vectors. The app must never log document text or tokens |
| Neural embedding vectors | SQLCipher-backed Room cache; transient vectors in memory | Generated remotely only when neural consent is enabled and Firebase Auth + App Check succeed | Versioned by model/prompt format; cleared on neural-consent revocation; incompatible cached vectors are regenerated |
| Google/Firebase identity | Google Play Services / Firebase Auth session; Drive bearer cached in app memory | Drive access token is sent only to Google Drive. Firebase ID token and Android App Check token are sent to the Worker. Gemini API key remains server-side | Sign-out/account mismatch clears the in-memory Drive bearer and signs out Firebase; never export or log credentials |
| User-requested local index JSON export | User-selected destination URI | Export includes metadata and may include extracted text only when full-content consent permits it; the user explicitly selects the destination | Export is not guaranteed encrypted by the app once written to a user-selected external destination. Tokens, API keys, local file bytes, and vector cache are excluded |
| Cloud backup artifact | Google Drive app-created backup file and local temporary staging | Upload/restore occurs only through explicit cloud actions. SHA-256 metadata is verified; source Drive files are not trashed or deleted | Temporary downloads/staging are cleaned up; restore is fail-closed and must preserve existing data on failure |
| Vault files and vault secrets | App-private storage, SQLCipher database, Android Keystore | Not sent to the embedding Worker as part of Drive indexing | `allowBackup=false`; encryption and lockout protections apply |

## Platform controls

- `android:allowBackup="false"`; no broad Android storage/media permissions are declared in the current manifest.
- Google Drive scopes are `drive.readonly` for corpus reads and `drive.file` for app-created backups; the app does not request full Drive-management access.
- Neural consent is separate from full-content extraction consent and defaults off.
- Worker native calls require a Firebase Auth ID token plus Android App Check; a Drive access token is never accepted as Worker identity.
- Local keyword/full-text/metadata search remains available when neural consent is off or the Worker is unavailable.
- No API key or token may be embedded in the APK, exported in index backups, or written to logs.
- The current code has not yet proven production Worker deployment, real-account sign-in, Play App Signing/OAuth fingerprints, or device acceptance.

## Google policy and public release

Google classifies `drive.readonly` as a restricted scope. The owner must complete applicable OAuth restricted-scope verification and confirm the restricted-scope security-assessment and Google API User Data Policy requirements before public release, including the opted-in transfer of extracted text to Gemini.

Official references:
- https://developers.google.com/workspace/drive/api/guides/api-specific-auth
- https://support.google.com/cloud/answer/13465431
