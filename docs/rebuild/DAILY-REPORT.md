# Daily Report — 2026-11-20

## कार्य-परिचय

- Repository: `awasthisach/The-Perfect` (VVF Smart Manager)
- Branch: `audit/fixes-2026-11`
- पुराना/निरीक्षण SHA: `fd6a35ca49622217c876bdb3758aeafab978adbf`
- कार्य: चरण 1 — Project Map
- नया Phase 1 commit SHA: इस बदलाव के पूर्ण होने पर final task update में दर्ज होगा; commit स्वयं अपना ID नहीं रख सकता।
- PR #225: baseline snapshot में Draft/Open/Unmerged; आज का live PR status query नहीं किया गया।

## बदली/जोड़ी गई फाइलें

- `docs/rebuild/README.md`
- `docs/rebuild/ARCHITECTURE.md`
- `docs/rebuild/DATA-FLOW.md`
- `docs/rebuild/KEEP-REWRITE-REMOVE.md`
- `docs/rebuild/DELETION-LOG.md`
- `docs/rebuild/TEST-MATRIX.md`
- `docs/rebuild/RISK-REGISTER.md`
- `docs/rebuild/RELEASE-CHECKLIST.md`
- `docs/rebuild/DAILY-REPORT.md`

`BASELINE.md` में कोई बदलाव नहीं। `.serena/` local untracked directory stage/commit नहीं की जाएगी।

## बदलाव का कारण और परिणाम

- Source map में exact 21 modules (1 app, 10 core, 7 features, 3 plugins) सत्यापित; direct Gradle dependencies, Android entry/startup, Room/SQLCipher, Drive/OCR/search flows और नौ workflow files दर्ज।
- `KEEP-REWRITE-REMOVE` source आधारित planning inventory है; REMOVE स्वीकृत: 0.
- मुख्य खुले मुद्दे: schema export v2 बनाम DB source v5; semantic plugin hash-based है; OCR chunking/export protection; DI strategy; remote embedding client के पूरे-repository call sites; actual workflow/build/test results।
- कोई source/build/workflow/test file edit, delete, deploy या run नहीं।

## Validation

- Tests/build/lint: **कोई नहीं चलाया** — Phase 1 scope read-only map था।
- CI/workflow run link/ID: कोई workflow manually dispatch नहीं किया; branch push से trigger होने वाले run (यदि कोई) की स्थिति final update में दर्ज होगी।
- Deletions: **0**।
- Release निर्णय: **NO-SHIP**।

## Workspace note

Serena project activation के बाद local `git status` में untracked `.serena/` दिखी; origin/contents स्वतंत्र रूप से सत्यापित नहीं किए गए। Serena onboarding calls दो बार timeout हुए और onboarding completion दर्ज नहीं हुई। `.serena/` को intact, unstaged रखा गया।


## Phase 2 — Build स्थिर करना (20 नवंबर 2026)

