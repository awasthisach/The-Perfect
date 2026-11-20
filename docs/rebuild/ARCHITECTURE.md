# Architecture Map — चरण 1

**Repository:** `awasthisach/The-Perfect` (VVF Smart Manager)
**Branch / निरीक्षण SHA:** `audit/fixes-2026-11` / `fd6a35ca49622217c876bdb3758aeafab978adbf`
**स्थिति:** केवल static source map; build, tests या runtime सत्यापन नहीं।

## 1. Gradle project inventory

`settings.gradle.kts:26-55` में **21 modules** हैं: 1 app + 10 core + 7 feature + 3 plugin modules। यह count सीधे `include(...)` declarations से सत्यापित है। `gradle/libs.versions.toml` केंद्रीय version/library/plugin catalog है। Root build plugin aliases को `apply false` के साथ रखता है और subprojects पर Jacoco configure करता है (`build.gradle.kts:2-22`).

| समूह | modules |
|---|---|
| App (1) | `:app` |
| Core (10) | `:core:common`, `:core:model`, `:core:security`, `:core:database`, `:core:database-compat`, `:core:data`, `:core:domain`, `:core:background`, `:core:cloud-gdrive`, `:core:plugin-spi` |
| Features (7) | `:feature:explorer`, `:feature:vault`, `:feature:cleaner`, `:feature:search`, `:feature:cloud`, `:feature:settings`, `:feature:plugins` |
| Plugins (3) | `:plugins:plugin-ocr`, `:plugins:plugin-semantic-search`, `:plugins:plugin-cloud-drivers` |

### Direct project dependencies

यह Gradle configuration graph है, runtime call graph नहीं। `api` edges consumers को API expose करते हैं; `implementation` edges ऐसा API exposure घोषित नहीं करते।

- `:app` 19 project modules पर `implementation` निर्भरता घोषित करता है: सभी core modules **सिवाय** `:core:database-compat`, सभी 7 feature modules और सभी 3 plugin modules (`app/build.gradle.kts:102-121`). `:core:database-compat` settings में शामिल है, पर app की direct project dependencies में नहीं।
- Core edges: `common → model (api)`; `model → —`; `security → common, model (api)`; `database → model, common, security (api)`; `database-compat → security (androidTestImplementation)`; `data → model, common, database, security, plugin-spi (api)`; `domain → data, database, model, plugin-spi, cloud-gdrive, common (api)`; `background → domain, common (api)`; `cloud-gdrive → model, common, plugin-spi (api)`; `plugin-spi → model, common (api)`.
- Feature edges: `explorer → domain, model, common`; `vault → domain, security, model, common`; `cleaner → domain, model, common`; `search → domain, model, common`; `cloud → domain, model, common, cloud-gdrive`; `settings → domain, model, common`; `plugins → plugin-spi, domain, model, common`, और `plugins → plugin-ocr (implementation)`.
- तीनों `:plugins:*` modules `plugin-spi`, `model`, `common` पर `api` निर्भरता घोषित करते हैं।

Module build files और configurations का पूरा स्रोत-सूची विवरण ऊपर के Gradle files में है। Transitive dependencies इस सूची में शामिल नहीं हैं। किसी Gradle edge को वास्तविक runtime उपयोग का प्रमाण न मानें।

## 2. Android entry point और startup

- Manifest `VVFApplication` को Application class घोषित करता है और `MainActivity` को exported `MAIN`/`LAUNCHER` entry बनाता है (`app/src/main/AndroidManifest.xml:26-71`). Manifest में network, storage/media और biometric permissions हैं; `allowBackup=false`, `usesCleartextTraffic=false` और non-exported `FileProvider` भी घोषित हैं (`:5-71`). Effective merged manifest/runtime permission state सत्यापित नहीं।
- `VVFApplication.onCreate` readiness initializer शुरू करता है। `StartupReadiness` initialization states/await gate देता है; initialization पूरा होने तक app navigation नहीं दिखाता (`VVFApplication.kt:120-150`; `StartupReadiness.kt:21-74`; `MainActivity.kt:179-229`).
- App initializer IO context में `CryptoSecurityManager` बनाता है; production path में SQLCipher native library लोड कर 32-byte database passphrase लेता है और `VVFDatabase.buildEncryptedDatabase(...)` बुलाता है। JVM unit test branch in-memory DB बनाती है। Room writable database खुलने के बाद repositories तैयार होती हैं (`VVFApplication.kt:152-228`).
- `VVFApplication` repository/use-case/plugin/background objects को constructors/factory methods से सीधे बनाता है; search, OCR, semantic, Drive और cloud-sync wiring इसके आगे के initialization में है (`VVFApplication.kt:240-365`; `AppCompositionRoot.kt:23-78`). App build में Hilt plugin/runtime भी मौजूद हैं (`app/build.gradle.kts:3-12,147-149`), लेकिन inspected app source में मुख्य construction path manual है। Hilt बनाम manual की repository-wide स्थिति चरण 4 में तय होनी है; अभी कुछ हटाया नहीं जाएगा।
- Compose NavHost Explorer से शुरू होकर Explorer, Vault, Cleaner, Search, Cloud, Plugins और Settings routes register करता है (`MainActivityNavHost.kt:45-220`; `NavigationItems.kt:28-83`).

