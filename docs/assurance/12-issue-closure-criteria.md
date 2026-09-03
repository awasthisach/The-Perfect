# Issue closure criteria (evidence-backed)

## #3 — Storage boundary + no demo-data mutation
- [ ] Full-green CI run ID attached (unit + lint + assemble + SQLCipher instrumented)
- [ ] `cpas-status.json` artifact shows PASS for storage checks
- [ ] EVID-001 / EVID-002 present in `evidence-ledger.json`
- **Status:** code closed (PROD-001/004); close GitHub issue when next full-green run is linked in a comment

## #20 — PROD-007 storage permission / Play policy
- [ ] Permission justification documented (MANAGE_EXTERNAL_STORAGE + READ_MEDIA_* rationale)
- [ ] Runtime `StoragePermissionGate` / `StorageAccessPolicy` unit tests green
- [ ] PROD-007 status `closed` in risk register
- **Status:** PROD-007 closed in register; add `docs/assurance/permission-justification.md` if missing, then close issue

## #21 — PROD-002/003 durable upload + fail-closed restore
- [x] Unit evidence EVID-006 / EVID-007 on CI 33790505509
- [x] Formal waiver `WAIVER-021-CLOUD-E2E` (expires 2026-12-04)
- [ ] Private-runner live Drive E2E when secrets available (monthly)
- **Status:** register closed with residual medium; keep issue open until E2E or explicit product decision to accept residual through release
