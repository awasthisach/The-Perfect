# VVF Smart Manager — Baseline (चरण 0)

**स्थिति: PASS — केवल Baseline पूरा।**
**आगे के चरण: शुरू नहीं किए गए।**
**Release स्थिति: NO-SHIP** — इस baseline से ऐप की build, सुरक्षा या release-readiness प्रमाणित नहीं होती।

## 1. दायरा और capture समय

यह रिपोर्ट केवल `awasthisach/The-Perfect` repository के read-only निरीक्षण और evidence preservation पर आधारित है। GitHub API snapshots 20 नवंबर 2026 को लगभग **01:50–01:53 UTC (07:20–07:23 IST)** के capture window में लिए गए। PR, branch, workflow और artifact की नीचे दी गई स्थिति उसी snapshot समय की है।

कोई source, build, workflow या test file नहीं बदली गई और कोई file/commit/workflow log/artifact delete नहीं किया गया। `main` में कोई बदलाव नहीं किया गया। इस रिपोर्ट से पहले audit checkout साफ था।

## 2. Git और PR baseline

| वस्तु | capture समय पर स्थिति |
|---|---|
| Repository | `awasthisach/The-Perfect` — VVF Smart Manager |
| `main` SHA | `cc49da286430810c322a5969cc189a8de0cb6ad0` |
| `audit/fixes-2026-11` SHA | `3874943252b63be6fe7bef120b7e70380a5ea6f5` |
| PR #225 head | `3874943252b63be6fe7bef120b7e70380a5ea6f5` (`audit/fixes-2026-11`) |
| PR #225 base | `cc49da286430810c322a5969cc189a8de0cb6ad0` (`main`) |
| PR #225 state | **Draft + Open + Unmerged** (`state=open`, `draft=true`, `merged=false`) |
| PR प्रमाण | [PR #225](https://github.com/awasthisach/The-Perfect/pull/225); API response bundle में `api/pr-225.json` |

शाखा SHA का API evidence bundle में `api/branch-main.json` और `api/branch-audit_fixes-2026-11.json` में है। PR को merge या close नहीं किया गया।

## 3. Workflow baseline

GitHub Actions API ने कुल **5,341 runs** लौटाए; 54 pages के सभी 5,341 run records index किए गए। पूर्ण line-by-line सूची `api/workflow-run-index.csv` और `api/workflow-runs-normalized.json` में है।

| स्थिति | संख्या |
|---|---:|
| Completed — success | 4,878 |
| Completed — failure | 159 |
| Completed — cancelled | 300 |
| Pending | 2 |
| In progress | 2 |

### Capture समय पर लंबित/चल रहे runs

| Run | Workflow | स्थिति | SHA |
|---|---|---|---|
| [44652660183](https://github.com/awasthisach/The-Perfect/actions/runs/44652660183) | Nightly ORT Compliance Scan | Pending | `3874943252b63be6fe7bef120b7e70380a5ea6f5` |
| [44652660136](https://github.com/awasthisach/The-Perfect/actions/runs/44652660136) | CPAS Production Gate | Pending | `3874943252b63be6fe7bef120b7e70380a5ea6f5` |
| [44651911193](https://github.com/awasthisach/The-Perfect/actions/runs/44651911193) | Nightly ORT Compliance Scan | In progress | `b70221dad808862d375fcb15ef324cbd6286783b` |
| [44651911125](https://github.com/awasthisach/The-Perfect/actions/runs/44651911125) | CPAS Production Gate | In progress | `b70221dad808862d375fcb15ef324cbd6286783b` |

159 failed runs के पूरे downloadable logs सुरक्षित करके ZIP integrity जाँची गई। Failure count workflow के अनुसार:

| Workflow | Failed runs |
|---|---:|
| CPAS Production Gate | 72 |
| VVF Smart Manager CI & Quality Gate | 45 |
| Weekly Instrumented Hard Gate | 30 |
| Nightly ORT Compliance Scan | 7 |
| SBOM Alternative Trial (CycloneDX + OSV) | 5 |

सबसे नए failed run IDs क्रमशः: SBOM Alternative Trial `44652660215`, CPAS Production Gate `44638232660`, Nightly ORT Compliance Scan `44497647512`, VVF Smart Manager CI & Quality Gate `44491564739`, और Weekly Instrumented Hard Gate `44467333093`। प्रत्येक failure log और run metadata bundle में `logs/`, `api/failed-workflows.json` तथा `api/workflow-run-index.csv` के माध्यम से उपलब्ध है। **Failure root-cause analysis इस चरण में नहीं किया गया।**

## 4. Artifacts और सुरक्षित प्रतियाँ

Artifact API snapshot में कुल **967 artifacts** सूचीबद्ध थे। नामों में ORT/CPAS/SBOM identifiers मिलाने पर 563 unexpired artifacts मिले और सभी के ZIP archives डाउनलोड करके CRC/member integrity सत्यापित की गई:

- **451 CPAS-labelled artifacts** और **112 ORT-labelled artifacts** सुरक्षित किए गए।
- किसी artifact name में `sbom` नहीं था। Snapshot में `SBOM Alternative Trial` के पाँचों runs failed थे और उन पाँच run IDs से कोई artifact जुड़ा नहीं था। इसलिए SBOM artifact की उपलब्धता **सत्यापित नहीं** है; पूरी 967-entry artifact inventory और उन failed runs के logs सुरक्षित हैं।
- 159/159 failed workflow logs डाउनलोड हुए; 563/563 चुने गए ORT/CPAS artifacts डाउनलोड हुए; downloader manifest में **0 failures** दर्ज हैं।

पूर्ण backup: `The-Perfect-baseline-evidence-20261120.tar.gz`
SHA-256: `f985bf6e9b313ed24e2e6e49c85d4a08a0986c645b74e669fc8d9c3f8c665e52`
आकार: 36,928,628 bytes. Bundle में `SHA256SUMS` है; extraction के बाद `sha256sum -c SHA256SUMS` से सत्यापन किया जा सकता है।

Bundle में PR/branch API evidence, सभी workflow runs की सूची, failed-run logs, पूरा artifact metadata, डाउनलोड किए गए ORT/CPAS artifacts, source-tree snapshot, dependency/module inventory और Room/schema/migration files की प्रति है।

## 5. Source, modules और dependencies — केवल inventory

`settings.gradle.kts` में **21 Gradle modules** दर्ज हैं:

- App: `:app`
- Core: `:core:common`, `:core:model`, `:core:security`, `:core:database`, `:core:database-compat`, `:core:data`, `:core:domain`, `:core:background`, `:core:cloud-gdrive`, `:core:plugin-spi`
- Features: `:feature:explorer`, `:feature:vault`, `:feature:cleaner`, `:feature:search`, `:feature:cloud`, `:feature:settings`, `:feature:plugins`
- Plugins: `:plugins:plugin-ocr`, `:plugins:plugin-semantic-search`, `:plugins:plugin-cloud-drivers`

Tracked build/settings/catalog file सूची और Gradle script में dependency/plugin reference की static scan प्रति bundle में `source-build-file-list.txt` और `dependency-declarations.txt` के रूप में सुरक्षित है। Static scan में 354 matching lines हैं; यह **resolved dependency graph नहीं** है। Version catalog तथा Gradle wrapper भी source snapshot में सुरक्षित हैं। दिखे हुए मुख्य baseline versions: Gradle wrapper 9.3.1, AGP 9.1.1, Kotlin 2.4.21, KSP 2.3.14, Room 2.7.0, Hilt 2.60.1, SQLCipher Android 4.20.0। कोई dependency resolution/build नहीं चलाया गया।

## 6. Room schema और migration evidence

कुल 10 tracked Room/database/schema/migration-संबंधित paths अलग सूचीबद्ध और source snapshot में सुरक्षित हैं (`source-snapshots/room-files.txt`):

- `core/database/schemas/README.md`
- `core/database/schemas/com.vvf.smartmanager.core.database.VVFDatabase/2.json`
- `core/database-compat/src/androidTest/java/com/vvf/smartmanager/core/database/compat/PortableDatabaseKeyMigrationTest.kt`
- `core/database-compat/src/androidTest/java/com/vvf/smartmanager/core/database/compat/SqlCipherCompatibilityTest.kt`
- `core/database/src/androidTest/java/com/vvf/smartmanager/core/database/LegacyV1Database.kt`
- `core/database/src/androidTest/java/com/vvf/smartmanager/core/database/SqlCipherRoomInstrumentedTest.kt`
- `core/database/src/test/java/com/vvf/smartmanager/core/database/OcrRetryMigrationTest.kt`
- `core/database/src/test/java/com/vvf/smartmanager/core/database/OcrSearchDaoTest.kt`
- `core/database/src/test/java/com/vvf/smartmanager/core/database/OcrTagMutationTest.kt`
- `core/database/src/test/java/com/vvf/smartmanager/core/database/VVFDatabaseDaoTest.kt`

## 7. Build/test निष्पादन और सीमाएँ

यह केवल baseline चरण था; build, unit/instrumented tests, lint, ORT rerun, SBOM rerun, security/network analysis या device test **नहीं** चलाए गए। इसलिए कोई test pass दावा नहीं है। CI का कोई हरा run ऐप को release-ready सिद्ध नहीं करता।

चरण 0 का baseline gate पास है: आवश्यक SHA दर्ज हैं; PR स्थिति API evidence सहित दर्ज है; उपलब्ध failed-run logs/artifacts, workflow inventory, modules/dependency scan और Room evidence सुरक्षित व सत्यापित हैं; कोई deletion नहीं हुई। अगला चरण इस turn में **शुरू नहीं किया गया**।

## 8. Change log

- 2026-11-20 — Branch `audit/fixes-2026-11`; baseline parent SHA `3874943252b63be6fe7bef120b7e70380a5ea6f5`; जोड़ी गई file: `docs/rebuild/BASELINE.md`; कारण: चरण 0 का प्रमाण-आधारित baseline। Tests: नहीं चलाए (scope के अनुसार)। Evidence: [PR #225](https://github.com/awasthisach/The-Perfect/pull/225), workflow links ऊपर और संलग्न evidence bundle। बचा जोखिम: पाँच SBOM workflow failures का कारण और कोई अलग SBOM artifact उपलब्धता अभी सत्यापित नहीं; app NO-SHIP। इस दस्तावेज़ को जोड़ने वाले commit का अंतिम SHA Git history में दर्ज होगा।
