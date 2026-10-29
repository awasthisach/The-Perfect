# Architecture contracts (freeze)

**Branch:** `rebuild/stabilize-v1`  
**Baseline SHA:** `5af6b9e58d85ff3a4e39ed93480b5a6f4e262bbc` (main after #181)  
**Rule:** Do not violate these contracts without an explicit ADR and a single integration PR.

## Product

- Offline-first Android file manager with encrypted vault, portable backup, optional Drive sync.
- No new product features on this branch until residual security/CI slices close.

## Module dependency

```
app → feature/* → core/domain → core/data → core/{database,security,cloud-*}
                ↘ core/background, core/plugin-spi
plugins/* implement plugin-spi; app wires implementations.
```

- Features must not open SQLCipher databases or Keystore aliases directly.
- `core/database-compat` is for migration/compat tests only.

## Dependency injection

- **Chosen style for stabilize:** manual composition (`AppCompositionRoot` + `VVFApplication`).
- Hilt may remain in the version catalog for a later migration, but **do not** wire a second parallel graph on this branch.
- All application-scoped services are constructed once in `VVFApplication.onCreate` (or pure factories in `AppCompositionRoot`).

## Crypto & vault

- SQLCipher database passphrase: Android Keystore–wrapped, separate from vault file key.
- Vault file key: device-bound Keystore AES-GCM (`VAULT_KEY_ALIAS` family).
- PIN lockout: authenticated blob (`vault_lockout_state_v2` + IV), time base `SystemClock.elapsedRealtime()` — not wall-clock alone.
- Portable backup: passphrase-derived PBKDF2 + AES-GCM envelope (`PortableBackupCrypto`); clear passphrase buffers after use.
- Never export or restore legacy `vault_auth.json` as authority.
- Biometric unlock: one-use proof bound to helper-issued cipher; no replay.

## Startup order

1. Construct `CryptoSecurityManager`.
2. Recover interrupted restore journals (filesDir first, then legacy cacheDir).
3. Outside JVM unit tests: install Firebase App Check (Play Integrity) — **fail closed** (no catch-and-continue).
4. JVM unit tests: in-memory Room. Device/CI: `System.loadLibrary("sqlcipher")` then encrypted Room.
5. Wire repositories / use cases / WorkManager bootstrap.

## FileProvider

- Only narrow paths under `share/` and `exports/` for cache, files, and external-files.
- No `path="."` on internal roots.

## Backup / restore

- Portable archive requires policy-compliant passphrase.
- Restore is transactional: snapshot + journal; startup recovery before DB open.
- Fail closed on incomplete/inconsistent manifests and orphaned swap artifacts.
- Unattended cloud backup of vault material stays fail-closed until a reviewed unattended key strategy exists.

## CI / release honesty

- Daily PR: unit tests + lint + assemble (and hard SQLCipher instrumented when workflow applies).
- Production PASS requires non-empty evidence ledger with non-`local-*` CI evidence and ORT SBOM artifacts present and `ort-status.json` == PASS.
- Do not weaken gates to obtain a green check.
- README and store copy must not claim production-ready while any open P0 residual remains.

## Process

- **One** integration branch: `rebuild/stabilize-v1`.
- One vertical slice at a time (see `docs/STABILIZE_RESIDUALS.md`).
- Full-file local edits for large sources (especially `VVFApplication.kt`); never push stubs.
- Conventional commits; minimal diffs.
