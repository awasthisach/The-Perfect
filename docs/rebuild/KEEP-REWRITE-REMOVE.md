# KEEP / REWRITE / REMOVE — source-based triage

**तारीख:** 20 नवंबर 2026
**Branch / आधार SHA:** `audit/fixes-2026-11` / `fd6a35ca49622217c876bdb3758aeafab978adbf`
**स्थिति:** निर्णय-सूची; कोई source file बदली/हटाई नहीं गई।

यह सूची चरण 1 map और user-provided hard rules पर आधारित है। “REWRITE” का अर्थ आगे समीक्षा/डिज़ाइन करना है, अभी rewrite करना नहीं। चरण 2 build stabilization और प्रमाण के बाद ही चरण 3 cleanup शुरू होगा। हर REMOVE के लिए exact references + Gradle dependency graph + संबंधित tests/evidence चाहिए।

## KEEP — संरक्षित सीमा

- Git history, `main`, `audit/fixes-2026-11` और PR #225 की Draft/Open/Unmerged स्थिति; `main` में direct change, force-push, PR merge/close नहीं। PR स्थिति को अभी baseline capture के समय वाला समझें; नया live status इस चरण में query नहीं किया गया।
- `settings.gradle.kts`, `gradle/libs.versions.toml`, module build files, Gradle wrapper, AndroidManifest और workflows — पहले references/configuration/test evidence के बिना नहीं हटाने हैं।
- Room entities/DAOs/schema और migrations; `VVFDatabase` का SQLCipher builder; `CryptoSecurityManager`/Keystore recovery fail-closed path; SQLCipher, migration, key, biometric और backup-crypto tests। Source map में schema JSON v2 बनाम database source v5 तथा अलग `DatabaseConstants` values मिले; इन्हें “पुराना” मानकर हटाना निषिद्ध है।
- Drive OAuth `drive.file` scope, Firebase ID-token path से उसका अंतर, pagination/cancel/revoke logic; WorkManager opt-out/cancel paths; OCR/search/worker/security tests।
- ORT/CPAS/SBOM scripts, workflow evidence, पुराने logs/artifacts और audit evidence bundle। असफल workflow/SBOM coverage को छिपाने या artifacts मिटाने की अनुमति नहीं।

## REWRITE / INVESTIGATE — आगे के चरणों के उम्मीदवार

| विषय | स्रोत-आधार | आगे का कार्य (अभी नहीं) |
|---|---|---|
| Semantic search | Production plugin 128-dim deterministic hash similarity है; learned on-device model नहीं (`SemanticSearchPluginImpl.kt:15-148`). Candidate text inspected use case में name + tags है (`SemanticSearchUseCase.kt:13-70`). | चरण 8 के लिए वास्तविक local embedding model, model/license/size/offline behavior और migration/test plan डिज़ाइन करें। Remote endpoint अपने-आप न जोड़ें। |
| OCR full text | `ocrText` field/FTS है; repository save guard 1,000,000 characters है; inspected extraction path में text chunking नहीं दिखी (`FileMetadataEntity.kt:25-45`; `OfflineSearchRepository.kt:146-159`). | चरण 7 में full-text fidelity, long documents, chunking/index schema, encryption, export और opt-out cancellation व्यवहार मापें; data-loss-free migration के बिना rewrite न करें। |
| Dependency-injection strategy | App में Hilt plugin/runtime हैं, जबकि `VVFApplication`/`AppCompositionRoot` constructors से objects बनाते हैं (`app/build.gradle.kts:3-12,147-149`; `VVFApplication.kt:152-365`). | चरण 4 में project-wide annotations/references, generated components, tests और startup lifecycle गिनकर Hilt या manual में से एक रणनीति चुनें। Hilt को अभी remove न करें। |
| Drive reliability | scope और page-token recovery/cancellation स्पष्ट हैं; generic retry/backoff और real runtime consent/revoke evidence सत्यापित नहीं (`GoogleDriveAuth.kt`; `GoogleDriveServiceImpl.kt:97-171,372-413`). | चरण 6 में exact HTTP failure handling, pagination limits, retry/backoff, cancel, account switch, revoke और offline tests जोड़ें। |
| Background workers | WorkManager, continuation, retry and opt-out cancellation paths हैं; `OcrBatchRuntime` process-local bridge configuration gaps लौटा सकता है (`core/background/...`; `OcrBatchRuntime.kt:6-51`). | चरण 7 में process death, duplicate scheduling, stop/cancel, retry idempotence और source-file lifecycle परीक्षण करें। |
| Database metadata mismatch | source Room v5; checked-in schema v2; `DatabaseConstants` version/name अलग हैं। | चरण 2/5 में generated schema, every migration, real legacy fixtures और सभी references सत्यापित करें; फिर ही सुधार-निर्णय लें। |
| Remote embedding client | Gated authenticated client मौजूद, पर inspected production search path से call site नहीं मिला (`EmbeddingWorkerClient.kt:81-143`). | चरण 9 में repository-wide references/network audit और privacy review; user consent के बिना document/OCR/chunk/vector network path न बनाएं। |

## REMOVE — अभी कुछ भी स्वीकृत नहीं

**स्वीकृत removals: शून्य।** Phase 1 map से कोई module/file/dependency सुरक्षित रूप से हटाने का निष्कर्ष नहीं निकलता। Removal से पहले exact declaration/reference search, direct/transitive Gradle graph, tests that protect behavior, baseline backup, छोटी scoped diff, tests/evidence और `DELETION-LOG.md` entry चाहिए।

## आगे की gate

पहले चरण 2 में wrapper/JDK/SDK, dependency resolution, debug build, unit tests और lint का वास्तविक परिणाम दर्ज करें। Build/test failures का कारण जाने बिना dependency cleanup या refactor न करें। इस फाइल से कोई production-ready या release-ready दावा नहीं बनता।
