# Drive Semantic Search: verified implementation-gap audit

This audit describes the current working branch `fix/drive-semantic-search-product-alignment` in **The-Perfect** only. It does not declare production readiness.

## Implemented on the working branch

- Native Kotlin/Jetpack Compose app identity, minimum SDK 26, and no WebView wrapper.
- Removed the embedded reusable debug keystore and hard-coded debug signing secrets; release signing is configured through environment-injected secrets.
- Google Drive full-scope OAuth; Firebase Auth session is linked to the same normalized Google account; a Firebase ID-token accessor is available for future trusted backend calls.
- In-memory Drive access-token clearing, silent refresh only for an existing matching Google/Firebase session, account-specific index clearing on sign-out, and automatic index enqueue after successful interactive sign-in.
- Drive v3 paginated listing, changes API, bounded 20,000-item listing, change cursor handling, explicit move/star/folder methods with ID validation, and no Drive-delete method added by this feature.
- A Room-backed `DriveIndexingWorker` that indexes metadata/text in batches of 50, stores rows in the existing SQLCipher database, rebuilds FTS, preserves the changes cursor on failure, and reports the 20,000-item cap. Trashed/removed files are removed from the local index.
- Room schema version 5 with migrations for extracted text, canonical Drive URL, offline-pin metadata, and an internal Drive sync-generation marker. Existing local rows are preserved by migrations. Existing local rows are preserved by migrations.
- Plain-text and native Google Workspace text export; bounded PDF/image extraction through the existing extraction/OCR pipeline and bounded DOCX/XLSX/PPTX text extraction, only after full-content consent.
- Revocable full-content consent clears extracted PDF/image/Office text when turned off.
- App-private offline copies with an 80-file / 200 MiB cap and oldest-first eviction.
- JSON index import/export with size/count bounds, Drive ID and URL validation, account check when an account is known, and exclusion of tokens, API keys, and file bytes. Import now respects full-content consent.
- Keyword/FTS fallback remains available when optional semantic ranking is not enabled; vault PIN derivation changes retain a legacy verification path for existing PINs.
- Initial tests cover indexing policy, keyword fallback, vault PIN verification, Office extraction, database FTS and offline quota queries.

- Initial/full-list indexing now requests one bounded page at a time. After each page is fully written to Room, the worker stores the next opaque page token, initial changes cursor, processed count, and internal run ID. A retry replays the saved page idempotently; invalid/expired page tokens clear the checkpoint and safely restart. A separate Room schema-v5 generation column marks rows seen in the current full pass so stale local rows are removed only after a complete listing without polluting user-visible tags. The worker does not advance the changes cursor on a capped listing. Automated failure/restart and migration tests are still required.

## Remaining production blockers

1. **Real neural semantic search:** the previous `SemanticSearchPluginImpl` used deterministic hashed token buckets; that implementation has been removed. The plugin reports the model as unavailable, returns no synthetic embedding vectors, and fails closed for semantic ranking/duplicate grouping until a genuine provider is integrated. Local FTS, keyword, and metadata search remain the active fallback. The live worker health endpoint was verified to return `ok=true`, model `gemini-embedding-2`, version `3`, dimension `768`; this does not verify `/embed` POST authorization or prove mobile requests work. The Cloudflare worker at `https://drive-semantic-embed.awasthi-sach.workers.dev` must not be called directly from Android until a trusted backend proxy or worker-side Android App Check is implemented and deployed. The Firebase ID token—not the Drive access token—must authenticate the backend. No API key may ship in the APK.
2. **Resumability verification:** per-page full-list checkpoints are implemented, but automated tests still need to simulate interruption before/after page commit, invalid/expired page tokens, cap handling, and cursor persistence. Verify schema 4→5 migration against an existing encrypted database.
3. **OAuth configuration:** verify the actual package `com.vvf.smartmanager`, web client ID, Firebase project, and debug/release signing SHA fingerprints. These external project settings and interactive device sign-in cannot be proven by source inspection alone.
4. **UI/product completeness:** Drive move/star/folder suggestion and review/confirmation flows, dedicated Dashboard/Search/Duplicates/Vault/Offline/Backup navigation, and duplicate keep/move workflows need acceptance tests. Existing legacy file-manager screens and broad storage permissions remain.
5. **Security and reliability tests:** add worker tests for cancellation, retries, change-feed paging, removed/trashed files, full-list cap, and cursor persistence; test backup malformed/oversized data and consent revocation/import; test offline quota eviction failures and local-file integrity; test Room 1→5 migrations with existing data.
6. **Ranking:** implement and test the requested actual hybrid ranking (real cosine vectors + BM25/FTS + metadata weights) once a safe, authenticated embedding source is available. Do not describe hash-token similarity as neural embeddings.
7. **Vault acceptance:** verify the exact PBKDF2-HMAC-SHA256 310,000-iteration new-PIN contract and AES-GCM round-trip, wrong PIN, legacy PIN, and decoy PIN flows.
8. **Release gates:** current-head unit tests, lint, debug assembly, FOSSA/license checks, CodeQL, and CPAS checks passed. The dedicated hard SQLCipher 4.5.4 compatibility instrumentation test also passed on an API 35 emulator after fixing AVD creation and granting the ephemeral GitHub runner access to `/dev/kvm`. The normal PR CI's emulator step still soft-skips instrumentation when its separate emulator setup does not boot; the dedicated hard workflow is the verified database gate. Release signing, production Firebase configuration, release assembly, real-device checks, and live Google OAuth/Drive acceptance remain unverified. Configure release signing and Firebase files through CI secrets, not committed artifacts.

## Verified CI evidence (2026-10-12)

- Current code head at verification: `937e5d2ba181bfbc478b49e201659983efa0bcbd`.
- VVF Smart Manager CI & Quality Gate: success (unit tests, Android lint, debug APK assembly, license compliance).
- CodeQL: success.
- CPAS Production Gate: success.
- Weekly Instrumented Hard Gate: success. SQLCipher 4.5.4 fixture generation succeeded, the API 35 emulator booted after KVM permission correction, and the SQLCipher compatibility instrumentation test ran and passed (1 test).
- The ordinary PR CI still marks SQLCipher instrumentation as skipped when its own soft-gated emulator does not boot; it is not counted as an instrumentation pass.
- Current build warnings: the manifest declares a WorkManager initializer removal without a matching declaration; Android packaging reports some native libraries could not be stripped. These are warnings, not build failures.

## Release rule

Do not merge this draft or call the app production-ready until the blockers above have evidence-backed closure. A green CI workflow is necessary but not sufficient.
