# FINAL CPAS SHEET
## The-Perfect — Continuous Production Assurance System (CPAS)

**Repository:** https://github.com/awasthisach/The-Perfect  
**Project:** VVF Smart Manager  
**Document Date:** 2026-09-02  
**Purpose:** Production-grade remediation, verification, CI governance and evidence assurance

---

# 1. Executive Objective

The-Perfect को केवल ऐसा Android application नहीं बनाना है जिसका build सफल हो जाए। लक्ष्य है evidence-driven, security-conscious, reproducible और production-assurable application। CPAS केवल audit checklist नहीं बल्कि repository के अंदर executable assurance system होगा। Production readiness documentation से नहीं बल्कि Code + Tests + CI + Evidence + Computed Gates से निकलेगा।

# 2. Operating Constitution

1. सबसे पहले live repository और live CI की जाँच होगी।
2. बिना evidence के कोई technical claim नहीं किया जाएगा।
3. अनुमान को evidence नहीं माना जाएगा।
4. Static inference को `STATIC-INFERENCE` के रूप में चिन्हित किया जाएगा।
5. हर failure का loop: Observe → Research → Root Cause → Minimal Fix → Regression Test → Local Validation → GitHub Push → Live CI → Evidence Capture → CPAS Verification।
6. Security, cryptography, storage, database और data-loss boundaries में fail-open behavior स्वीकार नहीं होगा।
7. Requested blocker समाप्त और CI से verified होने तक resolved नहीं माना जाएगा।
8. Obsolete artifact केवल evidence के आधार पर हटेगा या बंद होगा।
9. हर 3 या 5 वास्तविक verified fixes के बाद consolidated report दी जाएगी।
10. हर तीसरे/पाँचवें work cycle में operating rules का self-check होगा।
11. Green build अकेले production readiness का प्रमाण नहीं है।
12. Cached, skipped या ignored tests executed evidence नहीं हैं।

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

Exact toolchain versions live repository से verify होंगी।

# 4. Known Blocker Landscape

| क्षेत्र | Observed समस्या | Required action |
|---|---|---|
| SQLCipher CI | SQLCipher 4.5.4 पर newer 4.7+ build contract/paths का mismatch | उसी version के वास्तविक configure/make contract के अनुसार build |
| AndroidKeyStore tests | Robolectric/JVM में AndroidKeyStore उपलब्ध नहीं | Injectable provider; JVM fake; instrumented real Keystore tests |
| Storage boundary | Empty `allowedRoots` access को allow कर सकता है | Root discovery failure पर deny; traversal/symlink tests |
| Sample/demo data | Production listing empty directory पर sample files बना सकती है | Production side-effect हटाएँ; fixtures अलग करें |
| Coverage | Historic aggregate/security/repository/vault/cloud floors बहुत नीचे रहे | Invariant-centric executed test mesh |
| Hilt/generated sources | कुछ CI runs में generated Dagger/Hilt failures | Live dependency/plugin/KSP graph verify करके minimal root fix |
| Cloud/restore | Restore transport/integrity/recovery evidence gaps | Complete restore chain verified होने तक gate बंद |

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

# 6. Technology Registry

Critical technologies के version, build और runtime invariants machine-readable contract में रखे जाएँ। SQLCipher, Android Keystore, Gradle/JDK/Kotlin/KSP/Hilt और अन्य security-critical components के pinned versions तथा compatibility assumptions दर्ज हों।

# 7. Security Invariants

Critical invariants executable tests से verified होने चाहिए। उदाहरण:

```yaml
invariants:
  - id: INV-STORAGE-001
    name: storage_fail_closed
    severity: critical
    rule: empty_allowed_roots_must_deny
  - id: INV-CRYPTO-001
    name: production_keystore
    severity: critical
    rule: production_must_use_android_keystore
  - id: INV-DB-001
    name: sqlcipher_wrong_key_rejection
    severity: critical
    rule: wrong_passphrase_must_fail
```

# 8. SQLCipher Assurance

