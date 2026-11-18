# VVF Smart Manager — Release Status

**Status: No verified public production release is certified by this document.**

The repository contains application code and release/assurance workflows, but a version label, release tag, APK, or green individual CI workflow does not by itself establish production readiness.

## Product direction

The primary product goals are:
- On-device semantic search over document contents.
- Cross-source exact and near-duplicate detection across phone internal storage, user-authorized SD-card folders, Google Drive My Drive, and Shared with me.
- No document text or embeddings sent to remote AI services.
- No automatic deletion or movement of user files.

See [PRODUCT_SCOPE.md](PRODUCT_SCOPE.md) for the canonical scope and acceptance tests.

## Current known implementation gaps

At inspected candidate `audit/fixes-2026-11` SHA `8eb0b5f2e083aca1a508c5e2199c1e02d29d5d32`:

- Semantic similarity is deterministic hash projection, not neural document-content embeddings.
- Full document/OCR text is not yet stored in the search index for semantic retrieval.
- Drive authorization uses `drive.file`; complete My Drive and Shared-with-me indexing is not established.
- SD-card SAF indexing and cross-source duplicate grouping are not established.
- A legacy remote embedding client exists and must be technically excluded from the final local-only search path.
- Production assurance, OAuth, signing, and physical-device acceptance remain separate release gates.

These findings must be rechecked against the exact candidate SHA before release decisions.

## Release gate policy

A release may be called ready only when:
1. Required Android build, tests, lint, and instrumented security gates pass on one exact candidate SHA.
2. ORT analysis, evaluated findings, and required SBOM artifacts are complete and reviewed.
3. CPAS reports an unblocked result using evidence for that same candidate.
4. Google OAuth verification, scopes, privacy disclosures, signing, and Play requirements are satisfied.
5. Local-only processing is verified by tests and network-boundary inspection.
6. Real-device acceptance covers Drive, internal storage, SD-card removal/reinsert, offline search, duplicate classification, and user-confirmed file actions.

## Candidate status

PR [#225](https://github.com/awasthisach/The-Perfect/pull/225) is maintained as **Draft / Open / Unmerged / NO-SHIP** until its exact-SHA evidence and all owner/device acceptance requirements are complete.

No historical CI result in this file should be interpreted as a result for a later commit.
