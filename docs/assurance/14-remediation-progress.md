# Remediation progress — production baseline

Updated: 2026-09-04

## Production-grade baseline: COMPLETE (P0/P1)

| Area | Status |
|------|--------|
| Branch protection on main | ON |
| CI serial on main / cancel on PR only | done |
| Bot noise cleaned; Dependabot majors ignored | done |
| Stale branches pruned | done |
| CPAS PASS 12/12; evidence ledger | done |
| Storage fail-closed; permissions | done |
| Cloud unit contracts + WAIVER-021 | done |
| Branding; scaffold removed; FOSSA | done |
| Release: signed + SBOM + SLSA attest | done |
| Privacy map; threat model; perf budgets | done |
| CODEOWNERS | done |

## Honest residual

| Item | Handling |
|------|----------|
| Live Google Drive E2E | WAIVER-021 until private-runner evidence |
| Device performance numbers | Budgets in performance-budgets.md |
| fossabot GitHub App | Disable in Settings if still installed |

Definition of done: offline-first product + fail-closed security + evidence-backed CPAS + protected main + signed release path.
