# FINAL CPAS SHEET
## The-Perfect — Continuous Production Assurance System (CPAS)

**Repository:** https://github.com/awasthisach/The-Perfect  
**Project:** VVF Smart Manager  
**Document Date:** 2026-09-02  
**Purpose:** Production-grade remediation, verification, CI governance and evidence assurance

---

# 1. Executive Objective

The-Perfect को केवल ऐसा Android application नहीं बनाना है जिसका build सफल हो जाए।

लक्ष्य है:

> **Evidence-driven, security-conscious, reproducible और production-assurable application.**

CPAS को केवल audit checklist नहीं, बल्कि repository के अंदर executable assurance system के रूप में लागू किया जाएगा।

Production readiness का निर्णय documentation या manual declaration से नहीं, बल्कि:

**Code + Tests + CI + Evidence + Computed Gates**

से निकलेगा।

---

# 2. Operating Constitution

1. सबसे पहले live repository और live CI की जाँच होगी।
2. बिना evidence के कोई technical claim नहीं किया जाएगा।
3. अनुमान को evidence नहीं माना जाएगा।
4. Runtime behavior के बारे में static inference हो तो उसे `STATIC-INFERENCE` के रूप में चिन्हित किया जाएगा।
5. हर वास्तविक failure का workflow: Observe → Research → Root Cause → Minimal Fix → Regression Test → Local Validation → GitHub Push → Live CI → Evidence Capture → CPAS Verification.
6. Security, cryptography, storage, database और data-loss boundaries में fail-open behavior स्वीकार नहीं होगा।
7. Requested blocker वास्तव में समाप्त और CI से verified होने तक उसे resolved नहीं माना जाएगा।
8. Obsolete/unresolved artifact केवल evidence के आधार पर हटाया या बंद किया जाएगा।
9. हर 3 या 5 वास्तविक और verified fixes के बाद consolidated report दी जाएगी।
10. हर तीसरे/पाँचवें work cycle में operating rules का self-check होगा।
11. Green build को अकेले production readiness का प्रमाण नहीं माना जाएगा।
12. Cached, skipped या ignored tests को executed evidence नहीं माना जाएगा।

---

# 3. Technical Architecture

- Kotlin, Coroutines/Flow
- Jetpack Compose, Material 3
- MVVM
- Modular Clean Architecture
- `app/`, `core/`, `feature/`, `plugins/`
- Manual Composition Root → Hilt migration
- Room + SQLCipher + FTS4
- Android Keystore, AES-GCM, PBKDF2
- Dual-PIN: Real / Decoy
- WorkManager
- Google Drive / Cloud synchronization
- OCR / Semantic / Cloud plugin contracts
- Gradle Kotlin DSL और Version Catalog

Exact toolchain versions हमेशा live repository से verify की जाएँगी।

---

# 4. Known Blocker Landscape

| क्षेत्र | Observed समस्या | Required action |
|---|---|---|
| SQLCipher CI | SQLCipher 4.5.4 पर newer 4.7+ build contract/paths लागू करने से mismatch; `src/sqlcipher.c` उपलब्ध नहीं था। | 4.5.4 के वास्तविक configure/make contract के अनुसार build। |
| AndroidKeyStore tests | Robolectric/JVM environment में AndroidKeyStore उपलब्ध न होने से CryptoSecurityManager initialization fail। | Injectable provider; JVM fake; instrumented real Keystore tests। |
| Storage boundary | Empty `allowedRoots` access को allow कर सकता है। | Root discovery failure पर deny; traversal/symlink tests। |
| Sample/demo data | Production listing empty directory पर sample files बना सकती है। | Production side-effect हटाएँ; test/debug fixtures अलग करें। |
| Coverage | Historic aggregate/security/repository/vault/cloud floors बहुत नीचे रहे। | Invariant-centric executed test mesh। |
| Hilt/generated sources | कुछ CI runs में generated Dagger/Hilt compilation failures। | Live dependency/plugin/KSP graph verify करके minimal root-cause fix। |
| Cloud/restore | Restore transport/integrity/recovery evidence gaps। | Complete restore chain verified होने तक gate बंद। |

