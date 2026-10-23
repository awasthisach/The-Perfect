# Audit remediation map (2026-10-22)

Source: executive summary for The-Perfect (PROD-008/009/010, ORT, CodeQL, signing).

**Production decision remains: BLOCKED.** Code/CI fixes do not close external gates.

## Already applied on this line (`fix/ort-scan-cache-and-vcs-curations` → phase3)

| Audit item | Status | Evidence |
|---|---|---|
| FOSSA retired; ORT is license gate | Done | `ci.yml` header; no FOSSA job |
| ORT scanner cache restore/save | Done | `actions/cache` on `~/.ort/scanner/` |
| ORT concurrency queue, no cancel-in-progress | Done | `group: ort-...`, `cancel-in-progress: false` |
| HTTPS rewrite for GitHub source downloads | Done | `git config url.https://github.com/.insteadOf` |
| Package VCS curations (OpenCensus, diffutils, sqlite4java, jaxb, codec, UTP) | Done | `.ort.yml` |
| Jacoco sources download hang curation | Done | commit on this branch (ARTIFACT origin) |
| `verify_ort_outputs.py` for analyzer/scanner/advisor/evaluator + SBOM | Done | CI step after ORT action |
| Fail-on violations/issues | Done | `fail-on: 'violations,issues'` |
| ORT PASS requires evaluator **and** advisor exit `0` | Done | `ort-status.json` recording |
| Conflicting advanced CodeQL workflow removed | Done | commit `d71825b` |
| CI assemble uses debug (no release keystore required for PR CI) | Done | `assembleDebug` in build job |
| DriveAuthEvents wire-up (no Activity-only callback) | Done on phase3 | `MainActivity` emits/collects `DriveAuthEvents` |
| Vault NIO publish | Done on phase3 line | `SecureVaultRepository` `Files.move` |

## Still failing / residual (must not claim PASS)

| Item | Why |
|---|---|
| **PROD-008** (#136) | ORT job still cancels/timeouts or incomplete SBOM on some runs; human package/license review of findings not recorded |
| **PROD-009** (#140) | Firebase App Check + Play Integrity + live Worker request — console + device only |
| **PROD-010** (#141) | OAuth consent scopes, privacy URL, Play signing FP, device matrix — console + device only |
| Build/Test/Lint on some SHAs | Lint or related hard gate still red — fix from lint report artifact |
| FOSSA App status contexts | External GitHub App may still post pending checks; disable in Settings |
| Release signing in CI | Intentionally not in PR CI; needs secrets + `release.yml` on exact SHA |

## Do not do (anti-patterns from audit)

- `\|\| true` on ORT evaluate/report to force green
- Blanket license allowlists
- Closing #136/#140/#141 without attached console/device evidence
- Merging while CPAS `production_status=BLOCKED`
- Treating empty advisor exit code as PASS (already rejected in `ort-status.json`)

## Owner checklist (external)

1. Wait for ORT job **success** on one SHA; download analyzer/scan/advisor/evaluation + `bom.cyclonedx.json` / `bom.spdx.json`; triage findings package-by-package (#136).
2. Firebase App Check register `com.vvf.smartmanager` + Play Integrity; prove 403 without token and 200 with valid Auth+App Check (#140).
3. OAuth Data Access scopes, privacy HTTPS, upload-key SHA-1/256 in Play + Firebase, physical device matrix on **signed** artifact (#141).
4. Disable FOSSA integration if required checks still wait on FOSSA.
5. Only then re-run CPAS; expect PROD-008/009/010 closed with evidence links in `08-risk-register.yaml`.
