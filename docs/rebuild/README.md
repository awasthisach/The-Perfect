# VVF Smart Manager — सुरक्षित Rebuild योजना

**वर्तमान चरण: 3 — Dependency और Supply-chain सफाई (IN PROGRESS)**
**रिलीज़ स्थिति: NO-SHIP** — यह source map build, runtime, security या release readiness प्रमाणित नहीं करता।
**कार्य-शाखा:** `audit/fixes-2026-11`
**Phase 1 निरीक्षण SHA:** `fd6a35ca49622217c876bdb3758aeafab978adbf`
**तारीख:** 20 नवंबर 2026

## उद्देश्य और सीमाएँ

यह पुनर्निर्माण केवल `awasthisach/The-Perfect` के लिए है। `main` नहीं बदलेगा; PR #225 को Draft + Open + Unmerged रखना है; force-push, इतिहास मिटाना और evidence हटाना निषिद्ध है। दस्तावेज़ source के प्रमाणित तथ्यों और सत्यापित न हुए प्रश्नों को अलग रखते हैं।

चरण 1 में repository का module/dependency map, Android entry/startup, Room/SQLCipher, Drive, OCR, keyword/semantic search, background work और CI/supply-chain workflows दर्ज किए गए। इस चरण में कोई build, test, lint, workflow, production deploy, deletion या source-code rewrite नहीं किया गया।

## अनिवार्य दस्तावेज़

| दस्तावेज़ | स्थिति | उपयोग |
|---|---|---|
| [BASELINE.md](BASELINE.md) | पूरा — चरण 0 | SHA, PR/workflow स्थिति और सुरक्षित evidence snapshot; स्थिति capture समय की है। |
| [ARCHITECTURE.md](ARCHITECTURE.md) | पूरा — चरण 1 | modules, Gradle edges, app entry, runtime construction और subsystem boundaries। |
| [DATA-FLOW.md](DATA-FLOW.md) | पूरा — चरण 1 | local data, OAuth/Drive, OCR/search, remote boundary और privacy review points। |
| [KEEP-REWRITE-REMOVE.md](KEEP-REWRITE-REMOVE.md) | प्रारंभिक source-based triage | क्या सुरक्षित रखना है, क्या आगे जाँचना/बदलना है; कोई code action नहीं। |
| [DELETION-LOG.md](DELETION-LOG.md) | शुरू — शून्य deletions | भविष्य के हर deletion का कारण और test/evidence। |
| [TEST-MATRIX.md](TEST-MATRIX.md) | शुरू — कोई test नहीं चलाया | मौजूद test evidence और अभी बाकी validation। |
| [RISK-REGISTER.md](RISK-REGISTER.md) | प्रारंभिक | source-based जोखिम/अज्ञात; release निर्णय नहीं। |
| [RELEASE-CHECKLIST.md](RELEASE-CHECKLIST.md) | शुरू — NO-SHIP | P0/P1/P2/P3 gates की स्थिति। |
| [DAILY-REPORT.md](DAILY-REPORT.md) | अद्यतन | इस phase का SHA, बदली docs और validation boundary। |

## चरण क्रम

1. **चरण 0 — Baseline:** पूरा। विवरण [BASELINE.md](BASELINE.md) में है।
2. **चरण 1 — Project Map:** पूरा। स्रोत-आधारित map ऊपर के architecture/data-flow दस्तावेज़ों में है।
3. **चरण 2 — Build स्थिर करना:** पूरा; exact SHA `9d3f0914cff72e1a83a9a40395ef924250b2df3e` पर Main CI, SQLCipher Hard Gate और Worker Validation PASS। विवरण [PHASE-2-BUILD-STABILITY.md](PHASE-2-BUILD-STABILITY.md) में।
4. **चरण 3 — Dependency और Supply-chain सफाई:** जारी; SBOM generation और SPDX package parity पास, लेकिन vulnerability/license interpretation में gaps हैं। विवरण [PHASE-3-SUPPLY-CHAIN-REVIEW.md](PHASE-3-SUPPLY-CHAIN-REVIEW.md) में।
5. इसके बाद DI, database/encryption, OAuth/Drive, OCR, local semantic search, privacy, ORT/CPAS और device/release acceptance।

## Phase 3 — Supply-chain review

[PHASE-3-SUPPLY-CHAIN-REVIEW.md](PHASE-3-SUPPLY-CHAIN-REVIEW.md) में SBOM/OSV artifact की जाँच, SPDX license metadata loss, SQLCipher Community Edition attribution और runtime-vs-test dependency boundary दर्ज है। नया workflow full inventory और release runtime inventory अलग-अलग बनाएगा; rerun अभी pending है।

## मूल गोपनीयता नियम

दस्तावेज़ का पूरा पाठ, OCR पाठ, chunks, निजी fingerprints या embedding vectors अपने-आप किसी बाहरी AI server पर नहीं भेजे जाएँगे। कोई online AI सुविधा जोड़ने से पहले अलग विकल्प, स्पष्ट सहमति, data minimization और privacy review अनिवार्य है।

## रिपोर्टिंग नियम

हर आगे के परिवर्तन में तारीख, branch, पुराना SHA, नया SHA, बदली हुई फाइलें, कारण, चलाए गए tests, run link/ID, परिणाम और बचा हुआ जोखिम दर्ज होगा। तथ्य उपलब्ध न हों तो उन्हें **“सत्यापित नहीं”** कहा जाएगा।
