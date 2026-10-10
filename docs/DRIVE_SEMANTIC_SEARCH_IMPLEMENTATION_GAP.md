# Drive Semantic Search: verified implementation gap audit

This document is a code-level gap inventory against the requested v1 product plan. It intentionally does not mark the app production-ready.

## Verified repository state

- Android application module uses Kotlin and Jetpack Compose.
- The application ID is currently `com.vvf.smartmanager`; changing it would invalidate OAuth and Firebase package registration unless backend/client configuration is updated in lockstep.
- The app and Drive module minimum SDKs are now set to 26 on the working branch.
- User-facing app identity and Gradle root project are aligned to **Drive Semantic Search** on the working branch.
- `GoogleDriveAuth` now requests the full Drive scope and links Firebase Auth using the same Google ID token, checking normalized email equality and clearing mismatched Firebase sessions. Silent Drive token refresh is exposed only when a matching existing Google/Firebase session exists.
- `GoogleDriveServiceImpl` now exposes bounded 20,000-file pagination, changes API cursor methods, full-list fallback on a failed changes cursor, and explicit move/star/create-folder methods with Drive ID validation. The new indexing policy defines batches of 50 and cursor-on-commit behavior, but it is not yet wired to a durable Room-backed WorkManager pipeline. The access token remains in memory.
- `FileIndexingWorker` logs generic device-storage indexing and delegates to `FileIndexingRuntime`; this is not proof of the requested resumable Google Drive changes API indexer.
- The current app manifest requests broad device-storage permissions for the existing file-manager functionality; the requested v1 product instead confines downloaded file bytes to app-private storage.
- Existing `SemanticSearchUseCase` delegates to an on-device plugin; the required optional, consent-gated Cloudflare embedding client and hybrid cosine/BM25/metadata ranker have not been verified in the reviewed files.
- The current vault/security module is a general cryptographic manager. The exact requested password-derived PBKDF2 (310,000 iterations) + AES-GCM vault round-trip contract has not been verified in the reviewed code.
- Current README identifies the app as VVF Smart Manager and explicitly says it is not independently verified for public release.

## Production blockers — do not release until each is closed with tests

1. Finish sign-out wiring so the in-memory Drive token is cleared with Firebase and Google sessions; add token-expiry, account-mismatch, and silent-refresh tests. Never send a Drive access token to the embedding worker.
2. Wire the new Drive changes/list APIs and batch/resume policy into a durable Room-backed WorkManager indexer; persist page/change cursors, show the 20,000-file incomplete warning, skip unchanged modifiedTime values, and test cancellation/failure resume.
3. Implement on-device PDF/Office/plain-text extraction, optional-consent ML Kit OCR, Room persistence for metadata/text/vectors/cursor, and explicit app-private offline pinning with 200 MB/80-file cap and oldest-first eviction.
4. Consent storage/UI and a local neural-search gate now exist on the working branch. Deploy and integrate a trusted proxy or worker-enforced Android App Check before any mobile cloud embedding request. The worker requires Origin allow-listing and Firebase ID-token auth; Android must not bypass either check. No API key belongs in the APK.
5. Verify hybrid cosine + BM25 + metadata ranking and a working keyword/metadata fallback when embeddings are disabled or unavailable.
6. Verify user-confirmed Move/Star/Pin/Suggest Folder flows, Drive ID validation, duplicate grouping, and SHA-256 only after file bytes are downloaded or pinned. Never delete Drive files.
7. Verify password vault uses PBKDF2-HMAC-SHA256 with 310,000 iterations and AES-GCM, including wrong-password and encrypt/decrypt round-trip tests.
8. Implement local-index JSON import/export that excludes access tokens, Firebase ID tokens, and API keys; test malformed/oversized imports.
9. Added initial tests for keyword fallback, batch sizing/cursor resume, and vault PIN verification. Complete tests for hybrid ranking with real vector candidates, token expiry/account mismatch, duplicate grouping, file extraction, backup import/export, permissions, and user-critical flows. Run unit tests, lint, debug/release assembly, emulator/instrumented tests, and real-device OAuth/Drive verification.
10. Replace the current broad local-storage permission footprint with least-privilege access appropriate to this Drive-only v1, after removing or isolating out-of-scope file-manager flows.
11. Remove any committed reusable signing material and verify release signing/Google OAuth configuration using managed secrets and registered production fingerprints.

## Release rule

A green CI workflow alone is not sufficient. This project must not be called production-ready until the functional, security, release-signing, and real-device gates above have passing evidence.