---

# 5. CPAS Repository Structure

```text
docs/assurance/
  00-constitution.md
  01-scope.md
  02-definitions.md
  03-evidence-policy.md
  04-technology-registry.yaml
  05-security-invariants.yaml
  06-requirements-traceability.yaml
  07-test-matrix.yaml
  08-supply-chain.yaml
  09-recovery-assurance.yaml
  10-remediation-policy.yaml
  FINAL_CPAS_SHEET.md

tools/audit/

.github/workflows/
  cpas-gate.yml

evidence/
  evidence-ledger.yaml
```

---

# 6. Technology Registry

Critical technologies के version, build और runtime invariants machine-readable contract में रखे जाएँ।

```yaml
technologies:
  sqlcipher:
    version: "4.5.4"
    category: database
    criticality: critical
    invariants:
      - correct_encryption_key_required
      - wrong_key_must_fail
      - no_plaintext_fallback
    build:
      version_specific: true
      contract_verified: false

  android_keystore:
    category: cryptography
    criticality: critical
    invariants:
      - production_uses_android_keystore
      - no_insecure_fallback
      - key_lifecycle_verified
```

---

# 7. Security Invariants

```yaml
invariants:
  - id: INV-STORAGE-001
    name: storage_fail_closed
    severity: critical
    rule: empty_allowed_roots_must_deny
    required_tests:
      - StorageRootBoundaryTest
      - StorageTraversalTest

  - id: INV-CRYPTO-001
    name: production_keystore
    severity: critical
    rule: production_must_use_android_keystore
    required_tests:
      - CryptoProviderInstrumentedTest

  - id: INV-DB-001
    name: sqlcipher_wrong_key_rejection
    severity: critical
    rule: wrong_passphrase_must_fail
    required_tests:
      - SQLCipherWrongKeyTest
```

---

# 8. SQLCipher Assurance

SQLCipher 4.5.4 को current/master 4.7+ documentation से blindly नहीं मिलाया जाएगा। उसी version के वास्तविक source tree और configure/make interface को authoritative माना जाएगा।

Required:

1. Version registry में pinned हो।
2. Source layout inspect हो।
3. Supported configure/make contract verify हो।
4. Non-existent source paths हटें।
5. Unsupported flags हटें।
6. Native fixture build CI में reproduce हो।
7. Successful CI run evidence ledger में दर्ज हो।

> SQLCipher version बदलने पर build contract फिर से validate होगा।

---

# 9. Android Keystore Assurance

Production में insecure fallback नहीं बनाया जाएगा।

```text
CryptoSecurityManager
        |
        v
KeyStore / Crypto Provider abstraction
        |
        +-- Production → AndroidKeyStore
        +-- JVM/Robolectric → deterministic test fake
        +-- Instrumented → real AndroidKeyStore
```

Tests:

- key creation/retrieval
- missing/wrong key
- encryption/decryption
- restart/reopen
- key invalidation
- authentication boundary
- provider selection

---

# 10. Storage Security — Fail Closed

यदि approved roots discover नहीं होते, तो empty `allowedRoots` universal permission नहीं बन सकता।

Required tests:

- approved path
- outside-root path
- `../` traversal
- encoded traversal
- absolute-path abuse
- symlink escape
- canonicalization mismatch
- missing/empty roots
- inaccessible root

Invariant:

```yaml
storage:
  fail_closed: true
  empty_allowed_roots: deny
  symlink_escape: deny
  traversal_escape: deny
```

---

# 11. Production Sample/Demo Data

Production file-listing APIs demo/sample files नहीं बनाएँगी। Listing का काम listing है।

Demo data केवल test fixtures अथवा explicitly gated debug/demo provider में रहेगा।

---

# 12. Coverage Assurance

Historic observed failures:

