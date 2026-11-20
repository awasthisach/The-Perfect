# Risk Register — प्रारंभिक source-based सूची

**तारीख:** 20 नवंबर 2026
**Branch / निरीक्षण SHA:** `audit/fixes-2026-11` / `fd6a35ca49622217c876bdb3758aeafab978adbf`
**स्थिति:** सभी entries provisional हैं; build/test/runtime validation नहीं हुआ। Release स्थिति **NO-SHIP**.

| ID | प्राथमिकता | जोखिम/अवलोकन | स्रोत-साक्ष्य | अगला प्रमाण/नियंत्रण | स्थिति |
|---|---|---|---|---|---|
| R-01 | P0 | Room source schema v5 है, जाँचा checked-in export v2 है; अलग `DatabaseConstants` version/name भी अलग हैं। गलत export या migration data risk हो सकता है। | `VVFDatabase.kt:27-49,51-162`; schema `2.json:1`; `DatabaseConstants.kt:6-9` | Phase 2/5 में KSP-generated v5 schema, fresh DB, every migration, legacy data preservation और SQLite/SQLCipher tests। | Open — परिणाम सत्यापित नहीं। |
| R-02 | P0 | Production app encrypted builder को call करता है और keystore-protected passphrase source है; secure migration/recovery runtime success अभी सिद्ध नहीं। | `VVFApplication.kt:152-210`; `CryptoSecurityManager.kt:83-151,197-239`; `VVFDatabase.kt:164-201` | Unit + emulator/real-device wrong-key, lost-key, legacy migration, restore, reopen tests। | Open — run नहीं। |
| R-03 | P0 | Drive/Firebase config, consent resolution, runtime revoke और error/retry behavior remote environment में सत्यापित नहीं। | `GoogleDriveAuth.kt`; `MainActivity.kt:88-172`; `GoogleDriveServiceImpl.kt:97-171,372-413` | Minimal-scope OAuth, pagination/cancel/retry/revoke tests; credentials/secrets validate without exposing values। | Open — source-only। |
| R-04 | P0 | अलग `EmbeddingWorkerClient` supplied texts को authenticated remote `/embed` पर भेजने की क्षमता रखता है। Inspected search path में caller नहीं मिला; पूरे repository में call-site absence सिद्ध नहीं। | `app/src/main/java/com/vvf/smartmanager/embedding/EmbeddingWorkerClient.kt:81-143`; local search flow `SemanticSearchUseCase.kt:13-70` | Repo-wide reference/network audit; default no-call guarantee; यदि कभी सुविधा हो तो explicit consent/revoke/minimization और privacy review। | Open — कोई network execution नहीं। |
| R-05 | P1 | Production “semantic” search deterministic hash vectors है, वास्तविक learned local embeddings नहीं; user objective से mismatch। | `SemanticSearchPluginImpl.kt:15-148`; `ISemanticSearchEngine.kt:10-36` | चरण 8 में on-device model/license/size/offline benchmark, quality tests और data migration योजना। | Open — rewrite नहीं किया। |
| R-06 | P1 | OCR full text एक `ocrText` field में है; 1M character save guard है और inspected path में chunking नहीं दिखी। Exported `.txt` file के protection की पुष्टि नहीं। | `OcrModels.kt`; `OfflineSearchRepository.kt:146-159`; `OcrUseCases.kt:39-109` | लंबी documents, PDF, chunks/index schema, encryption, export retention/cleanup और opt-out tests। | Open। |
| R-07 | P2 | Hilt plugin/runtime के साथ manual `VVFApplication`/`AppCompositionRoot` construction मौजूद; project-wide DI consistency ज्ञात नहीं। | `app/build.gradle.kts:3-12,147-149`; `VVFApplication.kt:152-365`; `AppCompositionRoot.kt:23-78` | चरण 4 में generated Hilt modules/annotations/references तथा startup/test implications की पूर्ण inventory। | Open — कोई dependency removal नहीं। |
| R-08 | P2 | Workflow YAML exact-SHA gates पर निर्भर है, पर इस phase में न run हुआ न current remote state/artifact contents जाँचे गए; ORT composite default image `latest` है। | `.github/workflows/{ci,nightly-ort,cpas-gate,weekly-instrumented}.yml`; `.github/actions/ort/action.yml` | Phase 10 में actual run IDs/SHA, image digest pinning, SBOM parity और evidence expiry जाँचें। | Open। |
| R-09 | P1 | OCR/DB/semantic source tests मौजूद हैं, पर इस phase में कोई test execute नहीं हुआ; source declarations व्यवहार की सफलता नहीं हैं। | `TEST-MATRIX.md` | Phase 2 में buildable command matrix, test results व failures कारण सहित। | Open। |

## निर्णय सीमा

ये risk entries किसी module/file/dependency को हटाने की अनुमति नहीं देतीं। हर P0 failure या unknown को release gate पर रोक मानें जब तक direct evidence उपलब्ध न हो।
