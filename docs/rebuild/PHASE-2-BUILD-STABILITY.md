# Phase 2 — Build स्थिरता रिपोर्ट

**स्थिति: COMPLETE — exact candidate SHA `43dc4a868f3353e11ecaa9722b447127626910ed` पर मुख्य Build/Test/Lint/Debug APK और SQLCipher Instrumented Hard Gate दोनों PASS।**  
**Repository:** `awasthisach/The-Perfect`  
**Branch:** `audit/fixes-2026-11`  
**प्रारंभिक निरीक्षण SHA:** `8ea352753dda545105c6fc0411cb55e33b7bf792`  
**अंतिम सत्यापित SHA:** `43dc4a868f3353e11ecaa9722b447127626910ed`  
**तारीख:** 20 नवंबर 2026  
**रिलीज़ स्थिति:** **NO-SHIP** — Build पास होना product/security/release readiness नहीं है।

## 1. इस चरण का लक्ष्य

Build को ऐसा बनाना कि एक ही source revision पर JDK, Android SDK, Gradle dependencies, unit tests, lint और debug APK भरोसेमंद तरीके से बनें। Emulator की समस्या हो तो उसे सही infrastructure skip के रूप में दर्ज किया जाए; गलत emulator command को 3 मिनट तक चलाकर timeout न कराया जाए।

## 2. Exact-SHA CI परिणाम

### मुख्य Build / Test / Lint

