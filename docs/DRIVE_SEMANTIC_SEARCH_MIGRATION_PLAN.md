# Drive Semantic Search — Production Migration and Acceptance Plan

## Product contract

Build an Android-native Kotlin application using Jetpack Compose and Material 3. The application name is **Drive Semantic Search**. Do not implement the primary experience as a WebView, PWA, browser-storage mock, or web demo.

The user connects their own Google Drive, indexes metadata and locally extracted text, and searches with keywords plus optional semantic embeddings. The app must degrade safely to local text and metadata search whenever embeddings are off or unavailable.

## Non-negotiable safety requirements

1. **No Drive deletion:** do not call Drive delete/trash APIs. Any future destructive local operation must be distinct, explicit, and limited to app-private copies.
2. **No embedded AI secrets:** no Gemini API key in source, APK, resources, BuildConfig, logs, or index backup.
3. **No token leakage:** never log or export Drive access/refresh tokens, Google ID tokens, Firebase ID tokens, or API keys. Redact request/exception diagnostics.
4. **Explicit neural consent:** embeddings remain off until the user sees and accepts a clear consent screen. Provide a way to turn them off and remove locally stored vectors.
5. **Safe restore:** cloud restore remains disabled/fail-closed until download, integrity verification, atomic staging, rollback and clean-device recovery tests pass.
6. **Honest UI:** no fake connection/indexing state, no silent success, and no claim that an incomplete Drive listing is complete.

## Target stack and data model

- Kotlin, Compose, Material 3, minSdk 26; coroutines and Flow.
- Room stores Drive file metadata, extracted text, vector metadata/values, vault records, sync page/change token, indexing cursor and last failure.
- Actual file bytes are stored only in app-private storage. Offline pinning is capped at about 200 MB or 80 files and evicts oldest pins first.
- Keep modules aligned with single responsibility: auth/session, Drive API, extraction, local index/search, embeddings transport, vault, offline cache, and index backup.
- No IndexedDB, sessionStorage tokens, WebView, or browser device-storage mock.

## Authentication acceptance gates

- Use Credential Manager / Google Sign-In and let the user choose an account; never hard-code an email.
- Request only the declared OpenID/profile/email and Drive scopes required by the implemented operations. Read/write Drive scope is justified only because upload, folder creation, move and star are in scope.
- Establish Firebase Auth for the same Google account. Compare normalized account emails before enabling embedding calls. On mismatch, clear Firebase state and require linking the intended account.
- Silent refresh is permitted only when a prior authenticated session exists; interactive sign-in remains available.
- Sign-out clears in-memory Drive credentials and Firebase Auth state; optional Google token revocation is user-visible.
- Tests cover expired-token behavior, refresh/re-auth, account mismatch and sign-out cleanup.

## Drive sync and indexing acceptance gates

- Use Drive Changes API and persist page/change tokens; recover with a full list if the change token is invalid.
- Cap the local listing at approximately 20,000 files and clearly disclose when results may be incomplete.
- Validate non-empty, syntactically valid file/folder IDs before every mutation. Support upload, create folder, move and star; never delete/trash.
- Index in batches of 50 through WorkManager. Automatically enqueue the next batch while work remains. Preserve the last successful cursor on cancel/failure and allow resume.
- Skip unchanged files using modifiedTime. Recompute SHA-256 only when modifiedTime changes and bytes are available locally (downloaded or pinned).
- Extraction is on-device for PDF, Office and plain text. OCR is only enabled after a separate opt-in to full-content indexing.
- Any failed file is recorded with a safe, redacted error and does not stall the whole queue. Retry policy is bounded and resumable.

## Search acceptance gates

- Search across indexed text and metadata with filters for All, Documents, Spreadsheets, Presentations and Images.
- When vectors exist and consent is on, combine cosine similarity, BM25 over extracted text and metadata ranking.
- When embedding generation, network access, or the vector index fails, continue to show keyword and metadata hits.
- When embeddings are off, perform local text/metadata search only and make no embedding network request.
- Ranking tests cover exact filename, phrase/text matches, semantic relevance, empty query, filter correctness, stale Drive metadata and deterministic tie-breaks.
- Folder suggestions compare an indexed file profile with actual Drive folder names. Suggestion alone never moves a file; a review dialog must show the selected destination and require explicit confirmation.

## Embedding transport / worker safety

