# Phase progress (rebuild/stabilize-v1)

**Ref:** `rebuild/stabilize-v1`  
**Evidence SHA (at write):** see PR #187 head  
**Overall release:** **NO-SHIP** until same-SHA ORT PASS + CPAS production PASS + device/secrets

| Phase | Name | Exit |
|-------|------|------|
| 3 | Vault / PIN / portable backup | **PASS** — `docs/PHASE3_EXIT.md` |
| 4 | Data / SQLCipher / Room | **PASS** with residuals (below) |
| 5 | CI/CD fail-closed | **PASS** (gates honest in workflow files) |
| 6 | Product honesty & store | **PASS** with residual (device/Play external) |
| 7 | Release candidate packaging | **FAIL / NO-SHIP** — ORT SBOMs absent on tree |

## Phase 4 residuals (documented, not ignored)

1. **Room schema JSON** not committed under `core/database/schemas/` — must be produced by `./gradlew :core:database:kspDebugKotlin` and committed; do not hand-write.
2. **Legacy 4.5.4 fixture** is **generated in** `weekly-instrumented` / hard-gate CI into `androidTest/assets/legacy-4.5.4.db`, not stored as a binary in git. Prefer pinning SHA-256 of that artifact in a future commit after one green hard-gate log.

## Phase 7 blockers

- `docs/assurance/ort-bom.cyclonedx.json` / `ort-bom.spdx.json` / `ort-status.json` missing → CPAS `production_status` BLOCKED
- Device acceptance + Action secrets unverified
