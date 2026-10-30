# Phase 4 — Data / SQLCipher / Room

**Exit: PASS (with residuals)**  
**Branch:** `rebuild/stabilize-v1`

## Checklist

| Item | Result | Evidence |
|------|--------|----------|
| Only `net.zetetic:sqlcipher-android` | PASS | `gradle/libs.versions.toml` → `sqlcipher-android`; no `android-database-sqlcipher` |
| `loadLibrary` before encrypted open | PASS | `VVFApplication`: `System.loadLibrary("sqlcipher")` then `VVFDatabase.buildEncryptedDatabase` |
| Support factory | PASS | `VVFDatabase` uses `net.zetetic.database.sqlcipher.SupportOpenHelperFactory` via wiping factory |
| Legacy fixture path | PASS* | Hard-gate CI generates `legacy-4.5.4.db` into androidTest assets; compat test opens asset |
| Compat tests correct + wrong passphrase | PASS | `SqlCipherCompatibilityTest` |
| Room migrations | PASS | `version = 2`, `MIGRATION_1_2` in `VVFDatabase.kt` |
| Schema JSON in git | RESIDUAL | Only `schemas/README.md`; JSON must come from KSP |
| FileProvider narrow paths | PASS | `app/src/main/res/xml/file_paths.xml` — share/exports only, no `path="."` |

\*Fixture not binary-committed; generation is CI-side.

## Verify

- CI: `assembleDebug` / unit / lint green on PR #187 heads
- SQLCipher Instrumented (hard) success on recent heads
- Do not invent schema JSON
