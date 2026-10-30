# Remaining work (post Phase 3)

**Candidate branch:** `rebuild/stabilize-v1`  
**PR:** #187 (keep **draft** until ship checklist is complete)  
**Phase 3 (vault/PIN/portable backup):** PASS — see `docs/PHASE3_EXIT.md`

## Done on integration line

- Architecture contracts frozen
- Single DI (manual composition; unused Hilt removed from app)
- Phase 3 security vertical + unit/SQLCipher hard gate green on prior head
- ORT no longer on every PR push (schedule + `workflow_dispatch` only)
- Release/CPAS remain fail-closed without ORT SBOMs in tree

## In flight

- Full ORT scan via `workflow_dispatch` on candidate SHA (multi-hour).
  When PASS: workflow should open an auto PR adding:
  - `docs/assurance/ort-bom.cyclonedx.json`
  - `docs/assurance/ort-bom.spdx.json`
  - `docs/assurance/ort-status.json`
  Merge that into `rebuild/stabilize-v1`, then confirm CPAS `production_status` on the new SHA.

## Owner-only (cannot be finished by repo bots alone)

1. **GitHub Actions secrets** (Settings → Secrets; never paste values into chat):
   - `ANDROID_KEYSTORE_BASE64`
   - `ANDROID_KEYSTORE_PASSWORD`
   - `ANDROID_KEY_ALIAS`
   - `ANDROID_KEY_PASSWORD`
   - `GOOGLE_SERVICES_JSON_BASE64`
2. **Device acceptance** (record pass/fail):
   - Vault PIN lockout
   - Portable backup device A → restore device B
   - App Check tokens in Firebase console
   - Real Google Drive sign-in smoke
3. **Privacy / Play:** live policy URL + Data safety form alignment
4. After same-SHA: CI + SQLCipher + ORT PASS + CPAS PASS + device notes → undraft #187 → merge → `release.yml`

## Explicit non-actions

- Do not merge historical parallel fix branches into `main`
- Do not reintroduce Hilt mid-stream
- Do not weaken ORT/CPAS to force green
- Do not claim production-ready in README until the ship checklist is complete

## Verdict policy

| State | Verdict |
|-------|---------|
| Phase 3 only | Phase complete; **NO-SHIP** |
| + ORT/CPAS PASS same SHA | Release-candidate code; still **NO-SHIP** without device/secrets |
| + secrets + device + store honesty | **SHIP** discussion allowed |