[VVF Smart Manager CI & Quality Gate — Run 44664981314](https://github.com/awasthisach/The-Perfect/actions/runs/44664981314)

- Tested SHA: `8ea352753dda545105c6fc0411cb55e33b7bf792`
- Overall job: **SUCCESS**
- सफल चरण: JDK 17 setup, Android SDK setup, SQLCipher 4.5.4 fixture generation, ORT validator Python unit tests, Gradle wrapper verification, `testDebugUnitTest`, Room schema export, Android lint और `assembleDebug`।
- Artifacts: debug APK, unit test reports, lint reports, Room schema export और CPAS unit evidence upload हुए।
- Debug APK artifact का आकार: 51,184,437 bytes।
- इस job में अलग-अलग unit test cases की कुल संख्या इस रिपोर्ट में **सत्यापित नहीं**; केवल Gradle task success सत्यापित है।

### SQLCipher hard instrumentation gate

[Weekly Instrumented Hard Gate — Run 44664981370](https://github.com/awasthisach/The-Perfect/actions/runs/44664981370)

- Tested SHA: `8ea352753dda545105c6fc0411cb55e33b7bf792`
- Overall job: **SUCCESS**
- Emulator hardware acceleration के साथ boot हुआ।
- App: 4 tests, 0 failures, 0 errors, 0 skipped।
- core/database: 3 tests, 0 failures, 0 errors, 0 skipped।
- core/database-compat: 3 tests, 0 failures, 0 errors, 0 skipped।
- कुल: **10 tests, 0 failures, 0 errors, 0 skipped**।
- Exact-SHA report validator ने `status=PASS` और tested SHA की पुष्टि की।
- Artifact: [sqlcipher-instrumented report](https://github.com/awasthisach/The-Perfect/actions/runs/44664981370/artifacts/14597152852)。

## 3. मुख्य CI में मिला emulator defect

मुख्य CI run के emulator log में यह संदेश मिला:

`Software CPU emulation (-accel off) is not supported. A hardware CPU accelerator is required`

कारण: `.github/workflows/ci.yml` optional emulator start command में `-accel off` दे रहा था। इसी Android Emulator image पर यह विकल्प boot से पहले ही असफल हो रहा था। इस वजह से primary CI ने instrumentation को infrastructure skip घोषित किया। यह Kotlin/Gradle compilation failure नहीं था।

अलग Weekly Hard Gate की workflow पहले से `/dev/kvm` जाँचती है, KVM उपलब्ध होने पर permission ठीक करती है और `-accel on` से emulator चलाती है। उसी exact SHA पर यह hard gate सफल हुआ।

### लागू किया गया सीमित सुधार

`.github/workflows/ci.yml` में optional emulator startup अब:

1. `/dev/kvm` की मौजूदगी जाँचेगा।
2. KVM उपलब्ध न होने पर स्पष्ट warning के साथ optional emulator skip करेगा।
3. KVM उपलब्ध लेकिन inaccessible हो तो भी स्पष्ट skip दर्ज करेगा।
4. KVM उपलब्ध/accessible हो तो `-accel on` से emulator शुरू करेगा।
5. Unit tests, lint और debug APK build की hard requirements को नहीं बदलेगा।
6. Weekly Instrumented Hard Gate को नहीं बदलेगा; वह अलग hard gate ही रहेगा।

यह सुधार commit `43dc4a868f3353e11ecaa9722b447127626910ed` पर सत्यापित हुआ: मुख्य CI में KVM check सफल, emulator `emulator-5554` boot हुआ और SQLCipher instrumentation के दोनों batches सफल रहे।

## 4. Build configuration का निरीक्षण

| क्षेत्र | निरीक्षण | निर्णय |
|---|---|---|
| Gradle Wrapper | 9.3.1; wrapper verification step सफल | अभी version न बदलें |
| JDK | CI में Temurin JDK 17; सफल | अभी स्थिर रखें |
| Android Gradle Plugin | 9.1.1 | सफल build के कारण अभी न बदलें |
| Kotlin | 2.4.21 | अभी न बदलें |
| KSP | 2.3.14 | अभी न बदलें |
| Android SDK | compile/target 36, min 24 | build सफल; device compatibility अलग चरण |
| Gradle settings | parallel, build cache और configuration cache enabled | इस run में कोई blocking error नहीं |
| Modules | 21 Gradle modules | Phase 3 से पहले कोई module delete नहीं |
| Room schema | CI schema export step सफल | source schema/migration parity अलग Phase 5 review |
| Firebase config | Debug में missing config passthrough/IGNORE नीति मौजूद | CI build पास होने से Firebase runtime config प्रमाणित नहीं; अलग issue |
| Release signing | release task में signing vars और Firebase JSON अनिवार्य | release validation अलग चरण |

### Build log के non-blocking संकेत

- SQLCipher fixture configure के दौरान Tcl configuration definitions नहीं मिलीं; configure/build और fixture generation सफल हुए। SQLCipher का Tcl regression suite इस job में नहीं चला; Android SQLCipher instrumentation अलग hard workflow में सफल हुआ।
- Theme code में deprecated `statusBarColor` API warning दिखी; यह इस run में build blocker नहीं बनी। इसे UI/platform cleanup सूची में रखें, अभी build dependencies न बदलें।

## 5. अलग supply-chain workflow परिणाम

| Workflow | Run | स्थिति | अर्थ |
|---|---:|---|---|
| SBOM Alternative Trial | [44667847167](https://github.com/awasthisach/The-Perfect/actions/runs/44667847167) | **FAIL** | CycloneDX task Hilt configurations पढ़ते समय Gradle provider error; Build gate से अलग |
| Nightly ORT | [44667847521](https://github.com/awasthisach/The-Perfect/actions/runs/44667847521) | **PENDING** | कोई completed result नहीं |
| CPAS Production Gate | [44667847137](https://github.com/awasthisach/The-Perfect/actions/runs/44667847137) | **PENDING** | कोई completed result नहीं |
| Worker Validation | [44664981301](https://github.com/awasthisach/The-Perfect/actions/runs/44664981301) | **SUCCESS** | Worker tests/bundle; Android Build का विकल्प नहीं |

SBOM failure का exact संकेत `:app:hiltCollectClassesDebug`, `DebugAndroidTest`, `DebugUnitTest` और `Release` task properties (`classesJars`/`classesDirectories`) द्वारा `hiltCompileOnly*` configurations query करते समय आया। यह Android app build failure नहीं है। CycloneDX plugin docs के अनुसार `skipConfigs` full-match regex से उन configurations को छोड़ सकता है जो scan के लिए अभिप्रेत नहीं हैं; `hiltCompileOnly.*` को सीमित रूप से छोड़ने का patch अगले commit में जोड़ा गया है। उस patch का rerun अभी सत्यापित नहीं, इसलिए SBOM trial अभी FAIL ही माना जाएगा।

## 6. इस चरण में क्या नहीं किया गया

- कोई dependency delete/update नहीं की।
- Gradle/Kotlin/AGP versions नहीं बदले।
- Hilt migration नहीं छेड़ी।
- Room schema/migrations नहीं बदले।
- Firebase missing-config behavior नहीं बदला।
- ORT/CPAS policy या required artifacts नहीं बदले।
- `main` नहीं बदली; PR #225 Draft/Open/Unmerged है।
- कोई force push नहीं किया।

## 7. Phase 2 पूरा मानने के मानदंड

- [ ] इस workflow correction वाले नए exact SHA पर मुख्य CI सफल।
- [ ] Wrapper/JDK/SDK/dependency setup सफल।
- [ ] Unit tests सफल।
- [ ] Room schema export सफल और artifact उपलब्ध।
- [ ] Lint सफल।
- [ ] Debug APK सफल और artifact उपलब्ध।
- [ ] Optional emulator KVM उपलब्ध होने पर सही तरीके से boot हो; KVM न होने पर स्पष्ट soft skip हो।
- [ ] उसी exact SHA पर Weekly Instrumented Hard Gate सफल और report validator PASS।
- [ ] कोई नया build regression न हो।

ORT/SBOM/CPAS Phase 2 के build pass criteria नहीं हैं, लेकिन release gate बने रहेंगे। उनका अलग समाधान आवश्यक है।

## 8. अंतिम exact-SHA सत्यापन

[मुख्य CI — Run 44667847110](https://github.com/awasthisach/The-Perfect/actions/runs/44667847110)

- Tested SHA: `43dc4a868f3353e11ecaa9722b447127626910ed`; job **SUCCESS**।
- Unit tests, Room schema export/upload, Android lint, Debug APK, CPAS evidence generation/upload सफल।
- KVM check सफल; emulator boot हुआ; logs में `emulator-5554 device`, instrumentation के 2 batches शुरू/समाप्त, `BUILD SUCCESSFUL` और summary `Emulator booted: true`, `Instrumented outcome: success`।
- Artifacts: Debug APK 51,184,441 bytes; unit-test reports, Room schema export, lint reports, Android test reports और emulator diagnostics उपलब्ध।

[SQLCipher Instrumented Hard Gate — Run 44667847130](https://github.com/awasthisach/The-Perfect/actions/runs/44667847130)

- Tested SHA वही exact SHA; job **SUCCESS**।
- App 4 + core/database 3 + core/database-compat 3 = 10 tests; हर module में 0 failures और 0 errors।
- Exact-SHA report validator सफल; artifact `sqlcipher-instrumented-43dc4a868f3353e11ecaa9722b447127626910ed` अपलोड।

इसलिए Phase 2 के build criteria पूरे हैं। ORT, CPAS और SBOM release assurance अलग हैं और अभी Phase 2 completion में शामिल नहीं किए गए।

## 9. अगला कदम

SBOM trial में CycloneDX द्वारा Hilt के generated `hiltCompileOnly*` configurations query करने की विफलता मिली। एक narrowly scoped `skipConfigs = listOf("(?i)hiltCompileOnly.*")` correction जोड़ा जा रहा है; इससे बाकी resolvable project configurations और `failOnUnresolvedDependencies = true` बने रहेंगे। नए exact-SHA SBOM rerun के बिना इसे PASS नहीं माना जाएगा।
