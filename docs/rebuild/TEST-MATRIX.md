# Test Matrix — Phase 1 inventory

**तारीख:** 20 नवंबर 2026
**Branch / निरीक्षण SHA:** `audit/fixes-2026-11` / `fd6a35ca49622217c876bdb3758aeafab978adbf`
**इस phase में execution:** कोई build, test, lint, scanner या workflow नहीं चलाया गया। नीचे test intent source/configuration से है; passing status का दावा नहीं।

| क्षेत्र | पहचाना source evidence | अपेक्षित सत्यापन | स्थिति |
|---|---|---|---|
| Room migration/schema | `core/database/src/androidTest/.../SqlCipherRoomInstrumentedTest.kt`; `LegacyV1Database.kt`; `core/database/src/test/.../OcrRetryMigrationTest.kt`; checked schema `.../VVFDatabase/2.json` | Fresh schema, 1→5 encrypted legacy upgrade, हर adjacent migration, data/FTS preservation, wrong-key fail-closed और generated schema v5 parity। | Not run; schema/source version gap खुला। |
| SQLCipher compatibility/key | `core/database-compat/src/androidTest/.../SqlCipherCompatibilityTest.kt`; `PortableDatabaseKeyMigrationTest.kt`; `core/security/src/test/.../CryptoSecurityManagerTest.kt` | Legacy fixture availability, correct/wrong passphrase, key wrap/recovery/rollback, protected-data keystore-loss behavior। | Not run; fixture/runtime outcome सत्यापित नहीं। |
| OCR + FTS | `core/database/src/test/.../OcrSearchDaoTest.kt`; OCR migration tests; plugin/domain OCR source | Latin/Devanagari output, long text/1M boundary, blank result, tags, FTS + LIKE sync, PDF page handling and export cleanup। | Not run; full-text/chunking behavior device पर सत्यापित नहीं। |
| Background/OCR work | `core/background/src/test/.../OcrBatchWorkerTest.kt`; `BackgroundSyncManagerTest.kt` | opt-out cancellation, process/worker stop, bounded continuation, retry/no-progress, indexing failure के बाद OCR scheduling। | Not run; WorkManager runtime नहीं चलाया। |
| Database/security cryptography | `core/security/src/test/.../PortableBackupCryptoTest.kt`; `CryptoSecurityManagerTest.kt`; SQLCipher instrumentation source | Wrong password/tamper/truncation, buffer wipe, lost key refusal, encrypted DB header/reopen। | Not run। |
| Semantic search | `core/domain/src/test/.../SemanticSearchUseCaseTest.kt` | Use case filtering, candidate composition, thresholds/dedup; production model-vs-hash requirement अभी पूरा नहीं। | Not run; test fake production embedding का प्रमाण नहीं। |
| Drive OAuth/API/sync | source map: `GoogleDriveAuth.kt`, `GoogleDriveServiceImpl.kt`, `CloudSyncUseCase.kt` | scope grant, consent resolution, pagination/repeated token, cancellation, retry/backoff, upload/download, offline behavior, revoke/account switch। | इस map में संबंधित Drive test files सत्यापित नहीं; **सत्यापित नहीं**। |
| Gradle/build/lint | `.github/workflows/ci.yml` configured steps: `testDebugUnitTest`, Room schema, `lintDebug`, `assembleDebug`; CI artifact records | wrapper/JDK/SDK/dependency resolution तथा actual command results। | इस phase में नहीं चलाया। |
| Android instrumentation | `ci.yml` और `weekly-instrumented.yml` configured hard/soft gate distinction | SQLCipher, migration और app tests on emulator/real device; exact SHA-bound XML. | इस phase में नहीं चलाया। |
| ORT/CPAS/SBOM | `.github/workflows/nightly-ort.yml`, `cpas-gate.yml`, `tools/audit/*` | Exact-SHA provenance, nonempty/valid report/SBOM, exclusions/coverage impact और gate result। | Workflow नहीं चलाया; baseline artifacts capture status तक सीमित। |
| Cloudflare worker | `.github/workflows/worker-validation.yml`; `cloudflare/drive-semantic-embed/package.json` | `npm test`, Wrangler dry-run और auth/consent contract। | इस phase में नहीं चलाया। |

## निष्पादन रिपोर्ट

- चलाए गए test commands: **कोई नहीं**.
- CI run IDs/links: **इस Phase 1 परिवर्तन के लिए कोई नया run नहीं**.
- परिणाम: repository map तैयार; verification pending. किसी test को PASS नहीं कहा गया।