Existing worker base URL: https://drive-semantic-embed.awasthi-sach.workers.dev
- GET / health contract: { ok, model, version, dimension }; current expected model gemini-embedding-2, version 3, dimension 768. Treat server response as authoritative and validate dimensions/version.
- POST embed uses Authorization: Bearer <Firebase Auth ID token> — never the Drive access token.
- Worker currently enforces Origin allow-listing and JWT verification. Native Android requests do not have a browser Origin and must not bypass this protection.
- Before enabling Android embedding calls, implement one reviewed option: (A) a small trusted proxy that validates Firebase identity and forwards requests without logging text/tokens; or (B) a worker-side Android app-check/client authorization mechanism with documented threat model and tests. Keep embeddings unavailable in the app until that gate is closed.
- Rate limits, timeouts, bounded retries, response validation and redacted errors are required. Do not log file text, queries, ID tokens or access tokens.

## UI acceptance gates

Bottom navigation must contain:
1. Dashboard: account connection, last sync, sync/index progress and embedding-consent state.
2. Search: query, type filters, results/preview, Move, Star, Pin offline and Suggest folder.
3. Duplicates: candidates grouped by same size and name; only calculate SHA-256 after bytes are available. Never pick the first result as the original automatically.
4. Vault: password/PIN lock with PBKDF2-HMAC-SHA256 at 310,000 iterations and AES-GCM. Clearly state it protects against casual device access, not a compromised app/device.
5. Offline: pinned files, 200 MB / 80-file cap and oldest-first eviction.
6. Backup: import/export local index JSON with schema/version validation. Exclude tokens, credentials, API keys and raw vault secrets. Use atomic import with validation and rollback on failure.

## Required tests

- Search ranking and fallback with embeddings disabled/failing.
- Batch resume after cancellation, process death, partial failure and expired credentials.
- Google account mismatch and sign-out cleanup.
- Drive ID validation and a static guard against Drive delete/trash endpoints.
- Duplicate grouping by size/name and optional content SHA-256; deterministic keep-candidate explanation.
- Vault encrypt/decrypt round-trip, wrong-password failure, tamper detection and salt/iteration metadata.
- Backup JSON schema, token/API-key exclusion, malformed import and atomic rollback.
- Worker transport auth/header, response dimension validation, rate-limit and timeout handling.
- Compose critical-user-journey tests for sign-in, consent, search, move confirmation, offline pinning and backup.

## Delivery sequence

### Gate 0 — Secure foundation (current branch)
- Remove embedded debug signing-key material from Gradle.
- Set app identity to Drive Semantic Search.
- Remove broad shared-storage permissions not needed for Drive indexing and app-private offline cache.
- Publish this acceptance plan and make README claims match verified implementation.

### Gate 1 — Architecture and account/session
- Inventory current modules and remove or isolate Smart Manager-only functionality not in this product.
- Introduce typed session state separating Drive OAuth credentials from Firebase Auth.
- Implement account alignment, expiry/re-auth and complete sign-out.
- Add tests before replacing old auth paths.

### Gate 2 — Drive metadata and resumable index
- Implement paginated Changes API + full-list fallback and 20,000-item cap.
- Persist metadata/cursors in Room and run WorkManager batches of 50.
- Implement safe file-ID validation and mutation allow-list; enforce no-delete policy.

### Gate 3 — Extraction and local search
- Implement tested PDF/Office/plain-text extraction and content-index consent for OCR.
- Implement local keyword/BM25 + metadata ranking and deterministic filters.
- Preserve usable search if embeddings fail.

### Gate 4 — Neural embeddings
- Keep consent off by default.
- Implement a trusted proxy or verified Android app-check path before any native worker call.
- Use Firebase ID tokens only for the embedding endpoint; validate model/version/dimension and avoid logging sensitive content.
- Add vector lifecycle, invalidation and fallback tests.

### Gate 5 — Product screens and data protection
- Build Dashboard, Search, Duplicates, Vault, Offline and Backup flows around actual state.
- Implement user-confirmed move/star/upload/folder actions, offline cap/eviction and versioned safe index backup.
- Complete Vault tests and document threat boundaries.

### Gate 6 — Production verification
- Run clean CI: unit tests, lint, debug assemble, dependency/license/security checks and static no-delete/token-leak guards.
- Test on physical Android hardware with at least two Google accounts, token expiry, offline mode, large Drive corpus, worker auth, interrupted indexing and recovery.
- Verify release signature, manifest permissions, app bundle contents and secrets scanning.
- Do not call the app production-ready while any critical/high gate is open. Attach run links and artifacts to the final report.

## Status discipline

A repository change is not considered complete until it has a commit, CI result, relevant test evidence and a checked diff. A plan or green compile does not imply runtime correctness. Keep remaining blockers explicit and never mark unverified gates as passed.
