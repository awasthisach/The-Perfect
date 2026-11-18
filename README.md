# VVF Smart Manager

**VVF Smart Manager** is an Android project whose primary product goals are:

1. **Private Semantic Search** — search the contents and meaning of documents, not only their filenames.
2. **Cross-source Duplicate Finder** — identify exact and near-duplicate files across Google Drive, phone storage, and user-authorized SD-card folders.

The intended design is **on-device first**: document text, OCR output, fingerprints, and embeddings must stay on the device. The app must never automatically delete or move files. File management, the encrypted vault, and storage utilities are supporting features—not the product's primary purpose.

> **Development status:** These are product goals, not a claim that every feature is implemented. The current code still has material gaps described below. This repository is not certified production-ready.

Application ID: `com.vvf.smartmanager`

## Product scope

| Area | Intended behaviour | Current verified status in this candidate |
|---|---|---|
| Google Drive | Index the user's My Drive and **Shared with me** files, with complete pagination and incremental sync | **Incomplete.** Current authorization uses `drive.file`; this does not provide blanket access to all existing Drive files. Full-drive and Shared-with-me indexing are not verified. |
| Phone storage | Index files the user has granted the app permission to access | **Not verified end-to-end** as part of one unified index. Android scoped-storage limits apply. |
| SD / Memory Card | Let the user choose folders through Android Storage Access Framework (SAF); persist permission and resume safely after card removal/reinsert | **Not implemented/verified** as a complete source in this candidate. |
| Exact duplicates | SHA-256 content match across all enabled sources | Existing models contain hash fields, but unified cross-source grouping is **not verified**. |
| Near duplicates | Compare normalized content/fingerprints and show a score with evidence | **Not verified** end-to-end. |
| Semantic search | Local multilingual document embeddings and hybrid full-text/vector retrieval | **Not implemented.** Current semantic plugin uses deterministic hash projection, not a learned neural embedding model. |
| Privacy | No document text or embeddings sent to a cloud embedding API | **Not yet certified.** A legacy remote Worker client exists in the codebase; it must be removed from or technically blocked from the product search/indexing path before this requirement can be claimed. |
| File actions | Review first; require explicit user confirmation for every move/delete | Product requirement. Automatic deletion is prohibited. |

Shared Drives are a separate Google Drive feature from My Drive and “Shared with me”; they are not included in the initial scope unless explicitly added later.

See [PRODUCT_SCOPE.md](PRODUCT_SCOPE.md) for the binding product definition and [TECHNICAL_DOCUMENTATION.md](TECHNICAL_DOCUMENTATION.md) for the current-versus-target architecture.

## Duplicate classification

The app must keep these results separate:

- **Exact duplicate:** identical file bytes, confirmed by SHA-256.
- **Near duplicate:** highly similar content or structure, supported by a transparent comparison score.
- **Semantically related:** similar meaning or topic, but not necessarily a duplicate. Never present this category as safe to delete.

No file is deleted or moved automatically. Results are recommendations; every destructive or relocating action requires a separate user confirmation.

## Privacy and storage-access rules

- Neural embeddings and document-content analysis must run locally on the Android device.
- Do not send document text, OCR text, fingerprints, or embeddings to Gemini, Cloudflare, or another remote embedding service.
- Google Drive access must be least-privilege, transparent, and compatible with Google's OAuth verification requirements.
- Internal storage access is limited by Android scoped storage and granted permissions.
- Removable SD-card scanning must use SAF folder grants. The app can scan only folders the user authorizes; it cannot promise unrestricted access to protected/system directories.
- A removed SD card must pause its scan and resume only after the same source is safely re-authorized or reattached.
- Encrypted local persistence and explicit user control are required.

## Current implementation snapshot

The following statements describe source code inspected on branch `audit/fixes-2026-11` at commit `8eb0b5f2e083aca1a508c5e2199c1e02d29d5d32`; they are not a claim about later commits:

