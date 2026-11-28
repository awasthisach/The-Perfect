# VVF Smart Manager — Canonical Product Scope

**Status:** Product definition / implementation target. This document does not certify implementation or release readiness.

## 1. Product purpose

VVF Smart Manager is primarily a private Android **Semantic Search + Cross-source Duplicate Finder**. Its central job is to help a user find documents by meaning and identify duplicate files across storage locations without exposing document contents to cloud AI services.

The file explorer, encrypted vault, and storage utilities are supporting capabilities.

## 2. Included sources

### Google Drive
- My Drive files.
- Files explicitly shared with the user (“Shared with me”).
- Complete pagination, recursive folder coverage where applicable, resumable indexing, and incremental change tracking.
- Clearly distinguish inaccessible, trashed, shortcut, unsupported, and permission-revoked items.

**Not included by default:** Shared Drives. Google Drive Shared Drives are distinct from My Drive and Shared with me and require a separate product decision and API handling.

### Phone internal storage
- Scan only locations Android permits through scoped storage, app-specific access, or user-granted document-tree permissions.
- Never promise unrestricted access to all Android files or protected application directories.

### External SD / Memory Card
- Use Android Storage Access Framework (SAF), normally through `ACTION_OPEN_DOCUMENT_TREE`.
- Persist the user-granted URI permission when supported.
- Traverse only the authorized tree.
- Treat card removal, permission loss, rename, and reinsertion as resumable source-state changes; do not corrupt or silently discard index records.

## 3. Local-only AI and privacy invariant

Document extraction, OCR, text normalization, chunking, fingerprints, embedding generation, vector search, and semantic ranking must execute on-device.

**Prohibited:** sending document text, OCR output, chunks, fingerprints, or embeddings to Gemini, Cloudflare Workers, or any other remote embedding/inference endpoint.

A remote embedding client or server implementation may not be used as a fallback. The product must remain functional offline after required local model assets are installed. Model downloads, if ever needed, must be model assets only—not user document data—and must be separately disclosed.

## 4. Search behaviour

Use hybrid retrieval:
1. Lexical/full-text retrieval for exact terms and names.
2. Local neural embeddings for meaning-based retrieval.
3. A ranking layer that combines lexical and semantic evidence.
4. Source-aware results with file location, source, modified date, and permission/access state.

Search must support Hindi and English at minimum; broader language coverage depends on the selected on-device model and acceptance tests. Never claim multilingual quality without measured corpus tests.

## 5. Duplicate classification

- **Exact duplicate:** same bytes, verified by SHA-256.
- **Near duplicate:** substantially similar normalized content or structure; show score and matching evidence.
- **Semantically related:** related meaning, not a duplicate assertion.

Cross-source matching must work across all enabled sources: phone internal storage, authorized SD-card trees, My Drive, and Shared with me.

Same filename or same file size alone is not sufficient proof of a duplicate.

## 6. User control and destructive operations

- No automatic deletion, moving, overwriting, or bulk cleanup.
- Present a review screen with source, path, size, hash/score, and reason for grouping.
- Require explicit confirmation for each destructive or relocating action.
- Preserve an audit trail for user-confirmed actions where technically appropriate.
- A scan must never mutate source files.

## 7. Security and data lifecycle

- Encrypt sensitive local index/content data at rest.
- Keep source identifiers distinct: source type, source instance/account, and source-specific file ID/URI.
- Handle revoked Drive access and lost SAF grants without exposing stale content as currently accessible.
- Support cancellation, pause/resume, bounded memory, battery-aware scheduling, and safe recovery after process death.
- Provide a user-visible way to clear local index/content data and explain what will be rescanned.

## 8. Minimum acceptance tests

1. Identical bytes in internal storage, SD card, and Drive form one exact-duplicate group.
2. Same filename but different bytes do not form an exact-duplicate group.
3. Near-identical documents are classified as near duplicates with a score and evidence.
4. Semantically related but distinct documents are shown as related, not duplicates.
5. Offline search and embedding generation produce no document-data network requests.
6. A test intercepting network traffic confirms no text/chunks/embeddings leave the device.
7. Removing an SD card during indexing pauses safely and resumes without duplicate or corrupt records.
8. Revoking a SAF grant or Drive permission marks that source inaccessible and prevents stale content from being treated as accessible.
9. A user must confirm before every move/delete operation; no background worker deletes files.
10. Large libraries are indexed incrementally with bounded memory, cancellation, and progress reporting.

## 9. Delivery order

1. Correct public identity and documentation; preserve NO-SHIP status.
2. Establish a source-aware unified file identity and index.
3. Implement authorized internal-storage and SAF/SD-card source adapters.
4. Correct Google Drive authorization and My Drive / Shared-with-me listing.
5. Add content extraction and encrypted full-text/chunk persistence.
6. Add local neural model runtime, local tokenizer/model assets, and vector storage.
7. Build hybrid semantic search.
8. Build exact and near-duplicate grouping across sources.
9. Add incremental/resumable sync and lifecycle handling.
10. Complete tests, security review, CI/ORT/CPAS, OAuth, and real-device acceptance.

No phase is complete merely because this plan exists.
