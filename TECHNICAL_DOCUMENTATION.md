# VVF Smart Manager — Technical Architecture and Delivery Plan

**Package:** `com.vvf.smartmanager`  
**Platform:** Android (minimum SDK configured in Gradle; verify current build files before changing)  
**Document status:** Architecture plan grounded in source inspection of `audit/fixes-2026-11` at `8eb0b5f2e083aca1a508c5e2199c1e02d29d5d32`. Recheck source before treating this as current after later commits.

## 1. Product architecture

The primary capabilities are:
- Local semantic search over document content.
- Exact and near-duplicate detection across phone storage, authorized SD-card folders, Google Drive My Drive, and Shared with me.
- User-controlled file review and actions; no automatic deletion or movement.

The local-only invariant is strict: user document text, OCR text, chunks, fingerprints, and embeddings do not leave the device.

## 2. Current implementation versus target

| Capability | Current source evidence at inspected SHA | Required target |
|---|---|---|
| Semantic similarity | Deterministic 128-dimensional hash projection in `SemanticSearchPluginImpl` | On-device neural embeddings with local tokenizer/model |
| Search input | Filename/tags and a limited indexed candidate set in `SemanticSearchUseCase` | Extracted document text, chunks, lexical index, and vectors |
| OCR indexing | `IndexOcrTextUseCase` stores a small keyword set as tags | Persist encrypted full extracted text/chunks with lifecycle controls |
| FTS | `FileFtsEntity` contains name, path, tags, MIME type | Full-text content index plus metadata |
| Drive authorization | `GoogleDriveAuth` requests `drive.file` | OAuth scope and verification plan that supports all intended My Drive and Shared-with-me files |
| Drive listing | `GoogleDriveServiceImpl` lists direct folder children | Complete, paginated, recursive/appropriate corpus indexing and incremental Changes API sync |
| Phone source | File explorer and storage-related code exist; unified source indexing is not established | Source adapter using Android-supported permissions |
| SD-card source | Complete SAF source adapter not established | SAF tree grant, persisted permission, recursive authorized scan, removal/reinsert recovery |
| Duplicate pipeline | Hash fields and duplicate-level model definitions exist | One source-aware exact/near-duplicate engine across every enabled source |
| Remote embedding | Legacy `EmbeddingWorkerClient` exists | No remote document embedding path; remove or make unreachable and test network boundary |

These gaps are implementation work, not optional polish.

## 3. Source-aware unified index

Every indexed item must have a stable source identity, independent of its display path:

- `sourceType`: `INTERNAL`, `SD_CARD`, or `GOOGLE_DRIVE`.
- `sourceInstanceId`: app storage volume identity, removable volume identity, or Drive account identity.
- `sourceFileId`: canonical SAF document URI or Drive file ID.
- `displayName`, `mimeType`, `size`, `modifiedAt`, `accessState`.
- `contentHashSha256`, extraction status, and last indexed version.

A path is mutable and must not be the sole primary key. Duplicate groups reference source-aware file IDs, not only paths or names.

Candidate logical tables (schema must be designed against the actual Room migrations before implementation):
- `indexed_files` — source identity and metadata.
- `document_content` — encrypted extracted text or content references.
- `document_chunks` — chunk boundaries and normalized text references.
- `document_embeddings` — model/version, dimensions, vector payload, source chunk.
- `source_sync_state` — page/change token, permission state, last successful checkpoint.
- `duplicate_groups` — classification, evidence, score, and members.

Do not create these tables blindly; first inspect the current database schema, migration chain, SQLCipher configuration, and backup/restore behaviour.

## 4. Source adapters

### Google Drive
- My Drive and Shared with me are in scope.
- Shared Drives are out of scope until separately approved.
- Reassess OAuth scopes against Google's current official documentation. Full read-only access is a restricted scope and can require OAuth verification and additional security assessment.
- Support list pagination, folder traversal, shortcuts, trashed files, export/download formats, rate limits, expired tokens, revoked access, and incremental Changes API sync.
- Never use a Drive access token as a Firebase identity token.

### Internal storage
- Respect Android scoped storage and the app's actual permission model.
- Use MediaStore for supported shared media and SAF for user-selected document trees where required.
- Report scan coverage honestly; never claim access to protected app-private/system locations.

### SD card
- Use SAF document-tree grants and persist URI permissions.
- Traverse only the granted tree.
- Persist scan checkpoints and handle unavailable volumes and revoked grants safely.

## 5. Local document intelligence

Required pipeline:
1. Read supported local/Drive document bytes using source-specific adapters.
2. Extract text locally (including OCR for scanned images/PDF pages where supported).
3. Normalize and split text into bounded chunks.
4. Persist content securely and build lexical/full-text index.
5. Generate embeddings using a fully on-device runtime and local model/tokenizer.
6. Store model ID/version and vector dimension with every embedding.
7. Perform local vector similarity and hybrid ranking.

The model/runtime choice must be benchmarked on supported low-memory Android devices for model size, RAM, CPU latency, battery, language coverage, and license. Do not choose a model solely because its benchmark score is high.

## 6. Duplicate engine

- Exact duplicate: SHA-256 equality after reading the full bytes.
- Near duplicate: normalized text/content fingerprints with explainable similarity.
- Semantically related: separate discovery category; never automatically label as duplicate.
- Cross-source matching must compare all enabled source adapters.
- Same name or size is only a candidate filter, never final proof.
- No background job may delete or move source files.

## 7. Security, consent, and lifecycle

- SQLCipher/Room and Android Keystore remain part of the security boundary; review the actual key lifecycle and recovery path.
- No document content or embedding network requests. Add tests with a fake HTTP stack and device-level network inspection.
- Cancel and pause indexing safely; bound concurrency and memory.
- Handle process death, card removal, permission revocation, account disconnect, and partial extraction.
- Preserve fail-closed behaviour for backup and release paths until tested restore/recovery exists.

## 8. Delivery gates

Each phase requires code, tests, and evidence:
1. Source-aware index and migration design.
2. Internal/SD source adapters.
3. Drive scope and complete listing.
4. Local extraction, encrypted content store, and FTS.
5. Local neural embedding runtime and hybrid search.
6. Cross-source exact/near duplicate engine.
7. Incremental sync and recovery.
8. Device performance, privacy/network-boundary, security, CI, ORT, CPAS, OAuth, and Play acceptance.

See [PRODUCT_SCOPE.md](PRODUCT_SCOPE.md) for product acceptance tests. No production claim should be made until the exact release candidate passes all applicable gates.