- `SemanticSearchUseCase.kt` documents deterministic on-device hash-based similarity and limits candidates to indexed metadata; this is not neural document-content search.
- `SemanticSearchPluginImpl.kt` uses a 128-dimensional deterministic hash projection.
- `FileFtsEntity.kt` indexes name, path, tags, and MIME type; it does not contain full document text.
- `IndexOcrTextUseCase.kt` stores a small set of extracted keywords as tags, not the complete OCR text for semantic indexing.
- `GoogleDriveAuth.kt` requests `drive.file`, not full read-only Drive access.
- `GoogleDriveServiceImpl.kt` lists direct children of a folder; complete recursive indexing and Shared-with-me coverage are not established.
- `EmbeddingWorkerClient.kt` is a legacy remote embedding client and conflicts with the final local-only product requirement until its use is technically excluded.

Source references:
- [SemanticSearchUseCase.kt](core/domain/src/main/java/com/vvf/smartmanager/core/domain/SemanticSearchUseCase.kt)
- [SemanticSearchPluginImpl.kt](plugins/plugin-semantic-search/src/main/java/com/vvf/smartmanager/plugin/semanticsearch/SemanticSearchPluginImpl.kt)
- [FileFtsEntity.kt](core/database/src/main/java/com/vvf/smartmanager/core/database/model/FileFtsEntity.kt)
- [OcrUseCases.kt](core/domain/src/main/java/com/vvf/smartmanager/core/domain/OcrUseCases.kt)
- [GoogleDriveAuth.kt](core/cloud-gdrive/src/main/java/com/vvf/smartmanager/core/cloud/gdrive/GoogleDriveAuth.kt)
- [GoogleDriveServiceImpl.kt](core/cloud-gdrive/src/main/java/com/vvf/smartmanager/core/cloud/gdrive/GoogleDriveServiceImpl.kt)
- [EmbeddingWorkerClient.kt](app/src/main/java/com/vvf/smartmanager/embedding/EmbeddingWorkerClient.kt)

## Technology currently present

| Area | Repository evidence |
|---|---|
| Language / UI | Kotlin, Jetpack Compose, Material 3 |
| Persistence | Room and SQLCipher |
| Security | Android Keystore and encrypted vault/database support |
| Background work | WorkManager |
| OCR | ML Kit-based plugin |
| Drive | Google Drive API integration |
| Search | Local metadata search and deterministic hash-based similarity; neural content embeddings remain future work |
| Build | Gradle Kotlin DSL and version catalog |

Do not infer feature completion from a module, interface, plugin stub, or dependency being present.

## Build and local checks

Use the Gradle wrapper:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

A successful debug build or unit-test run does not prove OAuth approval, complete Drive access, SD-card access, local neural inference, production signing, Play compliance, or release readiness.

## Release and assurance status

The project has open production/assurance work. PR [#225](https://github.com/awasthisach/The-Perfect/pull/225) is deliberately **Draft / Open / Unmerged / NO-SHIP**. Do not merge it as a shortcut around exact-SHA CI, ORT/SBOM, CPAS, OAuth, signing, and physical-device acceptance.

The risk register and evidence ledger are under [docs/assurance](docs/assurance/). A CI pass applies only to the exact commit and workflow run that produced it.

## Documentation map

- [PRODUCT_SCOPE.md](PRODUCT_SCOPE.md) — canonical product goals, boundaries, and acceptance criteria
- [TECHNICAL_DOCUMENTATION.md](TECHNICAL_DOCUMENTATION.md) — current architecture gaps and target design
- [BEGINNER_GUIDE_HINDI.md](BEGINNER_GUIDE_HINDI.md) — project status in simple Hindi
- [RELEASE_NOTES.md](RELEASE_NOTES.md) — verified release status
- [SECURITY.md](SECURITY.md) and [SECURITY_STATUS.md](SECURITY_STATUS.md) — security reporting and posture
- [docs/assurance](docs/assurance/) — assurance evidence and risk tracking
