# Drive Semantic Search: verified implementation gap audit

This document is a code-level gap inventory against the requested v1 product plan. It intentionally does not mark the app production-ready.

## Verified repository state

- Android application module uses Kotlin and Jetpack Compose.
- The application ID is currently `com.vvf.smartmanager`; changing it would invalidate OAuth and Firebase package registration unless backend/client configuration is updated in lockstep.
- The configured minimum SDK was 24, which contradicts the requested minSdk 26; the product-alignment change raises it to 26.
- User-facing strings and Gradle root project are being aligned to **Drive Semantic Search**.
- `GoogleDriveAuth` currently uses Credential Manager for a Google ID token and a separate Google Sign-In flow for Drive access. The Drive scope is currently `drive.file`, not the requested full `drive` scope.
- `GoogleDriveServiceImpl` uses a REST list-files call scoped to a folder and has an in-memory access token with a 55-minute age guard. This is not a durable OAuth refresh/session implementation.
- `FileIndexingWorker` logs generic device-storage indexing and delegates to `FileIndexingRuntime`; this is not proof of the requested resumable Google Drive changes API indexer.
- The current app manifest requests broad device-storage permissions for the existing file-manager functionality; the requested v1 product instead confines downloaded file bytes to app-private storage.
- Existing `SemanticSearchUseCase` delegates to an on-device plugin; the required optional, consent-gated Cloudflare embedding client and hybrid cosine/BM25/metadata ranker have not been verified in the reviewed files.
- The current vault/security module is a general cryptographic manager. The exact requested password-derived PBKDF2 (310,000 iterations) + AES-GCM vault round-trip contract has not been verified in the reviewed code.
- Current README identifies the app as VVF Smart Manager and explicitly says it is not independently verified for public release.

## Production blockers — do not release until each is closed with tests

1. Implement same-account Google + Firebase Auth linking; compare verified account identity, clear both sessions on sign-out, and add token-expiry tests. Never send a Drive access token to the embedding worker.
2. Implement and test Drive changes API sync, full-list fallback, 20,000-item cap/incomplete warning, stable page/change cursors, modifiedTime-based incremental extraction, and WorkManager batches of 50 with resume on cancellation/failure.
3. Implement on-device PDF/Office/plain-text extraction, optional-consent ML Kit OCR, Room persistence for metadata/text/vectors/cursor, and explicit app-private offline pinning with 200 MB/80-file cap and oldest-first eviction.
4. Implement consent-gated embedding with a trusted proxy or a worker-enforced Android app-check mechanism. Worker requires Origin allow-listing and Firebase ID-token auth; Android must not bypass either check. No API key belongs in the APK.
5. Verify hybrid cosine + BM25 + metadata ranking and a working keyword/metadata fallback when embeddings are disabled or unavailable.
6. Verify user-confirmed Move/Star/Pin/Suggest Folder flows, Drive ID validation, duplicate grouping, and SHA-256 only after file bytes are downloaded or pinned. Never delete Drive files.
7. Verify password vault uses PBKDF2-HMAC-SHA256 with 310,000 iterations and AES-GCM, including wrong-password and encrypt/decrypt round-trip tests.
8. Implement local-index JSON import/export that excludes access tokens, Firebase ID tokens, and API keys; test malformed/oversized imports.
9. Add tests for ranking, cursor resume, token expiry/account mismatch, duplicate grouping, vault crypto, permissions, and user-critical flows. Run unit tests, lint, debug/release assembly, emulator/instrumented tests, and a real-device OAuth/Drive verification.
10. Replace the current broad local-storage permission footprint with least-privilege access appropriate to this Drive-only v1, after removing or isolating out-of-scope file-manager flows.
11. Remove any committed reusable signing material and verify release signing/Google OAuth configuration using managed secrets and registered production fingerprints.

## Release rule

A green CI workflow alone is not sufficient. This project must not be called production-ready until the functional, security, release-signing, and real-device gates above have passing evidence.