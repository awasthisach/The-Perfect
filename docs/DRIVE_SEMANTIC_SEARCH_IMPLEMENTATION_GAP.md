# Drive Semantic Search: verified implementation-gap audit

This audit describes the current working branch `fix/drive-semantic-search-product-alignment` in **The-Perfect** only. It does not declare production readiness.

## Implemented on the working branch

- Native Kotlin/Jetpack Compose app identity, minimum SDK 26, and no WebView wrapper.
- Removed the embedded reusable debug keystore and hard-coded debug signing secrets; release signing is configured through environment-injected secrets.
- Google Drive full-scope OAuth; Firebase Auth session is linked to the same normalized Google account; a Firebase ID-token accessor is available for future trusted backend calls.
- In-memory Drive access-token clearing, silent refresh only for an existing matching Google/Firebase session, account-specific index clearing on sign-out, and automatic index enqueue after successful interactive sign-in.
- Drive v3 paginated listing, changes API, bounded 20,000-item listing, change cursor handling, explicit move/star/folder methods with ID validation, and no Drive-delete method added by this feature.
- A Room-backed `DriveIndexingWorker` that indexes metadata/text in batches of 50, stores rows in the existing SQLCipher database, rebuilds FTS, preserves cursor on failure, and reports the 20,000-item cap. Trashed/removed files are removed from the local index.
- Room schema version 4 with migrations for extracted text, canonical Drive URL, and offline-pin metadata. Existing local rows are preserved by migrations.
- Plain-text and native Google Workspace text export; bounded PDF/image extraction through the existing extraction/OCR pipeline and bounded DOCX/XLSX/PPTX text extraction, only after full-content consent.
- Revocable full-content consent clears extracted PDF/image/Office text when turned off.
- App-private offline copies with an 80-file / 200 MiB cap and oldest-first eviction.
- JSON index import/export with size/count bounds, Drive ID and URL validation, account check when an account is known, and exclusion of tokens, API keys, and file bytes. Import now respects full-content consent.
- Keyword/FTS fallback remains available when optional semantic ranking is not enabled; vault PIN derivation changes retain a legacy verification path for existing PINs.
- Initial tests cover indexing policy, keyword fallback, vault PIN verification, Office extraction, database FTS and offline quota queries.

## Remaining production blockers

1. **Real neural semantic search:** the currently wired `SemanticSearchPluginImpl` uses deterministic hashed token buckets; it is not a neural model. The Cloudflare worker at `https://drive-semantic-embed.awasthi-sach.workers.dev` must not be called directly from Android until a trusted backend proxy or worker-side Android App Check is implemented and verified. The Firebase ID token—not the Drive access token—must authenticate the backend. No API key may ship in the APK.
2. **Full resumability:** the changes-feed continuation cursor is persisted after a successful pass, but the initial/full-list pass is re-read from the beginning after interruption; a durable per-page checkpoint and failure/restart tests are still needed.
3. **OAuth configuration:** verify the actual package `com.vvf.smartmanager`, web client ID, Firebase project, and debug/release signing SHA fingerprints. These external project settings and interactive device sign-in cannot be proven by source inspection alone.
4. **UI/product completeness:** Drive move/star/folder suggestion and review/confirmation flows, dedicated Dashboard/Search/Duplicates/Vault/Offline/Backup navigation, and duplicate keep/move workflows need acceptance tests. Existing legacy file-manager screens and broad storage permissions remain.
5. **Security and reliability tests:** add worker tests for cancellation, retries, change-feed paging, removed/trashed files, full-list cap, and cursor persistence; test backup malformed/oversized data and consent revocation/import; test offline quota eviction failures and local-file integrity; test Room 1→4 migrations with existing data.
6. **Ranking:** implement and test the requested actual hybrid ranking (real cosine vectors + BM25/FTS + metadata weights) once a safe, authenticated embedding source is available. Do not describe hash-token similarity as neural embeddings.
7. **Vault acceptance:** verify the exact PBKDF2-HMAC-SHA256 310,000-iteration new-PIN contract and AES-GCM round-trip, wrong PIN, legacy PIN, and decoy PIN flows.
8. **Release gates:** pass current-head unit tests, lint, debug/release assembly, license/security checks, SQLCipher instrumentation, emulator/device checks, and real Google OAuth/Drive acceptance. Configure release signing and Firebase files through CI secrets, not committed artifacts.

## Release rule

Do not merge this draft or call the app production-ready until the blockers above have evidence-backed closure. A green CI workflow is necessary but not sufficient.
