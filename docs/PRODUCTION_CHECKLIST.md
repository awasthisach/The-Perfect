# Production readiness checklist

This checklist is fail-closed: a skipped check or a code change is not proof that an external service was configured or a real device passed.

## Automated repository gates
- [ ] Daily CI on the exact candidate SHA passes audit tests, Android unit tests, lint, and `assembleDebug`.
- [ ] The Debug APK artifact was uploaded by the same successful CI run and its commit SHA matches the candidate.
- [ ] The separate nightly ORT workflow completes Analyzer, Scanner, Evaluator, Advisor, and Reporter without timeouts.
- [ ] Review `evaluation-result.json` for license policy violations and `advisor-result.json` for vulnerabilities. Fix or approve findings with evidence; do not suppress findings.
- [ ] Required ORT reports and CycloneDX/SPDX SBOMs validate for the exact SHA; `ort-status.json` says PASS and records that SHA.
- [ ] CPAS verifier passes without changing its fail-closed policy: `python3 tools/audit/cpas_verify.py --root .`.
- [ ] Weekly SQLCipher instrumentation hard gate passes on an Android emulator or physical device; an emulator infrastructure skip is not a pass.
- [ ] CodeQL and dependency/security checks pass on the same candidate SHA.

## PROD-008 — ORT / Issue #136
- [ ] Run Nightly ORT Compliance Scan for the exact candidate SHA after the workflow is available on the default branch.
- [ ] Download the `ort-results-evidence` artifact and review analyzer, scanner, evaluator, advisor, reporter output and both SBOM formats.
- [ ] Resolve or formally approve each finding with package-specific evidence; never use blanket allowlists or mark a scan PASS when a stage/report is missing.
- [ ] Repository owner: uninstall the retired FOSSA GitHub App and remove only confirmed obsolete FOSSA required status contexts (`License Compliance`, `Dependency Quality`, `Security Analysis`). Do not remove CI, CodeQL, ORT, or CPAS requirements.

## PROD-009 — Firebase App Check / Issue #140
- [ ] In Play Console → Setup → App integrity, copy the SHA-256 certificate fingerprint for the Play App Signing key.
- [ ] In Firebase Console → App Check, register Android package `com.vvf.smartmanager` with that SHA-256 and enable Play Integrity.
- [ ] Confirm Firebase project/app ID and Cloudflare Worker configuration match the production app.
- [ ] Deploy Worker v4 through the protected workflow. Keep production Gemini embeddings disabled until billing, provider terms, retention/abuse monitoring, and data-handling approval are complete.
- [ ] On a physical device, prove a valid Firebase Auth ID token plus App Check token is accepted; missing/invalid tokens are rejected. Record sanitized request/response evidence.

## PROD-010 — OAuth, privacy, signing and Play / Issue #141
- [ ] In Google Cloud Console, verify OAuth consent-screen branding, scopes and verification status. Do not switch to Production until required restricted-scope verification/security assessment is complete.
- [ ] Verify the Web Client ID used by the app matches the registered Android/Firebase configuration and the actual signing fingerprints.
- [ ] Replace all placeholders in `docs/PRIVACY_POLICY_DRAFT.md`: legal entity, contact email, stable HTTPS URL, data retention/deletion, third-party Gemini processing, age restrictions, and user rights.
- [ ] Publish the privacy policy at a stable HTTPS URL and update Play Console Data Safety, permissions, account/data deletion and app-access declarations.
- [ ] Configure protected release-signing secrets and Play App Signing. Verify the signed APK/AAB signer fingerprint and provenance.
- [ ] Install the exact candidate artifact on a real supported Android device; test fresh install, login, Drive indexing, keyword search, embedding consent off/on/revocation, offline behavior, vault encrypt/restore/export, backup/restore, account switching, upgrade, crash and ANR monitoring.

## Release decision
- [ ] All three production blockers have linked evidence, not just checklist completion.
- [ ] The candidate is not declared production-ready while CPAS is BLOCKED or any required owner-controlled proof is missing.
