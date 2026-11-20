# Phase 3 — Dependency और Supply-chain Review

**स्थिति: IN PROGRESS — SBOM generation सफल; runtime exposure और license parity का अलग exact-SHA प्रमाण अभी बनना बाकी है।**  
**Repository:** `awasthisach/The-Perfect`  
**Branch:** `audit/fixes-2026-11`  
**Evidence SHA:** `9d3f0914cff72e1a83a9a40395ef924250b2df3e`  
**तारीख:** 20 नवंबर 2026  
**रिलीज़ निर्णय:** **NO-SHIP**

## 1. SBOM generation का सत्यापित परिणाम

[SBOM Trial push run 44675907899](https://github.com/awasthisach/The-Perfect/actions/runs/44675907899) और [PR run 44675911810](https://github.com/awasthisach/The-Perfect/actions/runs/44675911810) दोनों exact SHA `9d3f0914cff72e1a83a9a40395ef924250b2df3e` पर **SUCCESS** हुए।

Artifact: [sbom-alternative-trial-9d3f0914…](https://github.com/awasthisach/The-Perfect/actions/runs/44675907899/artifacts/14602655106)

Downloaded artifact में सात files थीं: CycloneDX JSON/XML, SPDX JSON, OSV CycloneDX/SPDX reports, SBOM validation और OSV status।

| जाँच | परिणाम |
|---|---:|
| CycloneDX components | 771 |
| SPDX packages | 771 |
| Package URL parity | PASS |
| CycloneDX license metadata वाले components | 746 |
| SPDX declared license वाले packages | 701 |
| OSV report में vulnerability वाले packages | 21 |
| Package-level vulnerability entries | 94 |
| Unique advisory IDs | 51 |
| Unique severity counts | Critical 3, High 19, Moderate 26, Low 3 |

ये 94 package-level entries हैं; 51 unique advisories हैं। इन्हें 94 अलग advisories कहना गलत होगा।

## 2. OSV निष्कर्षों की सीमा

OSV-Scanner v2.7.1 ने CycloneDX और SPDX दोनों पर exit code 1 लौटाया। दोनों JSON reports valid हैं; exit code findings के कारण non-zero है। Workflow ने इसे compliance PASS में नहीं बदला।

Dependency graph में vulnerable versions के कुछ उदाहरण:
- Bouncy Castle `1.79` — `com.android.tools:sdk-common` के माध्यम से Android test platform chain।
- Netty `4.1.93.Final` और `4.1.110.Final` — Android test platform / emulator-control chain।
- Guava `28.1-android` — Compose UI test / Truth chain; `33.6.0-jre` — Hilt compiler chain।
- Apache HttpClient `4.5.6` और Commons Lang `3.16.0` — Android SDK tooling chain।

ये paths test/build tooling की ओर संकेत करते हैं, लेकिन मौजूदा all-configurations SBOM से यह अंतिम रूप से सिद्ध नहीं होता कि कोई vulnerable package release runtime में नहीं है। इसलिए runtime exposure **सत्यापित नहीं** माना जाएगा, जब तक release-only SBOM न बन जाए।

OSV की `license_summary` में सभी 771 packages `UNKNOWN` दिखे, जबकि CycloneDX में 746 components के पास license metadata है। इस run में OSV का license summary CycloneDX license evidence का विकल्प नहीं है।

## 3. SPDX conversion में license metadata loss

Package URL parity सफल होने का अर्थ license parity सफल होना नहीं है।

CycloneDX से SPDX conversion के बाद:
- CycloneDX: 746 components पर license metadata।
- SPDX: 701 packages पर `licenseDeclared`।
- 25 internal Gradle project components के अतिरिक्त **45 external components** में CycloneDX का license metadata SPDX `licenseDeclared` में नहीं आया।

इनमें Google Play Services / Firebase / ML Kit के SDK Terms, Android SDK License, JNA के LGPL/Apache विकल्प, Bouncy Castle Licence, KXML BSD-style/Public Domain और FastInfoset के Apache/BSD विकल्प शामिल हैं।

निष्कर्ष: मौजूदा conversion को license compliance के लिए पर्याप्त नहीं माना जा सकता। SPDX license expressions/LicenseRef को source metadata से सावधानीपूर्वक preserve करने और validator में license parity जोड़ने की आवश्यकता है। अनुमान से license IDs नहीं भरे जाएँगे।

## 4. SQLCipher license finding

SBOM में `net.zetetic:sqlcipher-android:4.20.0` का license metadata खाली है। Maven Central POM में license URL है, पर CycloneDX component में resolved license नहीं आया।

Zetetic के आधिकारिक दस्तावेज़ के अनुसार Maven Central से लिया गया SQLCipher Android package Community Edition है और Community Edition BSD-style license के अंतर्गत है। इसके साथ attribution और license grants को app UI/materials में पुन: प्रस्तुत करना आवश्यक है। [Zetetic SQLCipher License](https://www.zetetic.net/sqlcipher/license/) और [Community Edition Integration](https://www.zetetic.net/sqlcipher/sqlcipher-for-android-community/)।

Action:
- ऐप में user-accessible About / Open-source licenses स्थान सत्यापित करें।
- SQLCipher का सही copyright/license notice और आवश्यक attribution जोड़ें।
- packaged release artifact के भीतर notice मौजूद है, इसका test/evidence जोड़ें।
- Commercial/Enterprise package को Community Edition समझकर न मिलाएँ।

## 5. लागू workflow सुधार

एक ही all-configurations SBOM से runtime और test/build risk को अलग करना संभव नहीं है। अगला workflow revision:
1. Full inventory SBOM को सुरक्षित रखेगा।
2. `testConfigs = listOf("(?i).*test.*")` से Android `androidTest` और `debugUnitTest` जैसे configuration names को test metadata के रूप में चिह्नित करेगा।
3. अलग release-runtime SBOM केवल `releaseRuntimeClasspath` configurations से बनाएगा।
4. दोनों SBOMs के SPDX package URL parity को जाँचेगा।
5. OSV scan full inventory और release runtime दोनों पर अलग-अलग चलाएगा।
6. Non-zero scanner results को evidence के रूप में रखेगा; compliance PASS नहीं बनाएगा।

CycloneDX Gradle plugin documentation स्पष्ट करती है कि default रूप से सभी resolvable configurations scan होते हैं; `includeConfigs` और `skipConfigs` selection करते हैं, जबकि `testConfigs` केवल test label लगाता है। [Official CycloneDX Gradle plugin configuration](https://github.com/CycloneDX/cyclonedx-gradle-plugin#select-configurations)।

इस workflow revision को नए commit पर GitHub Actions rerun के बिना verified नहीं माना जाएगा।

## 6. अभी क्या नहीं बदला

- App dependencies/version catalog नहीं बदले।
- कोई package या source module delete नहीं किया।
- ORT/CPAS policy या required evidence हटाया नहीं।
- Release runtime risk को false-negative घोषित नहीं किया।
- `main` नहीं बदली; PR #225 Draft/Open/Unmerged रखना है।
- कोई force push नहीं।

## 7. आगे के gates

- [ ] Full inventory और release-runtime SBOM दोनों सफलतापूर्वक बनें।
- [ ] Release-runtime SBOM में expected modules/configurations उपस्थित हों।
- [ ] Vulnerable packages का runtime/test/build classification exact graph से सिद्ध हो।
- [ ] SPDX license metadata loss ठीक हो या SPDX को incomplete घोषित कर CycloneDX authoritative रखा जाए।
- [ ] SQLCipher BSD-style attribution app में जोड़ी और test की जाए।
- [ ] ORT successful run और तीन required artifacts सत्यापित हों।
- [ ] CPAS exact-SHA gate PASS हो।
- [ ] सभी hard gates के बाद ही release status पर पुनर्विचार हो।
