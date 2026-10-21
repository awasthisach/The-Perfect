# Production status (evidence-based)

**Decision: BLOCKED — not production-ready**

Updated: 2026-10-21  
Current PR #153 head at update time: `234dc7b748d882d16169ad10383cc0713ca09442`

## Source changes made; current-head CI is pending

| Item | Evidence | Validation |
|------|----------|------------|
| API 24 vault restore publication | Commit `75b3978` replaces API-26 `java.nio.file.Files.move` usage with Android `Os.link` no-clobber publication | Build/Test/Lint passed on prior SHA `75b3978`; current-head rerun pending |
| Shared-inode plaintext cleanup | Commit `7bae68e` retries temp-link removal and prevents the outer failure handler from shredding a destination that shares the temp inode | Current-head CI pending |
| Drive OAuth callback lifecycle | Commits `2a820be`, `3d72d27`, `e6ee107`, `234dc7b` remove the Activity/Application-held callback and deliver the result through a buffered channel to a retained CloudViewModel | Current-head CI pending; do not claim rotation acceptance before a real-device test |
| ORT workflow | `.github/workflows/ci.yml` and `.github/workflows/release.yml` invoke ORT; the tree contains no `.fossa.yml` and no FOSSA workflow job | ORT result on current head not yet available |

## Current external and assurance blockers

| Blocker | Issue | Required evidence |
|---------|-------|------------------|
| PROD-008: license compliance | #136 | Complete ORT Analyzer, ScanCode, Evaluator, Advisor and SBOM/report outputs for the exact candidate SHA; package-by-package license/source review; no blanket suppressions |
| PROD-009: App Check / Worker | #140 | Firebase App Check registration and signing fingerprint; invalid-token rejection; successful physical-device request with separate Firebase Auth and App Check tokens |
| PROD-010: OAuth / privacy / signing / device | #141 | Correct OAuth scopes and consent configuration, reviewed/published privacy policy, protected signed AAB/APK, signer/SBOM/provenance evidence, and physical-device acceptance |
| FOSSA App status contexts | Settings | GitHub commit-status API still reports `Dependency Quality`, `License Compliance`, and `Security Analysis` with description “FOSSA is analyzing this commit.” These are externally emitted statuses, not workflow jobs in this branch. The connected GitHub integration cannot inspect/change branch protection (API returned 403). Disable/uninstall the FOSSA GitHub App for this repository; if any of these contexts are configured as required checks, replace them with the ORT gate in branch protection. |
| Current-head validation | PR #153 | Workflow run `39563277551` (CI), `39563277608` (CPAS), `39563277566` (SQLCipher), and `39563277473` (Worker) were queued/in progress when this file was updated. Their outcomes must be rechecked on the current head. |

## Verified facts and limits

- On prior SHA `75b3978f3d0b94436f83aeee2d8cf5812d73cc7c`, Build/Test/Lint/Debug APK succeeded in run [39561265271](https://github.com/awasthisach/The-Perfect/actions/runs/39561265271). The emulator in that job did not boot, so its in-job SQLCipher instrumentation was skipped; the separate weekly hard gate passed on that SHA.
- CPAS run [39562962959](https://github.com/awasthisach/The-Perfect/actions/runs/39562962959) failed on SHA `7bae68e`; the high-severity risk register still has PROD-008 `triaged`, PROD-009 `repair_implemented`, and PROD-010 `triaged`. These statuses must not be promoted without the required evidence.
- Worker build success is not proof of deployed Worker authentication or physical-device acceptance.
- Historical FOSSA inventories remain archived as evidence only. Scanner migration does not resolve their findings; the current ORT scan and human review are required.

## Required sequence

1. Recheck Android CI, ORT, CPAS, Worker and SQLCipher results on the latest PR #153 head.
2. Complete and review current-SHA ORT outputs for #136.
3. Complete Firebase/App Check and physical-device acceptance for #140.
4. Complete OAuth, privacy, billing/provider review, signing and device acceptance for #141.
5. Disable the external FOSSA GitHub App/status reporting and verify only intended checks remain.
6. Produce a signed AAB from the final SHA, verify signer/fingerprint/SBOM/provenance, and test that exact artifact on a clean supported device.
7. Only then can CPAS reach `production_status == PASS`.

Do not merge PR #153 to `main` while CPAS is blocked or current-head CI/ORT is incomplete.
