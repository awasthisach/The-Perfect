# Audit response (2026-09-04)

Cross-check of external reverse-engineering report against live `main` / PR branch.

## Fossabot

- No open fossabot PRs; recent PR activity is Dependabot only.
- Treat as **inactive / closed** for process purposes. If the GitHub App still appears under Settings → Applications, uninstall for hygiene (API cannot confirm app install with this token).

## Claims rejected or already fixed

| Claim | Live truth |
|-------|------------|
| `cancel-in-progress: true` on main | Already `${{ github.event_name == "pull_request" }}` |
| Downgrade hilt-navigation-compose to 1.2.0 | **Wrong** — 1.4.0 is newer; keep |
| SQLCipher single version 4.5.7 | Dual pin 4.5.4 fixture + 4.5.6 android is intentional compat gate |
| Kotlin 2.2.10 → 2.0.21 | Downgrade rejected; stay on project pin |
| memoryKeyMap is production leak | Production **refuses** in-memory keys when Keystore required; map is JVM-test fallback only |

## Applied safe fixes (this PR)

- OkHttp **4.10.0 → 4.12.0** (stable 4.x, addresses known 4.10 issues without OkHttp 5 break)
- FTS comment **FTS4 → FTS5** aligned with CI `--enable-fts5`
- `CryptoSecurityManager.clearInMemoryKeysForTests()` for unit-test isolation

## Deferred (real work, not false urgency)

- Full Hilt migration (large; composition root still valid)
- Live Drive E2E (WAIVER-021)
- SQLCipher major bumps (fixture + instrumented gate required)