- आधार SHA: `8ea352753dda545105c6fc0411cb55e33b7bf792`; branch: `audit/fixes-2026-11`.
- मुख्य Build/Test/Lint/Debug APK: PASS — [run 44664981314](https://github.com/awasthisach/The-Perfect/actions/runs/44664981314).
- SQLCipher hard instrumentation: PASS — [run 44664981370](https://github.com/awasthisach/The-Perfect/actions/runs/44664981370); app 4 + database 3 + database-compat 3 = 10 tests, 0 failures/errors/skips; exact-SHA validator PASS.
- मुख्य CI के optional emulator ने `-accel off` के कारण boot नहीं किया; log में software CPU emulation unsupported। Weekly hard workflow ने KVM + `-accel on` से उसी SHA पर सफल boot/test किया।
- सीमित workflow सुधार: `.github/workflows/ci.yml` अब KVM availability/accessibility check के बाद `-accel on` उपयोग करता है; KVM न हो तो explicit soft skip।
- SBOM Trial [44664981565](https://github.com/awasthisach/The-Perfect/actions/runs/44664981565) FAIL — CycloneDX/Hilt `hiltCompileOnlyDebug/Release` provider query error। ORT [44664981434] और CPAS [44664981409] capture समय पर PENDING।
- Build config निरीक्षण: Gradle 9.3.1, JDK17, AGP 9.1.1, Kotlin 2.4.21, KSP 2.3.14, compile/target SDK 36, min SDK 24। कोई version/dependency deletion या update नहीं।
- बदली/जोड़ी फाइलें: `.github/workflows/ci.yml`, `docs/rebuild/README.md`, `docs/rebuild/DAILY-REPORT.md`, नया `docs/rebuild/PHASE-2-BUILD-STABILITY.md`।
- Tests after workflow correction: नए commit पर rerun अभी बाकी; इसलिए Phase 2 **IN PROGRESS**। Deletions: 0. Release: NO-SHIP.


## Phase 2 exact-SHA recheck — 20 नवंबर 2026

- सत्यापित candidate SHA: `43dc4a868f3353e11ecaa9722b447127626910ed`.
- PR #225: Draft=true, Open, Unmerged; base `main` SHA `cc49da286430810c322a5969cc189a8de0cb6ad0`.
- मुख्य Build/Test/Lint/Debug APK: **PASS**, [run 44667847110](https://github.com/awasthisach/The-Perfect/actions/runs/44667847110). JDK/SDK, fixture, unit tests, Room schema export, lint, APK और evidence upload सफल।
- मुख्य CI emulator: KVM check और boot सफल; logs में `emulator-5554 device`, 2 instrumentation batches completed, summary `Emulator booted: true`, `Instrumented outcome: success`. पहले रिपोर्ट में इसे skipped बताना गलत था; केवल skip-annotation step skipped था।
- SQLCipher hard gate: **PASS**, [run 44667847130](https://github.com/awasthisach/The-Perfect/actions/runs/44667847130); 10 tests, 0 failures, 0 errors; exact-SHA report validator PASS.
- Artifacts verified: Debug APK (51,184,441 bytes), Room schema, unit tests, lint, Android test reports, emulator diagnostics; SQLCipher exact-SHA report artifact.
- SBOM Alternative Trial: **FAIL**, [run 44667847167](https://github.com/awasthisach/The-Perfect/actions/runs/44667847167). CycloneDX `:app:cyclonedxDirectBom` failed when Hilt `hiltCollectClasses*` providers queried `hiltCompileOnly*` configurations. This is isolated from the Android Build gate.
- Narrow correction prepared: exclude only Hilt-generated `hiltCompileOnly.*` configurations from CycloneDX scan; no dependency, Hilt, ORT policy, or CPAS gate removal. Rerun required.
- ORT [run 44667847521](https://github.com/awasthisach/The-Perfect/actions/runs/44667847521) and CPAS [run 44667847137](https://github.com/awasthisach/The-Perfect/actions/runs/44667847137) remain pending at capture.
- Phase 2: **COMPLETE**. Overall release: **NO-SHIP** until ORT/SBOM/CPAS and owner/device acceptance pass.
- Deletions: 0. No main change, no force push, no new branch.


## Phase 3 — Supply-chain evidence review (20 नवंबर 2026)

- Evidence SHA: `9d3f0914cff72e1a83a9a40395ef924250b2df3e`.
- SBOM Alternative Trial push [44675907899](https://github.com/awasthisach/The-Perfect/actions/runs/44675907899) और PR [44675911810](https://github.com/awasthisach/The-Perfect/actions/runs/44675911810): **SUCCESS**; artifact [14602655106](https://github.com/awasthisach/The-Perfect/actions/runs/44675907899/artifacts/14602655106) डाउनलोड और JSON inspection हुआ।
- CycloneDX 771 components; SPDX 771 packages; package URL parity PASS; CycloneDX license metadata 746, SPDX declared licenses 701. 45 external license metadata mappings SPDX conversion में खो गए; 25 project components के पास source license metadata नहीं।
- OSV-Scanner: 21 affected packages, 94 package-level entries, 51 unique advisory IDs (3 critical, 19 high, 26 moderate, 3 low). Exit codes 1; इसे compliance PASS नहीं माना।
- Vulnerable versions के dependency paths test/build tooling की ओर संकेत करते हैं, पर release runtime boundary अभी **सत्यापित नहीं**।
- OSV license summary 771 UNKNOWN देता है, जबकि CycloneDX में 746 license metadata है; इस scanner output को license clearance नहीं माना।
- `net.zetetic:sqlcipher-android:4.20.0` का SBOM license metadata खाली है। Zetetic official documentation Maven Central package को Community Edition बताती है; BSD-style license और attribution/reproduction requirements लागू हैं।
- SBOM workflow में dual scope (all-configurations + releaseRuntimeClasspath), Android testConfigs labeling और separate OSV scans जोड़े गए। इस नए workflow run का परिणाम अभी pending है।
- Build/Test/Lint/Debug APK: **PASS**, [44675911766](https://github.com/awasthisach/The-Perfect/actions/runs/44675911766); SQLCipher hard gate **PASS**, [44675911776](https://github.com/awasthisach/The-Perfect/actions/runs/44675911776); Worker validation **PASS**, [44675911803](https://github.com/awasthisach/The-Perfect/actions/runs/44675911803).
- ORT [44675911761] और CPAS [44675911771] अभी pending/queued; completion प्रमाण **सत्यापित नहीं**।
- Phase 3 **IN PROGRESS**; app dependency changes/deletions: 0; release: NO-SHIP.


## Cross-check और ORT cache root-cause correction — 21 नवंबर 2026

- Live GitHub API recheck: PR #225 Draft/Open/Unmerged; branch `audit/fixes-2026-11` और PR head दोनों `b8c00a21a4a515807006fe4d5277db02d162d417` थे। Base `cc49da286430810c322a5969cc189a8de0cb6ad0`। GitHub compare: 344 commits, 199 files, +13,763/-1,502।
- पुराने Phase 2 section में प्रारंभिक “IN PROGRESS” लाइन ऐतिहासिक थी; बाद का exact-SHA recheck section Phase 2 **COMPLETE** दर्ज करता है। वर्तमान Phase 2 स्थिति COMPLETE है; उस शुरुआती लाइन को वर्तमान स्थिति न माना जाए।
- ORT run [44700427117](https://github.com/awasthisach/The-Perfect/actions/runs/44700427117), SHA `b8c00a21a4a515807006fe4d5277db02d162d417`: Analyzer PASS; Scanner छह घंटे पर CANCELLED; CPAS [44700427691](https://github.com/awasthisach/The-Perfect/actions/runs/44700427691) BLOCKED। Build [44700427213](https://github.com/awasthisach/The-Perfect/actions/runs/44700427213), SQLCipher hard gate [44700427287](https://github.com/awasthisach/The-Perfect/actions/runs/44700427287), Worker [44700427239](https://github.com/awasthisach/The-Perfect/actions/runs/44700427239), supplementary SBOM [44700427174](https://github.com/awasthisach/The-Perfect/actions/runs/44700427174): PASS।
- Exact ORT cache logs: Gradle caches hit (लगभग 844 MB + 130 MB), लेकिन ORT scan-result cache key `Linux-ort-scan-results-cfbe09394cac630f25725138fd4f0f56586ab5b1c9f6ca64e660d177da447ef6` और restore prefix दोनों **CACHE MISS**। यह पहले “cache restored” मानने की गलती को सुधारता है; restored caches Gradle के थे, ORT scan-result cache का नहीं।
- Scanner ने ClearlyDefined पर कई timeout/HTTP 502 दिए। छह घंटे के cancellation के बाद ORT cache post-step skipped हुआ; इस कारण partial scan results अगली कोशिश के लिए persist नहीं हुए।
- लक्षित सुधार: ORT cache restore और save अलग किए गए; cache key में run ID/attempt शामिल होगा और save step `always()` पर verified checkout के बाद चलकर partial scanner results भी persist करने की कोशिश करेगा। अगली exact-SHA run में cache restore/save परिणाम और scanner progress से इसे validate करना आवश्यक है। यह अभी source-level सुधार है, PASS नहीं।
- No dependencies, licenses, ORT policy, shipped scopes, or fail-closed gates removed. PR remains Draft/Open/Unmerged; release NO-SHIP.