| Area | Coverage | Floor |
|---|---:|---:|
| Aggregate | 5.90% | 70% |
| Security-critical | 7.73% | 90% |
| Repository/Data | 8.36% | 85% |
| Vault | 18.82% | 95% |
| Cloud Sync | 0% | 90% |

Coverage के साथ invariant coverage भी मापी जाएगी। Critical invariant बिना executable verification के PASS नहीं होगा।

---

# 13. Hilt / Generated Code Assurance

Hilt/Dagger compilation failure पर पहले dependency जोड़ना समाधान नहीं माना जाएगा। Verify:

- Kotlin version
- KSP version
- Hilt version
- Hilt plugin
- compiler configuration
- generated sources
- dependency graph
- duplicate/mismatched versions
- JDK compatibility

> Direct dependency केवल evidence-backed root cause के बाद।

---

# 14. Cloud / Restore Assurance

Restore chain:

```text
Download
  ↓
Transport Integrity
  ↓
Authentication
  ↓
Decrypt
  ↓
Schema Validation
  ↓
Data Validation
  ↓
Stage
  ↓
Consistency Check
  ↓
Atomic Swap
  ↓
Post-Restore Verification
  ↓
Cleanup
```

हर stage पर process termination test आवश्यक है।

---

# 15. Test Mesh

## Security

- vault plaintext lifecycle
- key storage/lifecycle
- encryption/decryption
- wrong key/PIN
- Real/Decoy separation

## Storage

- root boundary
- traversal
- symlink
- canonicalization
- permissions

## Database

- all historical migrations
- interrupted migration
- corrupt DB
- wrong key
- schema mismatch
- reopen/restart

## Cloud

- authentication
- duplicate
- retry
- cancellation
- claim/heartbeat
- lease expiry
- owner guard
- terminal failure
- cleanup

## Concurrency

- duplicate execution
- simultaneous claim
- cancel/complete race
- lease race
- process death
- retry race

## Recovery

- process kill at every restore stage
- partial restore
- corrupted restore
- atomicity
- rollback

---

# 16. Adversarial / Fuzz Testing

Critical inputs:

- file paths
- Unicode filenames
- very long filenames
- malformed cloud payloads
- malformed plugin responses
- malformed database input
- unexpected JSON
- null/empty payloads
- encoded traversal
- huge inputs
- repeated retry signals

---

# 17. Lifecycle Assurance

Verify:

- cold start
- warm start
- background/foreground
- process death
- configuration change
- activity recreation
- WorkManager resumption
- interrupted operations

---

# 18. Offline / Network Matrix

Required scenarios:

```text
No network
Slow network
Timeout
Connection reset
Partial response
HTTP error
Auth expired
Token invalid
Server unavailable
Duplicate response
Malformed response
Retry storm
```

System must not lose data, corrupt state, bypass authorization या retry indefinitely।

---

# 19. Supply-Chain Assurance

Required:

- dependency inventory
- transitive dependency inventory
- lock verification
- vulnerability/CVE scan
- license policy
- SBOM
- build provenance
- artifact identity
- toolchain versions
- reproducible-build checks where practical

Dependency update flow:

```text
Research → Compatibility → Build → Tests → Security → CI
```

---

# 20. Standards Alignment

Relevant basis:

- OWASP MASVS
- OWASP MASTG
- OWASP SAMM
- NIST SSDF 1.1
- NIST SP 800-204D
- SLSA
- Official SQLCipher documentation/changelog
- Official Android Developers documentation
- Official Robolectric documentation
- Official Dagger/Hilt documentation

इनका उपयोग project-specific executable controls बनाने के लिए होगा।

---

# 21. Accessibility Gate

Verify:

- semantics
- content descriptions
- touch targets
- keyboard/focus behavior
- screen reader behavior
- contrast
- error messaging
- dynamic text

---

# 22. Localization Gate

Verify:

- missing translations
- hardcoded strings
- plural rules
- date/time formatting
- number formatting
- text expansion
- RTL जहाँ applicable हो