## 3. Persistence और security boundaries

- `VVFDatabase` Room schema version **5**, छह entities/DAO accessors और version 1→2→3→4→5 migrations घोषित करता है; encrypted builder SQLCipher `SupportOpenHelperFactory` उपयोग करता है (`core/database/.../VVFDatabase.kt:27-49,51-162,164-211`).
- `CryptoSecurityManager` Android Keystore AES-GCM से 32-byte SQLCipher passphrase को wrap/persist करता है; existing database के साथ recovery material न होने पर नया key चुपचाप बनाने के बजाय fail-closed व्यवहार source में है (`core/security/.../CryptoSecurityManager.kt:83-151,197-239,330-358`). यह source व्यवहार है, सफल device/runtime test नहीं।
- जाँचा गया checked-in Room schema JSON version **2** है, जबकि `VVFDatabase` source version 5 कहता है (`core/database/schemas/com.vvf.smartmanager.core.database.VVFDatabase/2.json:1`; `VVFDatabase.kt:27-38`). एक अलग `DatabaseConstants` version 1 और अलग filename घोषित करता है (`DatabaseConstants.kt:6-9`). यह cleanup का तत्काल निर्देश नहीं; consumers/schema export और migration compatibility चरण 2/5 में सत्यापित करें।
- Room में file metadata (paths/tags/OCR text), FTS4, vault records/journal, cloud-sync records और source-file records हैं। OCR full text और vault/database content संवेदनशील डेटा हैं; encrypted builder का universal runtime use या backup/restore completeness केवल इस map से सिद्ध नहीं।

## 4. Subsystem map

- **Drive:** `core:cloud-gdrive` OAuth, Retrofit/OkHttp Drive API और service implementation देता है; `feature:cloud` UI/use case उपयोग करता है। `MainActivity` authorization result संभालता है। Drive scope `drive.file` है; ID-token/Firebase sign-in अलग path है।
- **OCR:** `:plugins:plugin-ocr` ML Kit Latin + Devanagari recognizers प्रदान करता है; `:core:domain` indexing/use case और `:core:background` WorkManager execution देते हैं। Automatic OCR opt-in default false है; batch window 40 files तक है।
- **Search:** keyword path Room FTS4 + LIKE fallback है। Semantic path app में `SemanticSearchPluginImpl` से wired है, जो source के अनुसार deterministic hashed-token similarity है, learned embedding model नहीं।
- **Cloud-driver plugin:** app initializer OneDrive/Dropbox/Nextcloud/S3/NAS driver implementations register करता है; इससे इन providers के सफल end-to-end sync का प्रमाण नहीं मिलता (`VVFApplication.kt:288-294`).

## 5. Build/CI और supply-chain workflow inventory

यह सूची YAML/config में लिखी intent है; कोई workflow इस चरण में dispatch या rerun नहीं किया गया।

| Workflow | Source-level कार्य |
|---|---|
| `ci.yml` | Python audit tests, Gradle unit tests/schema/lint/assemble; emulator start soft, सफल boot के बाद instrumentation hard; reports/evidence artifacts। |
| `nightly-ort.yml` | exact-SHA checkout, ORT analyzer/scanner/evaluator/advisor/reporter और report/SBOM validation, evidence artifact। |
| `cpas-gate.yml` | exact-SHA ORT + weekly instrumentation evidence जोड़कर CPAS verification; configured fail-closed gate। |
| `weekly-instrumented.yml` | KVM/emulator और connected tests का hard gate; सफल scheduled/manual run के बाद evidence-ledger publication path। |
| `sbom-alternative-trial.yml` | CycloneDX → SPDX parity और OSV scan trial; CPAS के ORT inputs से अलग। |
| `worker-validation.yml` | Cloudflare worker npm tests और Wrangler dry-run; deploy नहीं। |
| `deploy-embedding-worker.yml` | manual production deployment workflow; `confirm_deploy=yes`, protected `production` environment, Worker secrets/deploy और external health/auth smoke checks चाहिए। इसे dispatch नहीं किया गया। |
| `cleanup-stale-branches.yml` | read-only eligibility audit; YAML स्वयं कहता है deletion अलग owner-approved action है। इसे dispatch नहीं किया गया। |
| `release.yml` | manual release path; exact-SHA ORT/instrumented evidence, CPAS और release gates, signed artifact/SBOM generation। |

Workflow paths और gates के line references संबंधित `.github/workflows/*.yml`, `.github/actions/ort/action.yml` तथा `tools/audit/` में हैं। Baseline snapshot में दर्ज workflow outcomes केवल उसके capture समय की स्थिति हैं।

## 6. सत्यापित नहीं / सीमा

किसी build, dependency resolution, test, lint, emulator, real device, OAuth consent, Drive request, production Worker, GitHub workflow, current artifact, branch protection या release gate को इस चरण में execute/validate नहीं किया गया। यह document code readiness, secure runtime behavior या production suitability का दावा नहीं करता।
