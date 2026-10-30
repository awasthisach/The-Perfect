# Phase 3 exit — vault / PIN / portable backup

**Status: PASS (source + unit/instrumented evidence)**  
**Evidence SHA:** `9e1e561a5fb6fd7c280ed62dc37b2521204b7788` (`rebuild/stabilize-v1`)  
**Date:** 2026-10-30

Phase 3 is the **security vertical** for vault PIN lockout and portable backup/restore.
It is **not** the full production ship gate (ORT/CPAS/device remain Phase 5–7).

## Exit criteria checklist

| Criterion | Result | Evidence |
|-----------|--------|----------|
| PIN lockout not wall-clock-only | PASS | `CryptoSecurityManager` uses `SystemClock.elapsedRealtime()` for lockout deadlines |
| Lockout state authenticated | PASS | AES-GCM blob keys `vault_lockout_state_v2` + `_iv`; legacy prefs removed on write |
| Legacy lockout migration fail-closed | PASS | Incomplete/invalid legacy state → `SecurityException` or forced max delay; unit tests on branch |
| Portable backup passphrase envelope | PASS | `PortableBackupCrypto` PBKDF2-HMAC-SHA256 + AES-GCM; `ArchiveService` requires ≥12 char passphrase |
| No vault_auth.json as backup authority | PASS | #181 merge on main; export/import paths removed |
| Unit tests green | PASS | CI Build/Test/Lint **success** on `9e1e561a` (run 40924744027) |
| SQLCipher instrumented hard gate | PASS | **success** on `9e1e561a` (run 40924744044) |

## Explicit non-claims

- ORT SBOM on this SHA: **not present** → CPAS `production_status` remains BLOCKED.
- Physical device vault/backup acceptance: **not evidenced here**.
- Therefore: **Phase 3 PASS**, overall release still **NO-SHIP**.

## Why it felt “not improving”

ORT was attached to every PR push, ran ~6 hours, then **cancelled** when the next commit arrived. That is process thrash, not a Phase 3 code regression. ORT is now schedule/`workflow_dispatch` only so a single candidate SHA can finish.