SQLCipher 4.5.4 को current/master 4.7+ documentation से blindly नहीं मिलाया जाएगा। उसी version के वास्तविक source tree और configure/make interface को authoritative माना जाएगा। Version pin, source layout, supported flags, native fixture build और successful CI evidence अनिवार्य हैं। Version बदलने पर build contract फिर validate होगा।

# 9. Android Keystore Assurance

Production में insecure fallback नहीं बनाया जाएगा। Architecture:

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

Key creation/retrieval, missing/wrong key, encryption/decryption, restart/reopen, invalidation, authentication boundary और provider selection test होंगे।

# 10. Storage Security — Fail Closed

यदि approved roots discover नहीं होते, तो empty `allowedRoots` universal permission नहीं बन सकता। Approved path, outside-root path, traversal, encoded traversal, absolute-path abuse, symlink escape, canonicalization mismatch, missing/empty roots और inaccessible roots test होंगे।

```yaml
storage:
  fail_closed: true
  empty_allowed_roots: deny
  symlink_escape: deny
  traversal_escape: deny
```

# 11. Production Sample/Demo Data

Production file-listing APIs demo/sample files नहीं बनाएँगी। Listing का काम listing है। Demo data केवल test fixtures अथवा explicitly gated debug/demo provider में रहेगा।

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

# 13. Hilt / Generated Code Assurance

Hilt/Dagger compilation failure पर पहले dependency जोड़ना समाधान नहीं माना जाएगा। Kotlin, KSP, Hilt, plugin, compiler configuration, generated sources, dependency graph, duplicate/mismatched versions और JDK compatibility verify होंगे। Direct dependency केवल evidence-backed root cause के बाद जोड़ी जाएगी।

# 14. Cloud / Restore Assurance

Restore chain:

```text
Download → Transport Integrity → Authentication → Decrypt → Schema Validation
→ Data Validation → Stage → Consistency Check → Atomic Swap
→ Post-Restore Verification → Cleanup
```

हर stage पर process termination, corruption और recovery tests आवश्यक हैं।

# 15. Test Mesh

Security, storage, database, cloud, concurrency और recovery के tests एक connected mesh होंगे। Security में vault/key/Real-Decoy; storage में boundary/traversal/symlink; database में migrations/corruption/wrong key; cloud में auth/duplicate/retry/cancel/lease/owner guard; concurrency में race conditions; recovery में process-kill और atomicity शामिल होंगे।

# 16. Adversarial / Fuzz Testing

File paths, Unicode filenames, very long filenames, malformed cloud/plugin/database payloads, unexpected JSON, null/empty payloads, encoded traversal, huge inputs और repeated retry signals fuzz/adversarial matrix में होंगे।

# 17. Lifecycle Assurance

Cold start, warm start, background/foreground, process death, configuration change, activity recreation, WorkManager resumption और interrupted operations verify होंगे।

# 18. Offline / Network Matrix

No network, slow network, timeout, connection reset, partial response, HTTP error, auth expiry, invalid token, server unavailable, duplicate response, malformed response और retry storm test होंगे। Data loss, state corruption, authorization bypass और infinite retry स्वीकार्य नहीं हैं।

# 19. Supply-Chain Assurance

Dependency inventory, transitive inventory, lock verification, vulnerability/CVE scan, license policy, SBOM, build provenance, artifact identity, toolchain versions और reproducible-build checks जहाँ practical हों अनिवार्य होंगे। Dependency update flow: Research → Compatibility → Build → Tests → Security → CI।

# 20. Standards Alignment

Relevant basis: OWASP MASVS, OWASP MASTG, OWASP SAMM, NIST SSDF 1.1, NIST SP 800-204D, SLSA, official SQLCipher documentation/changelog, official Android Developers documentation, official Robolectric documentation और official Dagger/Hilt documentation। Standards को project-specific executable controls में बदला जाएगा।

# 21. Accessibility Gate

Semantics, content descriptions, touch targets, keyboard/focus behavior, screen reader behavior, contrast, error messaging और dynamic text verify होंगे।

# 22. Localization Gate

