# FINAL CPAS SHEET
## The-Perfect — Continuous Production Assurance System (CPAS)

**Repository:** `awasthisach/The-Perfect`  
**Project:** VVF Smart Manager  
**Document date:** 2026-09-02  
**Document role:** Canonical assurance policy/index — not an independent production certificate  
**Current production decision:** computed by `tools/audit/cpas_verify.py` and CI evidence; this document must not contain manually maintained live counts.

---

# 1. Executive Objective

CPAS defines how production readiness is proved, not merely described. A production decision requires implementation, executable verification, live CI evidence and a computed gate. This sheet is the normative index; machine-readable assurance files and retained CI artifacts are the sources for live facts.

# 2. Status Semantics

Every assurance claim is classified as one of: `NORMATIVE`, `OBSERVED`, `VERIFIED`, `TARGET`, `HISTORICAL`, or `EXAMPLE`. `VERIFIED` requires reproducible evidence. `TARGET` is not current-state evidence. Historical measurements do not establish present readiness.

# 3. Operating Constitution

1. Inspect live repository and live CI before making technical claims.
2. Never treat inference or assumption as evidence.
3. Security, authorization, storage, cryptography and data-loss boundaries fail closed.
4. Follow: Observe → Reproduce → Research → Root Cause → Minimal Fix → Regression Test → Local Validation → Push → Live CI → Evidence → CPAS Verify → Re-audit.
5. A blocker is not closed until its closure criteria and relevant CI evidence are satisfied.
6. Green build alone is never production proof.
7. Skipped, cached, disabled or non-executed tests are not passing evidence.

# 4. Current Assurance Baseline

The authoritative current baseline is distributed across:

- `04-technology-registry.yaml`
- `05-security-reliability-invariants.yaml`
- `07-test-matrix.yaml`
- `08-risk-register.yaml`
- `evidence-ledger.json`
- `tools/audit/cpas_verify.py`
- live GitHub Actions evidence

This sheet intentionally does not duplicate live counts or claim that target-state files already exist.

# 5. Assurance Repository Structure

The actual repository structure is authoritative. Files that are not present are not treated as present merely because a target architecture lists them. Future assurance artifacts may include scope, definitions, evidence policy, traceability, recovery, supply-chain and remediation-policy files, but each must be created and wired before being treated as an executable control.

# 6. Technology Registry

Technology versions, compatibility assumptions and security-critical pins belong in `04-technology-registry.yaml`. The sheet does not hard-code version-sensitive facts. SQLCipher, Android SDK/JDK/Kotlin/KSP/Hilt, storage APIs and cryptographic providers must be verified against the actual build and runtime contract.

# 7. Canonical Security and Reliability Invariants

The canonical invariant IDs are those in `05-security-reliability-invariants.yaml`. Current examples include `STORAGE-INV-001`, `STORAGE-INV-002`, `DB-INV-001`, `RESTORE-INV-001` and `WORK-INV-001`. No second naming family should be introduced in this sheet without an explicit alias/deprecation rule.

# 8. Control Record Contract

A production-critical control should be representable as machine-readable data with at least: `control_id`, `domain`, `severity`, `statement`, `implementation_refs`, `test_refs`, `ci_job_refs`, `evidence_requirements`, `pass_condition`, `owner`, `status`, `last_verified_commit` and, where applicable, `expires_at` and `exception_ref`.

# 9. SQLCipher Assurance

SQLCipher assurance is version-specific. The pinned version, native source/build contract, configuration flags, fixture and CI result must agree. Wrong-key rejection, reopen, migration, backup/restore and corruption behavior must be tested. Documentation for another SQLCipher release is not evidence for the pinned release.

# 10. Android Keystore Assurance

Production cryptographic keys must use the approved Android Keystore path; insecure production fallbacks are prohibited. Testability may use an explicit JVM fake, but the production provider boundary must be tested on Android. Verify key creation/retrieval, missing or invalid key, encryption/decryption, restart and invalidation behavior.

# 11. Storage Boundary — Fail Closed

Approved-root discovery failure must deny access rather than broaden it. Canonical containment, traversal, encoded traversal, absolute paths, symlink escapes, inaccessible roots and empty-root behavior require negative tests. User-selected shared storage should use the Android Storage Access Framework where applicable; broad filesystem access requires an explicit policy justification.

# 12. Production Sample/Demo Data

Production listing and read paths must not create sample/demo files or mutate user storage as a side effect. Fixtures belong to tests or explicitly gated debug/demo providers. Storage statistics and other metadata must not fabricate successful values after a read failure.

# 13. Coverage Assurance

Coverage is evidence, not a design requirement by itself. Any reported percentage must identify tool, commit, denominator, report artifact and execution status. Historical coverage figures belong in dated evidence/audit records, not this canonical sheet. Critical invariants require executable verification even when aggregate line coverage is high.

# 14. Cloud Upload Contract

A successful cloud upload must return durable provider identity and verified integrity metadata. For Google Drive, use folder IDs as parents, persist remote file identity, verify provider metadata where available, compare local and remote integrity/size, and do not mark an operation successful before those checks pass.

