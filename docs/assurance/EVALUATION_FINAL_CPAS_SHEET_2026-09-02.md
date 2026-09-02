# The-Perfect रेपो के लिए `FINAL_CPAS_SHEET.md` का read-only मूल्यांकन

**रिपोर्ट की तारीख:** 2 सितंबर 2026  
**विश्लेषण का दायरा:** केवल निरीक्षण और दस्तावेज़ी समीक्षा; रेपो में कोई कोड, कॉन्फ़िगरेशन या दस्तावेज़ बदला नहीं गया।  
**समीक्षित रेपो:** [awasthisach/The-Perfect](https://github.com/awasthisach/The-Perfect)  
**समीक्षित मुख्य फाइल:** [`docs/assurance/FINAL_CPAS_SHEET.md`](https://github.com/awasthisach/The-Perfect/blob/main/docs/assurance/FINAL_CPAS_SHEET.md)

## 1. कार्यकारी निष्कर्ष

`FINAL_CPAS_SHEET.md` इस रेपो के लिए **दिशा, सुरक्षा-मानसिकता और production-governance blueprint के रूप में अत्यंत उपयोगी** है, लेकिन वर्तमान स्थिति में इसे **production readiness का स्वतंत्र प्रमाण या authoritative computed status source नहीं माना जा सकता**। इसकी उपयोगिता का सही वर्णन यह है: यह बताती है कि VVF Smart Manager को किन सुरक्षा, डेटा-अखंडता, recovery, CI, supply-chain और release controls के विरुद्ध परखा जाना चाहिए; परंतु यह अभी हर control को वास्तविक implementation symbol, test identifier, CI job, artifact digest और evidence record से पर्याप्त रूप से नहीं जोड़ती।

रेपो स्वयं एक offline-first Android file manager, encrypted vault और privacy-focused productivity suite के रूप में संगठित है। इसमें Kotlin/Compose, modular Clean Architecture, Room/SQLCipher, Android Keystore, WorkManager, Google Drive integration और plugin modules मौजूद हैं; README में manual composition root और अधूरी Hilt migration भी स्पष्ट रूप से कही गई है [1]। इस प्रकार CPAS की security और recovery प्राथमिकताएँ ऐप के वास्तविक risk profile से अच्छी तरह मेल खाती हैं।

फिर भी, CPAS और रेपो के बीच सबसे बड़ा gap **policy-to-proof gap** है। CPAS 49 numbered sections, computed status, bidirectional traceability, immutable evidence और executable verifier की मांग करती है, जबकि actual assurance directory में केवल कुछ registry/matrix/risk files और खाली evidence ledger मौजूद हैं [2]। वर्तमान verifier मुख्यतः required-file presence, 1–49 headings, risk-register status और ledger-entry shape जाँचता है; वह sheet में लिखे अधिकांश security, test, coverage, privacy, accessibility, performance, restore और supply-chain दावों को verify नहीं करता [3]।

### समग्र आकलन

| आयाम | आकलन | निष्कर्ष |
|---|---:|---|
| ऐप के risk profile से alignment | उच्च | Vault, encrypted DB, filesystem boundary, cloud restore और background work पर सही जोर है। |
| Governance/operating discipline | उच्च | Evidence-first, fail-closed और regression-driven नियम उपयोगी हैं। |
| वर्तमान repo traceability | निम्न से मध्यम | Requirements, symbols, tests, CI और evidence की वास्तविक bidirectional mapping अनुपस्थित/अपूर्ण है। |
| Executable assurance | निम्न से मध्यम | `cpas_verify.py` मौजूद है, पर उसका coverage केवल कुछ structural gates तक सीमित है। |
| Evidence readiness | निम्न | `evidence-ledger.json` में `entries: []` है, इसलिए कोई claim evidence-backed नहीं है [4]। |
| Release-readiness decision support | मध्यम | BLOCKED निर्णय उचित दिशा में है, पर computed status को अधिक वास्तविक gates से जोड़ना होगा। |
| वर्तमान उपयोगिता | **उच्च blueprint, निम्न प्रमाण** | इसे canonical policy/specification रखें, लेकिन current truth के लिए machine-readable artifacts अनिवार्य करें। |

## 2. रेपो को समझने से प्राप्त वास्तविक संदर्भ

रेपो में `app`, नौ `core` modules, सात `feature` modules और तीन plugin modules सहित modular Android structure है। README के अनुसार minimum SDK 24 और compile/target SDK 36 हैं; persistence में Room + SQLCipher, security में Android Keystore और AES-GCM vault encryption, background execution में WorkManager, cloud में Google Drive और plugin architecture में OCR तथा semantic search शामिल हैं [1]। यह scope किसी साधारण build checklist से बड़ा है; इसमें confidentiality, authorization, integrity, recovery, platform compatibility और supply-chain सभी महत्वपूर्ण हैं।

रेपो में assurance-related सामग्री पहले से मौजूद है: `04-technology-registry.yaml`, `05-security-reliability-invariants.yaml`, `07-test-matrix.yaml`, `08-risk-register.yaml`, `evidence-ledger.json`, `README.md` और `tools/audit/cpas_verify.py` [2]। यह एक अच्छा प्रारंभिक ढाँचा है, लेकिन CPAS sheet में प्रस्तावित `00-constitution.md`, `01-scope.md`, `02-definitions.md`, `03-evidence-policy.md`, `06-requirements-traceability.yaml`, `09-recovery-assurance.yaml` और `10-remediation-policy.yaml` अभी actual directory inventory में नहीं दिखते। इसलिए sheet का repository-structure भाग वर्तमान repo की स्थिति नहीं, बल्कि target-state architecture है। इसे स्पष्ट रूप से `TARGET STATE` के रूप में चिह्नित करना चाहिए।

CI में JDK 17, Android SDK 35/36, unit tests, lint, debug assembly, SQLCipher compatibility fixture, एक Android instrumentation invocation, reports/artifacts upload और वैकल्पिक FOSSA license scan दिखाई देते हैं [5]। यह उपयोगी आधार है, लेकिन CPAS में वर्णित complete security, cloud-restore, process-death, mutation, fuzz, accessibility, localization, performance और reproducible-build gates workflow में स्पष्ट रूप से wired नहीं दिखते। विशेष रूप से FOSSA scan secret उपलब्ध होने पर ही चलता है; इसलिए उसे unconditional production gate मानना सही नहीं होगा।

एक महत्वपूर्ण consistency point यह है कि sheet architecture में **FTS4** लिखा है, जबकि CI का SQLCipher compatibility fixture `--enable-fts5` से बनता है [5]। यह अपने-आप में bug का प्रमाण नहीं है, पर documentation, actual Room schema, native fixture और compatibility requirement को एक ही authoritative contract में reconcile करना आवश्यक है। CPAS में इस तरह के version/feature claims के साथ exact source, schema version और verification command होना चाहिए।

## 3. `FINAL_CPAS_SHEET.md` की उपयोगिता

Sheet की सबसे बड़ी ताकत यह है कि वह production readiness को केवल green build नहीं मानती। `Observe → Root Cause → Minimal Fix → Regression Test → CI → Evidence → CPAS Verification` loop, fail-closed security boundary, false-green detection, computed production status और evidence ledger जैसे विचार इस रेपो के security-sensitive स्वरूप के अनुरूप हैं। OWASP MASVS मोबाइल ऐप के storage, cryptography, authentication, network, platform, code, resilience और privacy domains को अलग-अलग verification areas में रखता है [6]; CPAS में इनका पर्याप्त बड़ा हिस्सा पहले से conceptual रूप में मौजूद है।

Storage boundary, Android Keystore testability, SQLCipher compatibility, cloud restore atomicity, lifecycle/process death, retry idempotency और permission behavior जैसे विषय वास्तविक Android failure modes हैं। NIST SSDF भी secure development practices को SDLC में integrate करने, vulnerabilities के root causes को address करने और recurrence कम करने पर जोर देता है [7]। इस दृष्टि से CPAS का remediation और evidence-oriented ढाँचा सही दिशा में है।

Supply-chain भाग भी सही है, पर उसे maturity target में बदलना चाहिए। SLSA provenance का उद्देश्य artifact को उसके source और build process से trace करना है तथा signed provenance और hosted/hardened build levels अलग-अलग assurance देते हैं [8]। इसलिए “SLSA” या “reproducible build” लिखना पर्याप्त नहीं; repo को यह बताना होगा कि वह कौन-सा target level अपना रहा है, कौन-सा evidence स्वीकार होगा और verifier क्या check करेगा।

## 4. मुख्य कमियाँ और जोखिम

### 4.1 Sheet target-state और current-state को अलग नहीं करती

एक ही दस्तावेज़ में architecture description, historical findings, mandatory policy, future target, examples और current production status मिले हुए हैं। इससे पाठक यह नहीं जान पाता कि कौन-सी बात आज रेपो में सत्यापित है, कौन-सी planned है और कौन-सी केवल desired control है। उदाहरण के लिए sheet में 37 critical invariants, 18 required gates और coverage figures का sample computed block है, लेकिन यह स्पष्ट नहीं है कि ये live current counts हैं या उदाहरण। इन्हें `EXAMPLE`, `TARGET`, `OBSERVED` और `VERIFIED` जैसे स्पष्ट status labels के बिना रखना false confidence पैदा कर सकता है।

### 4.2 Verifier sheet के दावों का वास्तविक semantic verification नहीं करता

`cpas_verify.py` 49 headings की sequence, required files, open critical/high risks और evidence-ledger entry format जाँचता है। वह registry के invariants को test reports से, tests को CI jobs से, evidence को commit/workflow/artifact digest से, coverage floors को generated coverage से, या restore chain को actual test execution से नहीं जोड़ता [3]। इसलिए “executable assurance system” का दावा वर्तमान implementation से बड़ा है। Sheet में verifier contract का exact input schema, output schema, failure taxonomy और self-test suite जोड़ना आवश्यक है।

### 4.3 Evidence ledger खाली है

`evidence-ledger.json` का `entries` array खाली है [4]। यह उचित रूप से production PASS को रोकता है, लेकिन sheet को यह बताना चाहिए कि पहली evidence entry का न्यूनतम schema क्या होगा: control/invariant ID, requirement ID, test ID, source commit, workflow run ID, artifact path और SHA-256 digest, execution timestamp, environment, result, reviewer/owner और expiry। Evidence के बिना “verified”, “resolved” या coverage-related historical claim को current fact की तरह नहीं लिखा जाना चाहिए।

### 4.4 Risk register और CPAS identifiers में पर्याप्त contract नहीं है

Current risk register में `PROD-001`, `PROD-002`, `PROD-003` और `PROD-007` जैसे findings हैं, लेकिन sheet के examples में invariant IDs `INV-STORAGE-001` आदि अलग naming family में हैं, जबकि actual invariant file में `STORAGE-INV-001` जैसे IDs हैं [2]। यह naming mismatch traceability और automation को कमजोर करेगा। एक canonical ID grammar, alias policy और deprecation rule चाहिए।

### 4.5 Coverage floors context के बिना misleading हैं

Aggregate, security-critical, repository/data, vault और cloud sync के प्रतिशत और floors उपयोगी warning हैं, लेकिन denominator, tool, report path, branch/commit, generated-vs-executed test distinction और calculation formula नहीं दी गई है। Line coverage अकेले security assurance नहीं है; CPAS का invariant-centric दृष्टिकोण सही है, पर invariant coverage का गणित स्पष्ट होना चाहिए। कोई floor module risk, code size या test level के बिना universal gate नहीं बनना चाहिए।

### 4.6 “Where practical” और broad mandatory wording enforceable नहीं हैं

Reproducible build, mutation testing, fuzz testing, performance/battery और API matrix जैसे controls के लिए “जहाँ practical हो” या बहुत व्यापक सूची पर्याप्त नहीं है। हर gate में scope, owner, environment, command, pass/fail threshold, evidence artifact और exception process होना चाहिए। अन्यथा policy aspirational रहेगी और verifier उसे enforce नहीं कर सकेगा।

## 5. इस फाइल में क्या जोड़ा जाना चाहिए

### 5.1 Document control और status semantics

सबसे पहले एक स्पष्ट document-control block होना चाहिए: `schema_version`, `document_version`, `status` (`CURRENT`, `TARGET`, `DRAFT`, `SUPERSEDED`), authoritative branch/tag, source commit, owner, approver, review cadence, last verified timestamp और next review date। साथ ही प्रत्येक section और प्रत्येक claim के लिए `normative`, `informative`, `example` या `historical` classification हो। इससे current state और target state अलग रहेंगे।

### 5.2 Application scope और asset inventory

Sheet में VVF Smart Manager के supported use cases, out-of-scope features, supported API levels, release variants, cloud providers, plugin trust model और user/data classes का छोटा लेकिन निश्चित scope होना चाहिए। Assets में vault contents, database, encryption keys, PIN/decoy state, filesystem handles, OAuth tokens, backup objects, logs, notifications और release signing material शामिल किए जाएँ। हर asset के लिए confidentiality, integrity, availability और recovery priority दी जाए।

### 5.3 Canonical control schema

हर control को एक machine-readable record में बदला जाना चाहिए। न्यूनतम fields इस प्रकार होने चाहिए: `control_id`, `title`, `domain`, `severity`, `statement`, `implementation_refs`, `test_refs`, `ci_job_refs`, `evidence_requirements`, `pass_condition`, `owner`, `status`, `last_verified_commit`, `expires_at` और `exception_ref`। यही record verifier का मुख्य input बने, न कि Markdown prose।

### 5.4 Requirements-to-code-to-test-to-evidence matrix

Sheet में matrix या linked artifact का schema स्पष्ट रूप से जोड़ना चाहिए। प्रत्येक row में requirement, implementation symbol/file, invariant/control, positive test, negative test, instrumentation/integration test, workflow job, evidence artifact, current status और residual risk हो। “Bidirectional traceability” कहने के बजाय orphan detection भी परिभाषित करें: requirement बिना test, test बिना requirement, evidence बिना commit, और closed risk बिना evidence सभी अलग failures हों।

### 5.5 Gate catalog और exact acceptance criteria

CI pipeline की सूची पर्याप्त नहीं है। प्रत्येक gate के लिए trigger, required command, environment, timeout, expected report, threshold और blocking behavior लिखना चाहिए। उदाहरणतः cloud restore gate में corrupt payload, interrupted download, wrong authentication, failed decrypt, schema mismatch, atomic swap interruption, rollback और post-restore hash/integrity checks अलग-अलग acceptance cases हों। Android Keystore में JVM fake और real instrumented provider के बीच trust boundary तथा fake को production artifact में शामिल न होने की जांच हो।

### 5.6 Evidence schema और freshness rules

Evidence ledger के लिए JSON Schema या YAML Schema commit करना चाहिए। Evidence record में source commit SHA, workflow run URL/ID, job/step, test case, report/artifact URL, artifact digest, environment/API level/JDK, start/end time, result, failure summary, reviewer और expiration शामिल हों। “Immutable” का अर्थ भी स्पष्ट हो: Git commit, retained CI artifact, signed attestation या external append-only store में से कौन-सा। Stale evidence की अधिकतम आयु control domain के अनुसार तय करें।

### 5.7 Exception, waiver और residual-risk process

Production assurance में हर requirement हमेशा लागू नहीं होगी। इसलिए exception ID, business/technical rationale, compensating control, approver, expiry, affected releases और residual risk अनिवार्य fields होने चाहिए। कोई waiver critical security, wrong-key rejection, unauthorized restore या data-loss control को silently bypass न कर सके। Expired waiver को automatic BLOCKED status देना चाहिए।

### 5.8 Real release and artifact assurance

Release gate में version name/code, variant, signing certificate fingerprint, APK/AAB SHA-256, mapping/proguard file, dependency lock state, SBOM, provenance/attestation, Play/side-load distribution channel, rollback artifact और release notes का संबंध जोड़ना चाहिए। CI में artifact upload होना और artifact identity verify होना अलग controls हैं।

### 5.9 Privacy और mobile-specific controls

Privacy data map को Android backup exclusion, screenshots/recents, clipboard, notifications, autofill, exported components, intents/deep links, WebView/ML/plugin inputs, OAuth token storage, crash/log telemetry और user deletion/retention तक विस्तारित करना चाहिए। OWASP MASVS की privacy और platform categories को control IDs में map करना चाहिए, केवल standards की सूची के रूप में नहीं [6]।

### 5.10 Performance, accessibility और localization के measurable budgets

इन sections में thresholds चाहिए: cold/warm startup, database operation latency, encryption throughput, sync retry ceiling, memory limit, battery/background budget, frame/jank target, minimum touch target, contrast rule, screen-reader assertions, string coverage, plural/RTL tests और text-expansion cases। “Test होगा” के बजाय “किस condition पर gate fail होगा” लिखा जाए।

### 5.11 Threat model और abuse-case catalog

Threat model में attacker capability, trust boundary diagram, abuse case, precondition, impact, mitigation, detection, recovery और residual risk fields होने चाहिए। विशेष रूप से decoy vault separation, malicious document/plugin, replayed cloud object, token theft, rooted/emulated device, symlink race, content URI confusion, backup leakage और process death के abuse cases जोड़ें।

### 5.12 Verifier self-assurance

CPAS verifier के लिए स्वयं test suite चाहिए: malformed YAML/JSON, duplicate IDs, missing references, stale commit, forged PASS, skipped test, empty evidence, expired waiver, artifact digest mismatch, status contradiction और unknown severity पर expected BLOCKED output। Verifier को deterministic exit codes, stable JSON schema, versioned input contract और no-network/local verification mode देना चाहिए।

## 6. इस फाइल में क्या नहीं होना चाहिए

| नहीं होना चाहिए | कारण | बेहतर स्थान/रूप |
|---|---|---|
| बिना source, commit या report के historical coverage प्रतिशत | यह stale या unverifiable claim बन जाता है। | Evidence ledger या dated audit report; sheet में केवल formula और reference रखें। |
| `production_status` के manually typed live counts | Computed status के सिद्धांत से विरोधाभास है। | Generated `cpas-status.json` या signed CI artifact। |
| Future target-state file tree को current repository structure की तरह लिखना | पाठक actual और planned files में भ्रमित होगा। | `CURRENT STATE` और `TARGET STATE` अलग sections। |
| Architecture की लंबी पुनरावृत्ति | CPAS का उद्देश्य assurance contract है, architecture encyclopedia नहीं। | `docs/ARCHITECTURE.md` को canonical reference दें। |
| एक ही control की कई naming schemes | Traceability और verifier matching टूटता है। | एक canonical ID grammar और alias/deprecation table। |
| “जहाँ practical हो”, “complete”, “secure”, “production-ready” जैसे unqualified शब्द | इनके pass/fail अर्थ नहीं हैं। | Measurable acceptance criteria, owner और evidence। |
| Agent/work-cycle reporting cadence जैसे generic operating instructions | यह repository control नहीं, operator workflow है; sheet को अनावश्यक रूप से process-heavy बनाता है। | Contributor/automation runbook में रखें। |
| GitHub push, merge, PR close या branch deletion को हर remediation का अनिवार्य step | सभी fixes में लागू नहीं; release governance और code remediation को मिला देता है। | Separate PR/release policy, जहाँ applicable हो। |
| Unverified claims कि किसी खास library/version का behavior निश्चित है | Version-specific contracts बदल सकते हैं और external documentation authoritative हो सकती है। | Pinned version, source URL, test fixture और evidence। |
| Security standards की केवल नाम-सूची | Compliance का आभास देती है, पर control mapping नहीं। | MASVS/MASTG/NIST/SLSA ID-to-control mapping। |
| Secrets, PINs, tokens, real paths या sensitive payload examples | Assurance document स्वयं leakage surface बन सकता है। | Sanitized fixtures और secret-scanning rule। |
| Broad API-level lists बिना device/emulator matrix और rationale | Test coverage का भ्रम पैदा होगा। | Supported API/device matrix, exclusions और observed results। |
| FTS4/FTS5, SQLCipher version, Hilt/KSP या SDK जैसे facts बिना single source of truth | Build और docs के बीच drift होगा। | Technology registry को authoritative बनाकर sheet में केवल link/reference। |

## 7. अनुशंसित पुनर्संरचना

`FINAL_CPAS_SHEET.md` को canonical **policy and index** बनाया जाए, न कि सभी live numbers और हर implementation detail का container। इसके ऊपर एक छोटी “How to read this CPAS” भूमिका हो; उसके बाद scope, status semantics, risk model और gate definitions आएँ। Live computed result, evidence entries, technology pins, invariants, traceability और test matrix को versioned machine-readable files में रखा जाए। Markdown sheet केवल उनके links, interpretation और governance rules दे।

एक उपयोगी target structure यह होगी: `00-constitution.md` में principles; `01-scope.md` में product/release scope; `02-definitions.md` में status और evidence vocabulary; `03-evidence-policy.md` में schema/freshness/retention; `04-technology-registry.yaml` में versions; `05-security-reliability-invariants.yaml` में canonical controls; `06-requirements-traceability.yaml` में graph; `07-test-matrix.yaml` में executable cases; `08-risk-register.yaml` में findings; `09-recovery-assurance.yaml` में restore state machine; `10-remediation-policy.md` में ownership/waivers; और `FINAL_CPAS_SHEET.md` में index, gate semantics तथा current generated status reference। यह structure तभी प्रभावी होगा जब CI verifier इन artifacts को वास्तव में parse और cross-check करे।

## 8. प्राथमिकता क्रम

| प्राथमिकता | करना चाहिए | अपेक्षित परिणाम |
|---:|---|---|
| P0 | Current-vs-target labeling, canonical IDs, evidence schema और verifier contract तय करना | False-green और documentation drift घटेगा। |
| P0 | Empty ledger को वास्तविक commit/workflow/test/artifact evidence से भरने की प्रक्रिया बनाना | Claims reproducible बनेंगे; current PASS/BLOCKED विश्वसनीय होगा। |
| P0 | Verifier को traceability, stale evidence, duplicate IDs, unknown references और actual gate outputs पर चलाना | Executable assurance का दावा वास्तविक बनेगा। |
| P1 | SQLCipher FTS4/FTS5 और सभी toolchain/version pins reconcile करना | Build contract ambiguity समाप्त होगी। |
| P1 | Cloud restore, Android Keystore, storage boundary और WorkManager के positive/negative/instrumented tests को gate IDs से जोड़ना | सबसे महत्वपूर्ण data-loss/security risks cover होंगे। |
| P1 | Release artifact digest, signing fingerprint, SBOM और provenance verification जोड़ना | Release identity और supply-chain trust सुधरेगा। |
| P2 | Privacy/platform, accessibility/localization और performance thresholds को measurable बनाना | Quality claims executable और repeatable होंगे। |
| P2 | Verifier self-tests, waiver expiry और periodic review automation | Assurance system स्वयं audit योग्य बनेगा। |

## 9. अंतिम निर्णय

**निर्णय: `FINAL_CPAS_SHEET.md` रखें, लेकिन इसे “canonical assurance blueprint/index” के रूप में पुनर्परिभाषित करें; इसे अकेले current production verdict न मानें।** इसकी दिशा रेपो के लिए सही और मूल्यवान है, खासकर encrypted vault, SQLCipher, filesystem authorization, cloud restore और background reliability के कारण। वर्तमान में इसका सबसे बड़ा योगदान remediation priorities और governance discipline है, जबकि सबसे बड़ा दोष यह है कि prose commitments का पर्याप्त भाग executable evidence और actual CI outputs से जुड़ा नहीं है।

अभी उपलब्ध निरीक्षण के आधार पर production status को **BLOCKED / not independently verified** रखना उचित है। कारण केवल खुले risk-register findings नहीं हैं; evidence ledger खाली है, verifier semantic controls को सीमित रूप से जांचता है, कई target-state files अनुपस्थित हैं, और CPAS के कुछ examples current computed data से अलग नहीं किए गए हैं [1] [2] [3] [4]। इसलिए इस sheet का अगला सुधार “और अधिक requirements जोड़ना” नहीं, बल्कि **कम ambiguity, स्पष्ट ownership, canonical IDs, measurable gates और verifiable evidence** बनाना होना चाहिए।

## References

[1]: https://github.com/awasthisach/The-Perfect/blob/main/README.md "The-Perfect README — VVF Smart Manager technology stack and current status"
[2]: https://github.com/awasthisach/The-Perfect/tree/main/docs/assurance "The-Perfect assurance directory"
[3]: https://github.com/awasthisach/The-Perfect/blob/main/tools/audit/cpas_verify.py "The-Perfect executable CPAS verifier"
[4]: https://github.com/awasthisach/The-Perfect/blob/main/docs/assurance/evidence-ledger.json "The-Perfect evidence ledger"
[5]: https://github.com/awasthisach/The-Perfect/blob/main/.github/workflows/ci.yml "The-Perfect CI workflow"
[6]: https://mas.owasp.org/MASVS/ "OWASP Mobile Application Security Verification Standard"
[7]: https://csrc.nist.gov/pubs/sp/800/218/final "NIST SP 800-218 — Secure Software Development Framework (SSDF) Version 1.1"
[8]: https://slsa.dev/spec/v1.0/levels "SLSA Build Security Levels"