Missing translations, hardcoded strings, plural rules, date/time formatting, number formatting, text expansion और applicable RTL behavior verify होंगे।

# 23. Permission / Privacy Matrix

API 24, API 28, API 29+, API 33+, API 35+ और API 36 पर storage, notification/media permissions, background behavior और MANAGE_EXTERNAL_STORAGE सहित permission behavior explicitly test होगा।

# 24. Performance / Battery Gates

Startup, database, encryption/decryption, sync, memory, UI responsiveness, WorkManager background behavior, retry/backoff और battery impact के measurable budgets define और verify होंगे।

# 25. Observability / Privacy

Logs में passwords, PINs, encryption keys, tokens, plaintext secrets, sensitive file contents या sensitive cloud payloads नहीं होंगे। Events actionable होंगे लेकिन privacy-preserving होंगे।

# 26. Privacy Data Map

हर sensitive data item के लिए collected, stored, encrypted, synced, shared, retained और deleted state documented और testable होगी। Data minimization और retention policy implementation से traceable होनी चाहिए।

# 27. Threat Model

Assets, trust boundaries, attackers, attack paths, mitigations और residual risk documented होंगे। विशेष focus storage traversal, key theft, PIN/decoy separation, cloud compromise, malicious files/plugins, replay, unauthorized restore और local privilege boundaries पर होगा।

# 28. Mutation Testing

Critical business/security tests की strength जाँचने के लिए selected mutation testing किया जाएगा। यदि intentional security-relevant mutation tests के बावजूद green रहती है, gate failure माना जाएगा।

# 29. False-Green Detector

CPAS skipped, cached, disabled, flaky या non-executed evidence को PASS नहीं मानेगा। Test count, execution status, generated reports, coverage provenance और CI job outcome cross-check किए जाएँगे।

# 30. CPAS Verifier

`cpasVerify` वास्तविक executable verifier होना चाहिए; केवल file-existence check नहीं। यह registry, invariants, required tests, evidence ledger और gate outputs को parse करके deterministic result देगा और missing/stale/inconsistent evidence पर failure करेगा।

# 31. Computed Production Status

Production status manually declared नहीं होगा। उदाहरण:

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

Counts live evidence से computed होंगे।

# 32. Requirements ↔ Code ↔ Test Traceability

Requirements, implementation symbols, security invariants, tests, CI jobs और evidence के बीच bidirectional traceability graph रखा जाएगा। कोई production-critical requirement बिना implementation और executable verification के complete नहीं माना जाएगा।

# 33. Database Migration Assurance

सभी historical migrations, fresh install, upgrade path, interrupted migration, corrupt database, wrong key, schema mismatch, reopen/restart, rollback/downgrade attempt और partial migration scenarios verify होंगे।

# 34. Disaster Recovery / Atomic Restore

Restore में download से post-restore verification तक प्रत्येक boundary पर process kill और failure injection किया जाएगा। Staging, consistency validation, atomic swap, rollback और cleanup के बाद final database integrity verify होगी।

# 35. Reproducible Build Assurance

Pinned toolchain, dependency resolution, generated-source determinism और artifact identity verify किए जाएँगे। जहाँ पूर्ण reproducibility practical नहीं हो वहाँ known nondeterminism documented और controlled होगा।

# 36. Concurrency / Race Assurance

Duplicate execution, simultaneous claim, cancel/complete race, lease expiry, heartbeat, process death, retry race और terminal cleanup के deterministic tests होंगे। Cloud queue semantics में owner guards और transfer-state preservation verify होंगे।

# 37. Android Lifecycle / Process-Death Assurance

Critical user operations और background work को activity recreation, process death, reboot-equivalent interruption और WorkManager resumption के बाद safe state में लौटना चाहिए। कोई operation silent data loss या duplicate side effect नहीं बनाएगा।

# 38. Permission / Platform Compatibility Assurance

Target और supported Android API levels पर runtime permissions, scoped storage, notification/media access, background restrictions और platform behavior को implementation तथा tests से map किया जाएगा। Deprecated APIs और policy-sensitive permissions की explicit justification होगी।