# 15. Cloud Restore Contract

Restore must be fail closed:

`Download → Transport Integrity → Authentication → Decrypt → Schema/Data Validation → Isolated Stage → Consistency Check → Atomic Apply → Post-Restore Verification → Cleanup`

Corrupt, incomplete or unauthenticated backup data must never overwrite valid local state.

# 16. Restore Recovery and Atomicity

Restore requires failure injection at download, verification, decryption, validation, staging, apply and cleanup boundaries. Rollback must be defined for the actual persistence mechanism; process-local rollback tokens alone are not sufficient proof of crash recovery across process death.

# 17. Test Mesh

Security, storage, database, cloud, concurrency, lifecycle and recovery tests must map to controls/invariants. Positive tests alone are insufficient; negative, corruption, authorization and interruption cases are required for security-critical flows.

# 18. Adversarial / Fuzz Testing

Exercise traversal, Unicode and long paths, malformed payloads, invalid JSON, empty/null data, hostile archive entries, large inputs, repeated retry signals and unexpected provider responses. Fuzz findings must become reproducible regression tests when a defect is discovered.

# 19. Lifecycle / Process-Death Assurance

Critical operations must be safe across cold/warm start, activity recreation, background/foreground transitions, process death, reboot-equivalent interruption and WorkManager resumption. No interruption may silently create data loss, duplicate terminal effects or inconsistent durable state.

# 20. Offline / Network Matrix

Verify no network, slow network, timeout, reset, partial response, provider errors, authentication expiry/revocation, malformed responses, duplicate responses and retry storms. Retry behavior must be bounded, durable and idempotent.

# 21. Concurrency / Lease Assurance

Cloud queue and background operations require owner guards, durable operation IDs, lease expiry/heartbeat semantics, duplicate-execution protection and deterministic cancel/complete/retry race handling. A provider retry must not create an unintended duplicate terminal effect.

# 22. Requirements ↔ Code ↔ Test Traceability

Maintain bidirectional traceability between requirement/control, implementation symbol, test, CI job and evidence. Orphans are failures: requirement without verification, test without requirement, evidence without source commit, and closed risk without closure evidence.

# 23. Evidence Ledger Contract

`evidence-ledger.json` is the current evidence store. A future hardened schema should record control/invariant ID, test ID, source commit, workflow run/job, artifact/report reference, artifact SHA-256 digest, environment, execution time, result, reviewer/owner and expiry where applicable. Empty or malformed evidence cannot support production PASS.

# 24. Evidence Freshness and Immutability

Evidence is valid only for the exact source and gate it proves. Preferred immutable references are Git commits plus retained CI artifacts/attestations with digests. Stale evidence must not override newer failures. Freshness windows should be control-specific and machine-enforced once the schema supports them.

# 25. Exception / Waiver Policy

Exceptions require a unique ID, rationale, affected control/release, compensating control, residual risk, approver and expiry. Expired exceptions block the affected gate. Waivers must not silently bypass wrong-key rejection, unauthorized restore, data-integrity or equivalent critical controls.

# 26. Threat Model

Document assets, trust boundaries, attacker capability, abuse case, impact, mitigation, detection, recovery and residual risk. Priority cases include path traversal/symlink escape, key theft, real/decoy separation, malicious files/plugins, cloud object replay, token compromise, unauthorized restore and process interruption.

# 27. Privacy Data Map

Sensitive data must be mapped across collection, storage, encryption, synchronization, sharing, retention and deletion. Include vault content, database data, keys/tokens, cloud metadata, logs, notifications and exported/shared content. Data minimization must be testable.

# 28. Platform / Permission Assurance

Map supported Android API levels to actual permission and storage behavior. Verify scoped storage, SAF grants/revocation, MediaStore behavior, notification/media permissions, background restrictions and any special-access permission. `MANAGE_EXTERNAL_STORAGE` requires a documented product-policy justification and denial behavior.

# 29. Accessibility / Localization Assurance

Critical flows require measurable checks for semantics, content descriptions, focus/keyboard behavior, screen readers, touch targets, contrast, error recovery, dynamic text, translations, pluralization, formatting, text expansion and applicable RTL behavior.

# 30. Performance / Battery Assurance

Define measurable budgets for startup, database work, crypto throughput, sync latency, memory, UI responsiveness, retries, background execution and battery impact. Budgets become blocking only when they are defined, measured and reproducibly gated.

# 31. Semantic Search / AI Claims

Do not call lexical `LIKE` search semantic vector retrieval. Separate lexical and semantic APIs where both exist, expose model readiness/failure states, bind derived indexes to content identity/version, and use an appropriate persisted vector/ANN index when scale requires it. A model or index is not production-ready merely because an interface exists.

# 32. Content Identity and Derived State

Physical content identity must match indexed/derived identity. Any change to file content must invalidate stale hashes, OCR, embeddings, fingerprints and other derived state. Destructive automation requires fresh exact identity verification.

# 33. Physical File / Database Consistency

