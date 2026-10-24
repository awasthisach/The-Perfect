# Production Blocker Remediation Runbook

Repository: `awasthisach/The-Perfect`  
Candidate baseline reviewed: `f3a5d34c9bcd108c3242fa6d0551d8f99e82dcc8`  
Purpose: resolve PROD-008, PROD-009 and PROD-010 with reproducible evidence. This document does not itself close risks or assert production readiness.

## Governing rule

Keep CPAS fail-closed. Do not change a blocker to `closed` merely because code was changed, a workflow completed, or a health endpoint returned 200. Close only after the acceptance evidence below is attached to the corresponding issue and verified against the exact candidate SHA. Record run URL, run ID, commit SHA, artifact name/digest, timestamp, reviewer and disposition. Redact access tokens, ID tokens, App Check tokens, secrets and personal data from evidence.

## PROD-008 — ORT open-source compliance (Issue #136)

### Required execution

1. Dispatch **Nightly ORT Compliance Scan** for the exact candidate commit; do not infer that a run on another SHA applies.
2. Require Analyzer, Scanner, Evaluator, Advisor and Reporter/SBOM stages to execute. Preserve raw reports and logs even when the policy gate fails.
3. Validate `ort-status.json.scan_revision == candidate SHA` and require the configured CycloneDX and SPDX SBOM outputs to exist, parse successfully, and describe that same dependency graph.
4. Review every evaluator violation and advisor finding. Record package URL (purl), resolved version/revision, license expression or CVE, severity, source URL, evidence, decision, rationale and reviewer.
5. Correct inaccurate metadata with package-specific ORT curations supported by upstream package metadata, source repository, revision, or source archive/hash. A curation must not be used to relabel a license without evidence.
6. For genuine incompatibility or unresolved high-risk findings, update the risk and release decision; do not blanket-allow, suppress, or manufacture a PASS.
7. Rerun after changes and retain both the original failure and the successful remediation run. Attach the evidence to Issue #136 before changing risk status.

### Acceptance

- All required ORT stages completed and required reports/SBOMs validated.
- Scan revision exactly equals the proposed release SHA.
- Every finding has an evidence-backed disposition; no unexplained policy violations remain.
- CI and CPAS pass on the same candidate SHA.

## PROD-009 — Firebase App Check / Worker v4 (Issue #140)

### Owner-controlled setup

1. Confirm Firebase project `thevvforg` and Android application ID `com.vvf.smartmanager`.
2. In Play Console, retrieve the **App signing key certificate SHA-256** (not the upload-key fingerprint). Link the correct Google Cloud project to Play Integrity.
3. Register the Android app in Firebase App Check with Play Integrity and the correct signing fingerprint. Do not enable enforcement until the legitimate client has been tested and server rejection behavior is understood.
4. Deploy the Worker only after verifying that it validates Firebase Authentication ID tokens and App Check tokens independently. A Google Drive OAuth token is not a substitute for a Firebase ID token. Never log bearer tokens.
5. Verify health/version reports the accepted v4 contract and verify server-side enforcement; a health endpoint alone is not acceptance evidence.

### Required physical-device tests

- A Play-installed signed candidate on a real, supported Android device sends a valid Firebase ID token and valid App Check token and receives the expected successful response.
- Missing Firebase token, invalid/expired Firebase token, missing App Check token, invalid App Check token, and malformed requests are rejected; capture status codes and redacted server logs.
- Confirm authorization is checked server-side for every protected route and that CORS/Origin checks are not treated as authentication.
- Record device model, Android version, install channel, candidate SHA, artifact SHA-256, test time and sanitized outcomes.

### Acceptance

Attach Firebase/Play configuration evidence and the real-device positive and negative test results to Issue #140. Keep the risk open if account configuration or device evidence is unavailable.

## PROD-010 — OAuth, privacy, signing and release acceptance (Issue #141)

1. Verify the Google OAuth consent configuration, support contact, verified owned domain, app homepage, exact requested scopes and publishing status. Submit for Google verification if the requested API scopes and app scenario require it; do not assume every app is exempt or every app requires the same review.
2. The public homepage and privacy policy must be reachable without login, hosted on an owned/verified domain where required, and consistent with OAuth consent configuration. The policy must accurately explain Google user-data access, use, storage, sharing, retention and deletion, security, and contact details. Remove all placeholders.
3. Complete Play Console Data safety declarations from actual application and SDK data flows; make them consistent with the privacy policy and Google API Services User Data Policy.
4. Configure Play App Signing and protect the upload key. Never commit keystores, passwords, OAuth client secrets, Firebase service-account credentials or API secrets. Use least-privilege repository secrets and permissions.
5. Build the release candidate from the reviewed commit, verify its provenance and SHA-256, install the signed build on a physical device and exercise: first launch, sign-in, Drive authorization, vault encrypt/decrypt, backup/restore and interrupted-restore behavior, offline operation, keyword/semantic search, App Check, logout, and upgrade/rotation paths.
6. Attach sanitized test records and artifacts to Issue #141. Any untested flow remains explicitly unverified.

## Final release gate

Do not publish or mark production-ready until all of the following are true for one exact candidate SHA:

- Unit tests, lint, build, security checks and required SQLCipher instrumentation pass.
- ORT stages, policy review and SBOM verification pass.
- Worker v4 Auth + App Check positive/negative acceptance passes on a physical device.
- OAuth/privacy/Data safety and signing evidence are complete.
- CPAS reports `production_status.value == PASS` without weakening verifier rules or using unsupported evidence.
- A reviewer cross-checks the evidence ledger, source commit, CI run IDs and artifact hashes.

If an external console or physical-device step cannot be performed by CI, leave it as an owner-controlled action and keep the risk blocked; never fabricate evidence.

## Official primary references

- ORT overview and pipeline stages: https://oss-review-toolkit.org/ort/docs/intro
- ORT package curations: https://oss-review-toolkit.org/ort/docs/configuration/package-curations
- ORT tutorial: https://oss-review-toolkit.org/ort/docs/getting-started/tutorial
- Firebase App Check with Play Integrity on Android: https://firebase.google.com/docs/app-check/android/play-integrity-provider
- Android Play Integrity setup: https://developer.android.com/google/play/integrity/setup
- Google OAuth verification requirements: https://support.google.com/cloud/answer/13464321
- Google OAuth verification submission process: https://support.google.com/cloud/answer/13461325
- GitHub Actions secure use reference (SHA pinning): https://docs.github.com/en/actions/reference/security/secure-use
- GitHub Actions repository security settings: https://docs.github.com/en/repositories/managing-your-repositorys-settings-and-features/enabling-features-for-your-repository/managing-github-actions-settings-for-a-repository