---

# 23. Permission / Privacy Matrix

API behavior explicitly test किया जाए:

```text
API 24
API 28
API 29+
API 33+
API 35+
API 36
```

विशेष रूप से storage, notification/media permissions, background behavior और MANAGE_EXTERNAL_STORAGE।

---

# 24. Performance / Battery Gates

Budgets define/verify करें:

- startup
- database
- encryption/decryption
- sync
- memory
- UI responsiveness
- WorkManager background behavior
- retry/backoff
- battery impact

---

# 25. Observability / Privacy

Logs में कभी भी निम्न नहीं होने चाहिए:

- passwords
- PINs
- encryption keys
- tokens
- plaintext secrets
- sensitive file contents
- sensitive cloud payloads

Privacy data map:

```text
Data → Collected? → Stored? → Encrypted? → Synced? → Shared? → Deleted? → Retention
```

---

# 26. Threat Model

Minimum:

```text
Assets
Trust Boundaries
Actors
Attack Surfaces
Abuse Cases
Threats
Controls
Residual Risk
```

Key threats:

- unauthorized local access
- PIN brute force
- traversal/symlink escape
- database theft
- cloud compromise
- restore poisoning
- malicious plugin
- malformed cloud data
- race conditions
- process interruption

---

# 27. Mutation Testing

Critical invariant की implementation को intentionally mutate करके tests के fail होने की पुष्टि की जाए।

उदाहरण:

```text
allowedRoots.isEmpty() → ALLOW
```

Security tests को इस mutation पर fail होना चाहिए।

---

# 28. False-Green Detector

CPAS verify करेगा:

- test skipped?
- ignored?
- filtered?
- cached only?
- emulator actually started?
- instrumentation actually executed?
- expected test count मिला?
- expected artifacts generated?
- coverage relevant source पर है?
- report empty/stale तो नहीं?
- CI target commit सही है?

Expected test execute नहीं हुआ तो result:

```text
UNVERIFIED / BLOCKED
```

---

# 29. CPAS Verifier

`cpasVerify` को केवल file existence check नहीं होना चाहिए।

Pipeline:

```text
Schema validation
↓
ID validation
↓
Traceability validation
↓
Technology contract validation
↓
Invariant validation
↓
Test mapping validation
↓
Execution evidence validation
↓
False-green validation
↓
Coverage validation
↓
Supply-chain validation
↓
Evidence freshness
↓
Gate aggregation
↓
Production status computation
```

---

# 30. Computed Production Status

Manual `READY` flag authoritative नहीं है।

```yaml
production_status:
  value: BLOCKED
  computed: true

blocking_findings:
  - PROD-001

critical_invariants:
  total: 37
  verified: 29
  unverified: 8

required_gates:
  total: 18
  passed: 13
  failed: 3
  pending: 2
```

---

# 31. CI Assurance Pipeline

```text
1. Constitution / Schema
2. Technology Contracts
3. Security Invariants
4. Static Analysis
5. JVM / Unit Tests
6. Instrumented / Emulator Tests
7. DB / Crypto / Storage Contracts
8. Concurrency / Recovery / Adversarial Tests
9. Coverage / Mutation Evidence
10. Supply Chain / License / SBOM
11. Evidence Ledger
12. cpasVerify
13. Production Gate
```

Independent stages parallelize किए जा सकते हैं। Final decision verified aggregation से होगा।

---

# 32. Evidence Ledger

प्रत्येक finding में:

- Finding ID
- severity
- component
- root cause
- fix commit SHA
- changed files
- regression test
- exact test result
- CI workflow/run ID
- branch/PR
- timestamp
- artifact/report reference
- status
- verifier version

Statuses:

```text
OPEN
FIXED
VERIFIED
BLOCKED
ACCEPTED-RISK
```

---

# 33. Remediation Policy

Low-risk auto-fix:

- formatting
- deterministic lint fixes
- documentation
- generated metadata