Physical operations and Room transactions are not inherently atomic together. Security-critical mutations require an operation journal/state machine with at least operation ID, source, target, expected content identity and durable state, followed by reconciliation after interruption.

# 34. Duplicate / Destructive Automation

Duplicate cleanup and other destructive actions must never rely on stale metadata or incomplete scans. An incomplete source scan must not trigger global stale deletion. Destructive action requires current identity evidence and post-operation verification where the provider supports it.

# 35. Release Artifact Assurance

For a production release, retain and verify version/code, variant, signing identity/fingerprint, APK/AAB SHA-256, mapping file, dependency/SBOM state, provenance/attestation, merged manifest and release notes. Artifact upload is not equivalent to artifact identity verification.

# 36. Supply-Chain Assurance

Verify direct/transitive dependencies, lock state, vulnerability/CVE policy, licenses, SBOM, provenance, artifact identity and toolchain pins. SLSA/reproducible-build claims must name the target level/acceptance criteria and actual evidence; broad labels alone are not proof.

# 37. CI Assurance Pipeline

Canonical gate order is conceptually:

`Checkout → Toolchain → Static Analysis → Unit → Instrumented → Security → Coverage → Supply Chain → Build → Artifact Verification → CPAS Verify → Computed Status`

The actual workflow is authoritative. A gate must identify command, environment, threshold, artifact and blocking behavior before it is considered a required control.

# 38. False-Green Detection

CPAS must distinguish executed from skipped, cached, disabled, flaky and unavailable tests. Evidence must identify the exact commit and workflow run. A green summary with a missing required job, missing report or skipped security test is not PASS.

# 39. CPAS Verifier Contract

`tools/audit/cpas_verify.py` is the executable entry point. Its current implementation is intentionally fail-closed but must evolve beyond file-presence/heading/risk/ledger-shape checks. The target verifier must validate references, duplicate IDs, evidence provenance/freshness, gate outputs, status contradictions and closed-risk evidence deterministically.

# 40. Verifier Self-Assurance

The verifier itself requires tests for malformed YAML/JSON, duplicate IDs, unknown references, empty evidence, forged PASS, stale evidence, expired waivers, digest mismatch, contradictory status and unexpected severity values. Verification must work deterministically without depending on network availability.

# 41. Computed Production Status

Production status is generated from machine-readable checks and live evidence. The sheet must not contain manually maintained totals such as “37 invariants” or “18 gates”. The generated `cpas-status.json`/CI result is the current computed output; any blocker keeps status `BLOCKED`.

# 42. Risk Register and Closure

`08-risk-register.yaml` is authoritative for current findings. A finding may move through `discovered → triaged → reproduced → root_cause_verified → repair_implemented → tested → ci_verified → closed`. Closure requires the finding's declared evidence and CI conditions; editing its status without proof is invalid.

# 43. Remediation Policy

Priority is: security/data loss/authorization → build/CI blockers → correctness/reliability → evidence and invariant gaps → performance → UX/documentation. Fixes should be minimal, root-cause based and accompanied by regression coverage. Obsolete artifacts are removed only after confirming they are no longer required evidence or dependencies.

# 44. Minimal Change / Blast Radius

Do not rewrite working architecture merely to satisfy the sheet. Prefer the smallest safe change that closes the verified root cause, then review affected modules, migrations, public contracts, security boundaries and rollback implications.

# 45. PR / Branch Governance

Mergeability, required checks, review status, branch protection/rulesets and divergence must be verified from live GitHub state. Workflow existence is not proof that a check is required. Main/release branches require the repository's actual configured protection evidence.

# 46. Definition of Done

A blocker is DONE only when root cause is verified, minimal repair is implemented, regression test exists, relevant validation passes, the GitHub commit exists, the relevant live CI gate passes, evidence is captured, CPAS recomputes the expected status, and no higher-level blocker is hidden.

# 47. Canonical Remediation Loop

```text
Observe
→ Reproduce
→ Research authoritative sources
→ Root Cause
→ Minimal Fix
→ Regression Test
→ Local Validation
→ GitHub Push
→ Live CI
→ Evidence Capture
→ CPAS Verify
→ Re-audit
```

A failed verification returns the item to remediation; it is never silently closed.

# 48. Production Decision Rule

`PASS` requires all required controls to have valid evidence and all critical/high release blockers to be closed, with the exact release commit passing the applicable build, static, test, security, artifact and runtime gates. Otherwise the result is `BLOCKED` or `UNVERIFIED` as defined by the verifier contract.

# 49. Final Reference Basis / Execution Directive

CPAS aligns project-specific controls with applicable official Android guidance, OWASP MASVS/MASTG, NIST SSDF, SLSA, and version-specific vendor documentation. Standards are references, not proof by themselves.

**Final execution rule:**

> **Live evidence first → authoritative research → verified root cause → minimal repair → regression test → GitHub push → live CI → immutable evidence → CPAS verification → re-audit.**

This document is the canonical assurance policy/index. Current truth comes from the repository's machine-readable assurance records, source code, tests, GitHub configuration, CI runs and retained evidence—not from prose in this file alone.
