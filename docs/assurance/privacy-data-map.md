# Privacy data map — Drive Semantic Search

**Status:** Engineering data-flow inventory; not a published privacy policy or Play Data safety declaration.  
**Package:** `com.vvf.smartmanager`

## Data classes and processing

| Data | Source / processing | Storage or recipient | User control / release requirement |
|---|---|---|---|
| Google Drive file metadata (IDs, names, MIME type, size, modified time, hashes, parents, links) | Read through Google Drive API after Google OAuth | Local Room database protected by SQLCipher; optional user-directed index export | The app requests `drive.readonly` for corpus reads and `drive.file` for app-created backups. `drive.readonly` is restricted and requires owner confirmation of OAuth verification and any required security assessment. |
| Extracted document text | Extracted locally for supported files; some formats require explicit full-content consent | Local SQLCipher index; may be sent in bounded batches to Cloudflare Worker and Gemini only after explicit neural-embedding consent | Neural embedding consent defaults off. Revocation cancels active requests and attempts to remove persisted vectors. Test revocation and deletion on-device. |
| Search query text | User enters query; optionally embedded for semantic ranking | Sent to the Cloudflare Worker/Gemini only when neural consent is enabled; otherwise local keyword/FTS search | Keep neural consent opt-in; disclose third-party processing before enabling. |
| Semantic vectors and content hashes | Generated from query/document text by Gemini; versioned retrieval format v4 | Persisted in SQLCipher-protected Room database; excluded from normal Android backup | Consent revocation clears persisted vectors. Confirm deletion behavior on real device. |
| Google Drive OAuth access token | Google Sign-In | In app memory for Drive API calls; must never be sent to the embedding Worker or logged | Revoke/refresh on account changes; test sign-out and account switching. |
| Firebase Auth ID token | Firebase Authentication | Sent as Bearer token to the Worker for authentication; must not be logged or exported | Separate from Drive OAuth token; token refresh and expiry must be tested. |
| Firebase App Check token | Firebase App Check / Play Integrity | Sent in `X-Firebase-AppCheck` to Worker for native request verification | Release test must use a correctly configured Play Integrity/App Check installation. |
| Rate-limit identity | Worker derives Firebase UID from a validated ID token | Cloudflare Durable Object rate limiter; exact retention/logging depends on deployed Cloudflare configuration | Verify provider settings and operational logs; do not log document text or bearer tokens. |
| Embedding requests | Worker validates request and calls Gemini API | Cloudflare Worker and Google Gemini API | Use a paid Gemini API project with active Cloud Billing for production document text; review Google's abuse-monitoring retention and applicable terms. Confirm this in the actual project, not from the health endpoint. |
| User-directed index export | User chooses a destination URI | JSON file at the destination chosen by the user; may include extracted text when full-content consent is enabled | Warn before export and ensure the destination is appropriate. Export excludes OAuth/Firebase tokens, API keys, local paths and file bytes. |
| User-directed cloud backup | User initiates backup to their own Google Drive | Backup archive in that user's Drive; SHA-256 recorded and verified | Cloud files remain until the user deletes them in Drive. Never trash/delete Drive files automatically. |
| Offline pinned files | User explicitly pins Drive files | App-private offline directory | Unpin/removal should delete local copies; test quota cleanup and integrity. |
| Crash/operational logs | Android/CI/Cloudflare/Google provider infrastructure | Depends on enabled SDK/provider settings | Inventory enabled crash/analytics SDKs and provider log retention before Play release; do not claim no analytics or no retention until verified. |

## Security controls present in source

- `android:allowBackup="false"`; SQLCipher-protected local database.
- Drive and Firebase tokens are separate; the Worker requires Firebase Auth plus App Check for native requests without an `Origin`.
- Neural embedding is opt-in; local keyword/FTS search remains available when consent is off or the Worker is unavailable.
- Worker request size, text count, text length and response vector dimensions are bounded/validated; rate limiting is keyed by authenticated UID.
- Index export excludes credentials, local paths and file bytes, and enforces file/size/text limits.
- Restore validates the checksum against trusted persisted/Drive metadata and re-hashes downloaded bytes before applying data.
- User's Google Drive content is not deleted by the indexer or restore flow.

## Release blockers before publication

- [ ] Owner supplies a public privacy-policy URL and support/contact details, and approves the policy wording.
- [ ] Verify restricted Drive scope eligibility, OAuth verification and any required annual CASA/security assessment.
- [ ] Confirm paid Gemini API billing, retention/abuse-monitoring terms, and Worker/Cloudflare logging configuration.
- [ ] Inventory all SDKs and finalize Google Play Data safety declarations from actual release dependencies and runtime behavior.
- [ ] On a physical device, verify consent defaults, consent revocation/vector deletion, account switching, sign-out, local export/import, and provider failure behavior.
- [ ] Confirm the public privacy policy, Play Data safety form, and in-app disclosures all match the same release candidate.