# 39. Release / Rollback Engineering

Release artifact identity, versioning, signing assumptions, migration compatibility, rollback boundary, failed-update recovery और release evidence documented होंगे। A release is not production-ready until rollback behavior is verified for applicable failure classes.

# 40. Performance / Reliability Budgets

Critical operations के measurable budgets होंगे: startup, database operations, crypto, sync latency, memory, retries, background work और battery. Budget regression CI में detectable होना चाहिए जहाँ practical हो।

# 41. Accessibility / UX Quality Gate

Critical flows keyboard/focus, screen reader, semantics, touch target, contrast, error recovery और dynamic text के साथ usable होने चाहिए। Accessibility regressions को quality gate में represent किया जाएगा।

# 42. Evidence Ledger

हर claim के साथ source, commit, workflow run, test name, artifact/report और timestamp दर्ज होगा। Evidence immutable reference के रूप में capture होगी। Stale evidence current status को override नहीं कर सकती।

# 43. CI Assurance Pipeline

Canonical pipeline:

```text
Checkout
→ Toolchain Verification
→ Static Analysis
→ Unit Tests
→ Instrumented Tests
→ Security Tests
→ Coverage
→ Supply-Chain Checks
→ Build
→ Artifact Verification
→ CPAS Verification
→ Production Status
```

Failure पर downstream green signal को overall PASS नहीं माना जाएगा।

# 44. Remediation Policy

Fix priority: security/data-loss/authorization → build/CI blockers → correctness → reliability → coverage/invariant gaps → performance → UX/documentation. हर fix minimal root-cause change के साथ regression test जोड़ेगा।

# 45. Minimal Change Principle

Working behavior को बिना आवश्यकता rewrite नहीं किया जाएगा। Refactor तभी होगा जब root cause, security boundary, testability या maintainability के लिए आवश्यक हो। हर change का blast radius review होगा।

# 46. PR / Branch Governance

Stale, conflicting, duplicate या obsolete PR को live state के आधार पर classify किया जाएगा। Mergeability, required checks, review status और branch divergence verify किए बिना merge/delete/close claim नहीं किया जाएगा। Main branch पर केवल evidence-backed changes स्वीकार होंगे।

# 47. Definition of Done

किसी blocker को DONE तभी कहा जाएगा जब:

1. root cause identified हो;
2. minimal fix लागू हो;
3. regression test मौजूद हो;
4. relevant local validation सफल हो;
5. GitHub commit मौजूद हो;
6. live CI relevant gate पास करे;
7. evidence ledger update हो;
8. CPAS verifier updated status दे;
9. कोई higher-level blocker unresolved होने पर status उसे reflect करे।

# 48. Canonical Remediation Loop

हर वास्तविक समस्या के लिए canonical loop अनिवार्य है:

```text
Observe
→ Reproduce
→ Research
→ Root Cause
→ Minimal Fix
→ Regression Test
→ Local Validation
→ Push
→ Live CI
→ Evidence Capture
→ CPAS Verify
→ Re-audit
```

एक failed verification के बाद समस्या को silently closed नहीं किया जाएगा।

# 49. Final Production Rules / Reference Basis / Execution Directive

CPAS का अंतिम निर्णय evidence-driven और computed होगा। Green build को production readiness के बराबर नहीं माना जाएगा। Security invariant, data integrity, restore/recovery, dependency/supply-chain, lifecycle, permissions, accessibility, privacy, performance और release gates में कोई critical unverified condition हो तो production status `BLOCKED` रहेगा।

Final execution rule:

> **Live evidence पहले, root cause उसके बाद, minimal repair उसके बाद, regression test उसके बाद, GitHub push उसके बाद, CI verification उसके बाद, और तभी production status update।**

CPAS स्वयं भी audit योग्य है: verifier deterministic होना चाहिए, evidence stale होने पर fail करना चाहिए, false-green conditions पकड़नी चाहिए और अपने critical controls के लिए self-tests रखने चाहिए। यही document The-Perfect के production remediation और assurance workflow का canonical reference है।
