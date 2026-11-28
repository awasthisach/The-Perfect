# Data Flow और Privacy Map — चरण 1

**Repository:** `awasthisach/The-Perfect`
**Branch / निरीक्षण SHA:** `audit/fixes-2026-11` / `fd6a35ca49622217c876bdb3758aeafab978adbf`
**दायरा:** source-based flow map; network/runtime परीक्षण नहीं।

## 1. App startup → encrypted Room → repositories

1. Manifest launcher `MainActivity` को `VVFApplication` के साथ शुरू करता है (`app/src/main/AndroidManifest.xml:26-71`). `VVFApplication` readiness barrier के भीतर setup करता है (`VVFApplication.kt:120-150`; `StartupReadiness.kt:21-74`).
2. App production path में `CryptoSecurityManager` initialize करता है, SQLCipher native library load करता है, key/passphrase प्राप्त करता है और `VVFDatabase.buildEncryptedDatabase(...)` बुलाता है। JVM unit tests के लिए अलग in-memory path है (`VVFApplication.kt:152-210`).
3. Database खुलने के बाद `StorageManager`, file/search/vault repositories और use cases बनते हैं (`VVFApplication.kt:211-228,240-283`).
4. Room version 5 में file metadata, OCR text, FTS, vault, journal, cloud-sync और source-file entities हैं; migration 4→5 OCR text/FTS triggers/index जोड़ता है (`VVFDatabase.kt:27-49,113-162`).

**सीमा:** build और device execution नहीं किए गए। Source में encrypted DB construction wired है, पर runtime key safety, migration success और database recovery को इस phase में प्रमाणित नहीं किया गया। Checked-in schema JSON v2 बनाम source v5 और अलग `DatabaseConstants` values खुले प्रश्न हैं; इन्हें तत्काल बदलना नहीं है।

## 2. Local files → OCR → encrypted index → keyword search

1. App `OcrPluginImpl`, `OcrIndexingService`, `IndexOcrTextUseCase` और Room-backed `OfflineSearchRepository` बनाता है (`VVFApplication.kt:240-272,336-345`).
2. OCR engine ML Kit Latin और Devanagari recognizers को उपयोग करता है। Image/PDF text एक `OcrResult.fullText` string में लौटता है; PDF pages उसी text result में जोड़े जाते हैं (`plugins/plugin-ocr/.../OcrEnginePlugin.kt:35-57,76-128,131-184`; `core/model/.../OcrModels.kt:6-35`).
3. Background batch अधिकतम 40 **files** चुनता है; यह OCR text chunking नहीं है। Automatic OCR preference default false है; opt-in, engine-enabled state और readiness पर checks हैं। WorkManager periodic/immediate batches और continuation चलाता है; settings opt-out unique OCR work cancel करता है (`OcrBatchBootstrap.kt:56-147`; `BackgroundSyncManager.kt:181-241`; `MainActivityNavHost.kt:205-215`).
4. Nonblank full text file metadata के `ocrText` field में save होता है। Repository एक million characters की guard लगाती है; FTS4 content table OCR text सहित searchable fields को index करता है और LIKE fallback उपलब्ध है (`OcrUseCases.kt:39-78`; `OfflineSearchRepository.kt:146-159`; `FileMetadataEntity.kt:25-45`; `FileFtsEntity.kt:7-20`; `SearchFtsDao.kt:14-31,56-59`).
5. OCR plugin content URI inputs के लिए app-cache temp copy बनाकर extraction के बाद हटाता है। Optional user-triggered export OCR text को UTF-8 `.txt` file के रूप में बनाता है (`OcrEnginePlugin.kt:76-128`; `OcrUseCases.kt:84-109`). उस exported file के downstream protection को इस map में सत्यापित नहीं किया गया।

**निष्कर्ष:** पूर्ण OCR text का local persistence path मौजूद है; inspected path में text chunking नहीं दिखी। यह न तो OCR runtime success साबित करता है, न दूसरे uninspected code में transmission की अनुपस्थिति।

## 3. Keyword + local semantic search

### Keyword

`SearchFilesUseCase → OfflineSearchRepository → SearchFtsDao → Room FTS4/LIKE` (`SearchUseCases.kt:11-19`; `OfflineSearchRepository.kt:188-269`; `SearchFtsDao.kt:14-31,36-65`). Query स्थानीय file name/path/tags/MIME/OCR text metadata पर चलती है। OCR field FTS में है; fallback query name/tags/OCR text देखती है।

### Semantic