Human review mandatory:

- cryptography
- key management
- authentication
- authorization
- storage boundary
- database migrations
- restore
- cloud synchronization
- permissions
- privacy
- data deletion
- release configuration

---

# 34. Minimal Change Principle

हर fix:

```text
Smallest safe change
+
Regression test
+
Evidence
```

Unrelated refactoring उसी change में नहीं किया जाएगा जब तक root cause के लिए आवश्यक न हो।

---

# 35. PR / Branch Governance

Materially conflicting PR/branch को:

1. inspect
2. classify
3. determine whether still required
4. identify superseded work
5. resolve/merge/close/delete only with evidence

सिर्फ inconvenience के कारण deletion नहीं।

---

# 36. Release Assurance

Release से पहले verify:

- source commit
- dependency state
- build environment
- artifact checksum
- signing configuration
- provenance
- migration compatibility
- rollback strategy
- backup/recovery
- smoke tests
- critical invariants
- production gate

---

# 37. Definition of Done

The-Perfect production-ready तभी माना जाएगा जब:

- [ ] Live main branch verified है।
- [ ] Requested critical blockers = 0.
- [ ] Critical security invariants verified हैं।
- [ ] Storage fail-closed है।
- [ ] Production sample generation हट चुकी है।
- [ ] SQLCipher build contract version-correct और CI-verified है।
- [ ] Android Keystore production में secure/fail-closed है।
- [ ] Robolectric tests उचित fake provider उपयोग करते हैं।
- [ ] Real-device Keystore tests executed हैं।
- [ ] Database migrations verified हैं।
- [ ] Cloud queue concurrency verified है।
- [ ] Restore/recovery verified है।
- [ ] Adversarial tests verified हैं।
- [ ] False-green detector pass है।
- [ ] Coverage evidence वास्तविक execution से है।
- [ ] Supply-chain gates pass हैं।
- [ ] SBOM/provenance evidence मौजूद है।
- [ ] Accessibility gate pass है।
- [ ] Localization gate pass है।
- [ ] Permission matrix verified है।
- [ ] Performance/battery budgets acceptable हैं।
- [ ] Privacy data map मौजूद है।
- [ ] Threat model verified है।
- [ ] CPAS self-tests pass हैं।
- [ ] `cpasVerify` computed production status देता है।
- [ ] Relevant CI runs target commit पर successful हैं।
- [ ] Evidence Ledger complete है।
- [ ] कोई unresolved critical finding नहीं है।

---

# 38. Canonical Remediation Loop

```text
FINDING
   ↓
CLASSIFY
   ↓
RESEARCH
   ↓
PROVE ROOT CAUSE
   ↓
MINIMAL FIX
   ↓
REGRESSION TEST
   ↓
LOCAL VALIDATION
   ↓
GIT COMMIT
   ↓
GITHUB PUSH
   ↓
LIVE CI
   ↓
VERIFY
   ↓
EVIDENCE LEDGER
   ↓
CPAS RECOMPUTE
   ↓
NEXT BLOCKER
```

---

# 39. Final Production Rules

> **No evidence = No PASS.**

> **No executed test = No verification.**

> **No verified critical invariant = BLOCKED.**

> **No successful relevant CI = Not fixed.**

> **No computed gate = No production readiness.**

> **A green build alone is not production assurance.**

---

# 40. Final Execution Directive

The-Perfect को इस CPAS के आधार पर bottom-up तरीके से सुधारना है।

Priority:

```text
1. Live Repository / CI Truth
2. CPAS Foundation
3. Technology Contracts
4. Security Invariants
5. Storage / Crypto / Database
6. Cloud / Restore
7. Concurrency / Lifecycle
8. Test Mesh
9. Supply Chain
10. False-Green Detection
11. Evidence Ledger
12. Computed Production Gate
13. Release Assurance
```

हर blocker पर evidence-first approach लागू होगी।

**Final production verdict केवल CPAS-computed evidence का परिणाम होगा।**
