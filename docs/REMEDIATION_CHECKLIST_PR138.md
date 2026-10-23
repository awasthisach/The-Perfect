# PR #138 Remediation Checklist

**Repository:** `awasthisach/The-Perfect`  
**Candidate branch:** `candidate/drive-semantic-search-release`  
**Historical snapshot:** PR #138 head `59e712d045beffde424dae4c56febe6f0562e74b` (2026-10-17 UTC). This is an archived audit snapshot, not evidence for the current candidate. The integrated hardening changes are tracked in [PR #160](https://github.com/awasthisach/The-Perfect/pull/160); the latest SHA must be verified independently by current GitHub Actions runs.  
**PR:** [#138](https://github.com/awasthisach/The-Perfect/pull/138)  
**Decision at audit time:** **BLOCKED — keep Draft**

This checklist is fail-closed. A code change, a green sub-check, a skipped test, or a prose assertion is not sufficient to close a blocker. Each item requires evidence tied to the same candidate SHA that is intended for release.

## Historical verified state at snapshot (2026-10-17; not current release evidence)

| Area | Current state | Evidence | Closure condition |
|---|---|---|---|
| Build, unit tests, lint, debug APK | **In progress** on SHA `59e712d045beffde424dae4c56febe6f0562e74b` | [CI run #38979011280](https://github.com/awasthisach/The-Perfect/actions/runs/38979011280), job `Build, Test, Lint & Quality` | Wait for completion and retain the result on the final candidate SHA |
| CodeQL | Passing | PR checks | Re-run on the final candidate SHA |
| SQLCipher instrumentation | **Passed** on SHA `59e712d045beffde424dae4c56febe6f0562e74b` | [Weekly Instrumented Hard Gate #38979011282](https://github.com/awasthisach/The-Perfect/actions/runs/38979011282) | Keep a non-skipped run on the final candidate SHA |
| Embedding Worker build/deploy contract | Passing for build/health/unauthenticated rejection | PR checks and Worker workflow | Complete real native Auth + App Check acceptance |
| Open-source license compliance (ORT) | **Migration committed; validation pending**. Historical FOSSA evidence recorded 34 runtime policy findings on SHA `59e712d045beffde424dae4c56febe6f0562e74b` | [Historical FOSSA CI run #38979011280](https://github.com/awasthisach/The-Perfect/actions/runs/38979011280); issue [#136](https://github.com/awasthisach/The-Perfect/issues/136); current ORT workflow in `.github/workflows/nightly-ort.yml`; release promotion requires successful evidence for the exact release SHA | Run ORT on the current candidate SHA, review its license/evaluator/advisor reports, remediate or document evidence-backed decisions, and pass the new gate |
| CPAS | **Failed closed** on SHA `59e712d045beffde424dae4c56febe6f0562e74b`; blockers `PROD-008`, `PROD-009`, `PROD-010` | [CPAS Production Gate #38979011368](https://github.com/awasthisach/The-Perfect/actions/runs/38979011368) | Close each finding with current, reproducible evidence |
| Privacy publication | **Blocked**; draft still contains placeholders | [`PRIVACY_POLICY_DRAFT.md`](PRIVACY_POLICY_DRAFT.md) | Owner/legal review complete and stable HTTPS policy URL published |
| Release signing / Play Integrity | **Blocked by external setup** | Firebase App Check console and Play Console access check on 2026-10-17 | Firebase App Check says app is unregistered with no SHA-256; connected Google account has no Play Console developer account; paid billing is unlinked |
| Physical-device acceptance | **Unverified** | [`RELEASE_CHECKLIST.md`](RELEASE_CHECKLIST.md) | Clean-device test report attached to the final artifact digest |

## Verified external prerequisites (2026-10-17)

- Google Cloud project `thevvforg` has no linked Cloud Billing account. Keep `GEMINI_PRODUCTION_APPROVED=false`; the Worker source now blocks health and embedding until the owner confirms the intended paid Gemini tier and provider data-handling terms.
- Firebase App Check lists `com.vvf.smartmanager` as **Unregistered**; Play Integrity is not configured and no SHA-256 fingerprint is registered.
- The connected Google account is redirected to Play Console developer-account signup; no Play Console app listing or Play App Signing fingerprint is accessible.
- Google Auth Platform is External, in Testing, and not verified. The exact restricted `drive.readonly` verification state remains unconfirmed.
- The archived FOSSA diagnostics artifact for run #38979011280 includes match-path data for all seven affected coordinates; this is historical evidence, not an active paid-service dependency. Most flagged matches for six coordinates point to a shared `third_party_licenses.txt`; Jakarta Inject also has package-local Apache-2.0 evidence and a declared GPL-2.0-with-classpath-exception identifier. This is useful attribution evidence, but does not alone prove a false positive or settle redistribution rights. The dashboard’s per-issue disposition workflow remains unavailable in the current browser profile.

## P0 — resolve before any release-candidate approval

### P0.1 ORT license and vulnerability triage (`PROD-008`)

**Owner:** repository/release owner  
**Status:** blocked; FOSSA has been removed from CI and the free/open-source OSS Review Toolkit (ORT) is the replacement. Do not weaken policy thresholds or blanket-allow licenses.

- [ ] Run the ORT compliance workflow on the current candidate revision and retain evaluator, advisor, scanner and SBOM artifacts.
- [ ] Export a review inventory containing package, version, finding type, license/vulnerability, severity, direct/transitive path, and current disposition.
- [ ] For each finding, choose exactly one disposition:
  - [ ] upgrade, replace, or remove the dependency;
  - [ ] add a narrowly scoped, reviewable allow-list entry when policy permits;
  - [ ] record a documented exception with owner, rationale, compensating control, and expiry; or
  - [ ] mark as false positive only with supporting evidence.
- [ ] Run **Nightly ORT Compliance Scan** on the exact final SHA. Confirm `ort-status.json` is `PASS`, evaluator/advisor exit codes are 0, all required reports/SBOMs validate, and retain the run URL and artifact. ORT is not part of daily PR CI.
- [ ] Attach ORT report/SBOM links and the disposition inventory to issue #136.
- [ ] Update `PROD-008` only after current ORT reports and evidence-backed dispositions exist.

**Acceptance:** `ort-status.json` reports `GATE-LICENSE-001/PASS` for the exact candidate SHA, the nightly workflow and evidence artifact are successful, and every finding has a durable disposition.

### P0.2 CPAS blocker `PROD-009`: native Worker authentication and App Check

**Owner:** Android/Worker owner  
**Status:** server-side contract is partially verified; native acceptance remains open.

- [ ] Confirm the deployed Worker is the intended v4 implementation and record the deployment URL, deployment revision, and health response.
- [ ] Verify health response: `ok=true`, model `gemini-embedding-2`, version `4`, dimension `768`.
- [ ] From a real signed Android build, verify a request with no Firebase credentials is rejected with `401`.
- [ ] Verify an invalid or expired Firebase Auth ID token is rejected.
- [ ] Verify a missing, invalid, or expired Firebase App Check token is rejected.
- [ ] Sign in on a supported physical device and verify a valid `POST /embed` succeeds using Firebase Auth ID token plus App Check token.
- [ ] Confirm the Drive OAuth token is never sent as Worker authentication and no Gemini API key is present in the Android artifact or request.
- [ ] Confirm Worker/application logs contain neither bearer tokens nor document text.
- [ ] Record device model, Android version, build variant, app version, Worker revision, request outcome, and sanitized logs.
- [ ] Update issue [#140](https://github.com/awasthisach/The-Perfect/issues/140) and close `PROD-009` only after the signed-build evidence is attached.

**Acceptance:** invalid credentials fail closed and a valid signed-device request succeeds with sanitized evidence tied to the release artifact digest.

### P0.3 CPAS blocker `PROD-010`: OAuth, privacy, signing, and device acceptance

**Owner:** release owner; requires Firebase/Google Play access and protected signing secrets  
**Status:** blocked by external configuration and human-only acceptance.

- [ ] Confirm Firebase Android app package is exactly `com.vvf.smartmanager`.
- [ ] Register the debug and release SHA-1/SHA-256 fingerprints in the correct Firebase project where applicable.
- [ ] Register and verify the correct App Check provider for the release build; ensure debug provider configuration cannot ship in release.
- [ ] Verify the OAuth client matches the production package and release signing certificate.
- [ ] Complete the applicable Google OAuth restricted-scope verification for `drive.readonly` and any required security assessment/CASA process.
- [ ] Confirm the Gemini project has active Cloud Billing and document provider retention/abuse-monitoring terms.
- [ ] Replace every placeholder in [`PRIVACY_POLICY_DRAFT.md`](PRIVACY_POLICY_DRAFT.md), including legal entity, monitored contact, effective date, policy URL, age-group treatment, providers, retention, deletion, and SDK disclosures.
- [ ] Reconcile the published policy with Play Data safety, in-app consent copy, account/data deletion behavior, and release permissions.
- [ ] Publish the reviewed policy at a stable HTTPS URL and add that URL to the Play listing and the app’s relevant disclosure surface.
- [ ] Produce the release AAB/APK in the protected release workflow using production signing secrets; verify there is no debug-keystore fallback.
- [ ] Record artifact SHA-256, signer certificate SHA-256, version code/name, SBOM, provenance/attestation, and workflow run/job URLs.
- [ ] Register Play App Signing and Play Integrity using the correct Play Console account and release certificate.
- [ ] Install the exact signed artifact on a clean supported device and execute the acceptance matrix below.
- [ ] Update issue [#141](https://github.com/awasthisach/The-Perfect/issues/141) and close `PROD-010` only after all external and device evidence is attached.

**Acceptance:** the production configuration, reviewed privacy publication, signed artifact, Play registrations, and clean-device test all refer to the same immutable release artifact.

## P1 — execute on the same candidate SHA

### P1.1 Automated verification

Run from repository root:

```bash
python3 -m unittest discover -s tools/audit -p 'test_*.py' -v
candidate_sha="$(git rev-parse HEAD)"
python3 tools/audit/collect_evidence.py --revision "$candidate_sha" --output candidate-evidence.json
python3 tools/audit/cpas_verify.py --root . --candidate-sha "$candidate_sha" --ledger candidate-evidence.json
./gradlew --no-daemon testDebugUnitTest --stacktrace
./gradlew --no-daemon lintDebug --stacktrace
./gradlew --no-daemon assembleDebug --stacktrace
```

- [ ] Dedicated `Weekly Instrumented Hard Gate` passes without an infrastructure skip.
- [ ] `Embedding Worker Build Gate` passes.
- [ ] CodeQL checks pass.
- [ ] CPAS verifier reports `production_status.value=PASS`.
- [ ] ORT reports `GATE-LICENSE-001/PASS`.
- [ ] All required checks are green for one identical candidate SHA.
- [ ] Preserve workflow URLs, job IDs, artifact names, and artifact digests in the evidence ledger.

### P1.2 Native acceptance matrix

| Case | Expected result | Evidence |
|---|---|---|
| Fresh install | Neural embedding consent is off | Device capture/test log |
| Consent off | No embedding network request; local search remains usable | Sanitized network/log evidence |
| Consent revoked during indexing | Requests cancel and persisted semantic vectors are removed | Device test report |
| Missing Auth token | Worker returns `401` | Worker response capture |
| Invalid App Check | Worker returns `401`/documented rejection | Worker response capture |
| Valid Auth + App Check | `/embed` succeeds and returns 768-dimension vectors | Sanitized response metadata |
| Account switch/sign-out | Old account data/tokens are not reused | Device test report |
| Token expiry/refresh | Re-authentication or refresh behaves as designed | Device test report |
| Offline Worker | Keyword/FTS/metadata search continues; neural failure is visible | Device test report |
| Backup/restore | Checksums, rollback, cancellation, and process death remain safe | Device test report |
| Clean signed release | Login, indexing, search, consent, restore, upgrade all pass | Artifact-linked acceptance report |

## P2 — release governance and documentation

- [ ] Refresh [`DRIVE_SEMANTIC_SEARCH_IMPLEMENTATION_GAP.md`](DRIVE_SEMANTIC_SEARCH_IMPLEMENTATION_GAP.md) with the final candidate SHA, actual Worker revision, and current check URLs.
- [ ] Ensure no document claims “production-ready” while any P0 item is open.
- [ ] Ensure all CPAS risk-register dates and notes describe current evidence, not an older commit.
- [ ] Confirm branch protection requires CPAS, build/quality, CodeQL, and instrumentation checks as intended. The release workflow must separately require successful exact-SHA nightly ORT evidence; do not require a nonexistent daily ORT status check.
- [ ] Keep PR #138 Draft until the release owner confirms every P0 acceptance condition.
- [ ] Only after evidence review, update risk statuses to `ci_verified`/`closed` according to the CPAS lifecycle; do not edit statuses to force a green result.

## Final release decision

The PR may leave Draft only when all of the following are true:

- [ ] `PROD-008`, `PROD-009`, and `PROD-010` have evidence-backed closure.
- [ ] CPAS verifier reports `PASS` on the exact release SHA.
- [ ] ORT analysis, scanning, evaluator and advisor pass on that SHA.
- [ ] The signed artifact, signer fingerprint, SBOM, provenance, privacy URL, Play configuration, Worker revision, and device report are cross-linked.
- [ ] No required check is skipped, soft-skipped, stale, or based on a different commit.
- [ ] Release owner and reviewer have approved the evidence package.

**Current decision remains: `BLOCKED — not production-ready`.**