`SearchViewModel` keyword और semantic queries अलग चलाकर semantic hits को merge/deduplicate करता है (`feature/search/.../SearchViewModel.kt:38-103,138-151`). `SemanticSearchUseCase` अधिकतम 400 recent files से candidate text **file name + tags** बनाता है; inspected implementation OCR text या document body candidate नहीं जोड़ती (`core/domain/.../SemanticSearchUseCase.kt:13-70`). Production `SemanticSearchPluginImpl` 128-dimensional deterministic token/category hash projections, normalization और cosine score उपयोग करता है; learned/neural embedding model नहीं। उसका compatibility `downloadModel` method source में model download/network call नहीं करता (`plugins/plugin-semantic-search/.../SemanticSearchPluginImpl.kt:15-148`; `ISemanticSearchEngine.kt:10-36`).

## 4. Google sign-in और Drive boundary

- `GoogleDriveAuth` Drive authorization के लिए `drive.file` scope माँगता है। अलग Credential Manager path Google ID token लेता है, जिसे `MainActivity` Firebase Auth sign-in के लिए उपयोग करता है; वह Drive bearer token नहीं है (`GoogleDriveAuth.kt:22-33,37-97`; `MainActivity.kt:88-172`).
- Interactive Drive consent/result `MainActivity` में संभलता है; Cloud route/view model token result को service/authentication path तक ले जाते हैं (`MainActivity.kt:88-128`; `MainActivityNavHost.kt:140-156`; `CloudRoute.kt:14-44`; `CloudViewModel.kt:86-154`).
- `GoogleDriveServiceImpl`/`DriveApi` bearer-authorized Google Drive v3 listing, media download, multipart upload, folder lookup/create और quota calls देते हैं। Service में Drive delete method घोषित नहीं है (`DriveApi.kt:17-88`; `GoogleDriveServiceImpl.kt:76-95,97-255,257-437`). Pagination incomplete search/repeated token पर fail होती है और rejected page-token के लिए एक bounded restart है; अन्य network errors के लिए व्यापक retry guarantee सत्यापित नहीं।
- Cloud sync/backup flow `CloudSyncUseCase → GoogleDriveService`; restore downloader service से artifact डाउनलोड/stage करता है (`CloudSyncUseCase.kt:33-180`; `GoogleDriveBackupDownloader.kt:15-88`). यह Google Drive पर user-authorized cloud storage flow है; इसे बाहरी AI embedding call के साथ न मिलाएँ।
- Online disconnect provider revocation का प्रयास कर local token/account state साफ करता है; offline-only path local state साफ करता है पर remote revocation न होने की बात स्पष्ट करता है (`GoogleDriveServiceImpl.kt:372-413`).

## 5. संभावित remote embedding endpoint — स्पष्ट privacy gate

`app/src/main/java/com/vvf/smartmanager/embedding/EmbeddingWorkerClient.kt:19-48,81-143` एक अलग authenticated HTTP POST client रखता है। Code के अनुसार request में supplied base URL का `/embed`, texts, Firebase Auth bearer और Firebase App Check headers होते हैं; offline-only mode, remote-AI consent, nonblank base URL और credentials checks के बिना network call नहीं करनी चाहिए।

Inspected production search path में इस client को `SearchViewModel`/`SemanticSearchUseCase` से बुलाने वाला call site नहीं मिला। इसलिए client की मौजूदगी को automatic upload का प्रमाण नहीं माना गया। पर यह सीमित source search है, पूरे repository/network audit का प्रमाण नहीं। **किसी भी भविष्य के wiring से पहले** explicit user option/consent, न्यूनतम data, revoke behavior और अलग privacy review चाहिए। Document/OCR text, chunks, private fingerprints या embeddings को अपने-आप external AI पर भेजने की अनुमति नहीं है।

## 6. Manifest/diagnostic observations

- Manifest source `INTERNET` और `ACCESS_NETWORK_STATE` permissions, `allowBackup=false`, `usesCleartextTraffic=false` और non-exported `FileProvider` घोषित करता है (`app/src/main/AndroidManifest.xml:5-71`). Referenced network-security config, merged manifest और effective device policy इस map में inspect/execute नहीं हुए।
- OCR batch failure log local filenames, paths, OCR text और exception details omit करने का source claim है; यह पूरे app के logs की audit नहीं (`OcrBatchBootstrap.kt` संबंधित failure path).
- Database में file paths/names/tags/OCR text जैसे निजी metadata हैं। Database encryption code मौजूद होना सभी backup/export/cache paths के protected होने का प्रमाण नहीं।

## 7. सत्यापित नहीं

कोई HTTP request, Drive consent, token, revocation, OCR, local database migration, worker, model, release workflow या Android device इस चरण में चलाया नहीं गया। कोई production base URL/model/deployment state, actual consent configuration, data transmission absence, model residency, or real-world cancellation behavior इस दस्तावेज़ से सिद्ध नहीं होता।
