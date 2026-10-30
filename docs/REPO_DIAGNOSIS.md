# Repository diagnosis (2026-10-30)

## Verdict on process

**Yes — diagnose before more thrash is correct.** Industry practice (trunk-based development, Google Eng Practices / DORA-style reduce WIP, GitHub Actions concurrency hygiene):

1. **One integration line** toward `main`
2. **Close or supersede** parallel long-lived branches
3. **Fail-closed** security/compliance gates without multi-hour PR waits
4. **Separate** owner-console work (OAuth, Play, secrets) from code CI green

## Last ~12 CI runs on `rebuild/stabilize-v1`

| Run | SHA tip | Conclusion | Cause class |
|-----|---------|------------|-------------|
| 1935 | b1c116d6 | in_progress | — |
| 1934 | d658f442 | **cancelled** | Superseded by next push (concurrency) |
| 1933 | dbafc867 | **success** | MainActivity factory fix |
| 1932 | ada97841 | **failure** | Kotlin: wrong `ExplorerViewModel.provideFactory` args |
| 1931 | 0fce2876 | **failure** | Same MainActivity wiring (pre-fix) |
| 1930–1924 | rapid docs/UI | **cancelled** | Push storm; cancel-in-progress |

**CPAS runs:** mostly **cancelled** (waited for ORT) or **failure** with `production_status=BLOCKED` (no ORT SBOMs). After cpas-gate fix: fast **BLOCKED** is expected and honest until ORT PASS on that SHA.

### Failure taxonomy (not “app is broken”)

| Class | Share | Fix |
|-------|-------|-----|
| A. Cancelled by newer commit | Majority | Batch commits; freeze during long jobs |
| B. Compile API mismatch (MainActivity) | Real red | Fixed in `dbafc867` |
| C. CPAS BLOCKED / wait cancel | Process | No long ORT wait on PR; ORT once on freeze SHA |
| D. External (OAuth, Play, device) | Issues #136/#140/#141 | Owner console + device; not more Kotlin churn |

## Open PRs (~24)

**Keep:** [#187](https://github.com/awasthisach/The-Perfect/pull/187) `rebuild/stabilize-v1` → `main` (only integration PR).

**Supersede / do not merge into main** (overlap or stale base):

| PR | Why open | Disposition |
|----|----------|-------------|
| 156, 169–172 | App Check / Drive / DI / passphrase | Ported or superseded on stabilize |
| 154, 160, 138 | Old release stacks | Stale base SHAs; conflict factory |
| 167, 176, 178–180 | CI/SQLCipher/CPAS iterations | Largely absorbed; close as superseded |
| 182–186 | Parallel Phase 3 restore/PIN | Risk of double-fix; review only if #187 missing a delta |
| 165, 162 | Docs | Optional cherry-pick; do not block |
| Dependabot 173–175 | Dep bumps | After stabilize green, separate PR |

## Open issues (3) — pending for the right reason

| Issue | Why still open |
|-------|----------------|
| **#136 ORT** | Needs completed ORT + human license review on **one** candidate SHA |
| **#140 App Check / Worker** | Firebase console registration + device request proof |
| **#141 OAuth / privacy / signing / device** | Google Cloud, Play, privacy URL, signed AAB, physical acceptance |

These are **not** fixed by opening more code PRs. Closing them without evidence would violate fail-closed release policy.

## Authoritative standards mapped to this repo

| Topic | Standard / practice |
|-------|---------------------|
| Single integration path | Trunk-based / short-lived branches (DORA, Google Eng Practices) |
| SBOM / dependency review | ORT / NTIA SBOM; fail closed if missing |
| App integrity | Firebase App Check + Play Integrity docs |
| OAuth | Google OAuth 2.0 / restricted scopes guidance |
| Android release | Play App Signing; no debug keystore for production |
| CI required checks | Must reflect real product readiness; avoid hour-long PR waits (Actions concurrency best practice) |

## Remediation plan (ordered)

1. **Freeze** `rebuild/stabilize-v1` except compile/test reds and ORT evidence attach.
2. Confirm CI **success** on head (post–`dbafc867`).
3. **One** `workflow_dispatch` Nightly ORT on freeze SHA; merge SBOM auto-PR if any.
4. CPAS `production_status=PASS` only after SBOMs in tree for that SHA.
5. Close obsolete open PRs as **superseded by #187** (bulk).
6. Owner: secrets, #140/#141 console + device evidence.
7. Only then undraft #187 / release.yml.

## Explicit non-goals

- Do not merge 20+ historical PRs into main.
- Do not weaken CPAS to force green.
- Do not claim SHIP while #136/#140/#141 lack evidence.
