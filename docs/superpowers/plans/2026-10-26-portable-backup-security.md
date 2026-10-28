# Portable Backup and Vault-Key Security Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans. Steps use checkbox syntax.

**Goal:** Make backups restorable across devices using a user passphrase without exporting vault PIN metadata, while retaining PIN-gated vault access and fail-closed restore.

**Architecture:** A versioned, streaming AES-256-GCM envelope derives a key from a user passphrase using PBKDF2-HMAC-SHA256. The archive contains only the minimal DB/vault key material needed for restore inside that envelope; restore validates everything in isolated staging, rewraps keys under the destination device's Keystore and local PIN, and commits the DB/vault/key state transactionally. Legacy device-bound backups remain explicitly same-device only and never import vault authentication metadata.

**Tech Stack:** Kotlin, Android Keystore, JCA AES/GCM, PBKDF2-HMAC-SHA256, Room/SQLCipher, coroutines, JUnit/Robolectric, GitHub Actions.

**Spec:** `docs/superpowers/specs/2026-10-26-portable-backup-security-design.md`

## Global Constraints

- Work on an explicit feature branch; never write directly to `main`.
- Do not claim a test passes without its exact-SHA CI result.
- Never include PIN hashes, PIN salts, decoy PIN data, biometric flags, lockout state, or raw key material in remote metadata or logs.
- Use AES-256-GCM with a random 12-byte nonce and 128-bit tag; use PBKDF2-HMAC-SHA256 with 600,000 iterations and a random 16-byte salt.
- New archive decrypt failures must fail closed; never downgrade after wrong-passphrase or authentication-tag failure.
- Existing vault/DB data must remain recoverable or the migration must abort without modifying live state.
- Keep DB and vault keys separate; the SQLCipher DB passphrase must be rewrapped by the destination device's Keystore.
- Legacy archives are not portable and their `vault_auth.json` must never be imported.

## Review Focus

- Wrong passphrase, modified header/salt/nonce/ciphertext, and truncation: no plaintext destination is retained.
- Backup/restore cancellation or process failure: staging files and mutable secret buffers are cleaned up.
- Process death is a separate threat from `finally`; plaintext ZIPs and snapshot staging can survive a crash. Do not claim this risk is solved by best-effort deletion. Prefer a stream-based snapshot-to-encrypted-envelope pipeline; until then, use app-private staging, minimize lifetime, clean stale staging on startup, and record residual risk.
- The scheduled `CloudBackupBootstrap` has no secure passphrase input. Do not persist a passphrase in WorkManager input, preferences, logs, or cloud metadata. Until a secure unattended key-wrapping strategy is designed and tested, scheduled jobs must not silently create backups under a different format or report a portable backup as completed.
- Existing legacy vault files mixed with newly PIN-wrapped vault files: migration does not silently corrupt or strand either format.
- Restore failure after key import: live DB, live vault, and current key wrapping are rolled back consistently.
- Repeated or concurrent backup/restore operations: archive mutex and state transitions prevent mixed key/data snapshots.

---

### Task 1: Portable backup envelope

**Files:**
- Create: `core/security/src/main/java/com/vvf/smartmanager/core/security/PortableBackupCrypto.kt`
- Create: `core/security/src/test/java/com/vvf/smartmanager/core/security/PortableBackupCryptoTest.kt`

**Interfaces:**
- `encryptFile(source: File, destination: File, passphrase: CharArray)`
- `decryptFile(source: File, destination: File, passphrase: CharArray)`
- Envelope header is magic `VVFB`, version byte `1`, 16-byte salt, 12-byte nonce; authenticate the full header as GCM AAD.
- Decrypt into a sibling temporary file and atomically rename only after GCM authentication succeeds.

- [ ] Write tests for round-trip, wrong passphrase, header tampering, ciphertext tampering, truncation, and no partial plaintext output.
- [ ] Verify the red test result on the branch.
- [ ] Implement the minimal envelope/KDF/streaming code.
- [ ] Run security-module unit tests and full `testDebugUnitTest`, `lintDebug`, `assembleDebug`.

### Task 2: Archive integration and auth-metadata removal

**Files:**
- Modify: `core/domain/src/main/java/com/vvf/smartmanager/core/domain/backup/ArchiveService.kt`
- Modify: `core/domain/src/main/java/com/vvf/smartmanager/core/domain/restore/impl/CryptoBackupDecryptor.kt`
- Modify: `core/domain/src/main/java/com/vvf/smartmanager/core/domain/restore/impl/LocalRestoreApplier.kt`
- Modify: `app/src/main/java/com/vvf/smartmanager/AppCompositionRoot.kt`
- Modify: `core/domain/src/main/java/com/vvf/smartmanager/core/domain/CloudSyncUseCase.kt`
- Modify: `feature/cloud/src/main/java/com/vvf/smartmanager/feature/cloud/CloudUiState.kt`
- Modify: `feature/cloud/src/main/java/com/vvf/smartmanager/feature/cloud/CloudViewModel.kt`
- Modify: `feature/cloud/src/main/java/com/vvf/smartmanager/feature/cloud/CloudScreen.kt`
- Add/update domain and UI tests as required.

**Interfaces:**
- Cloud backup requires an explicit passphrase for new portable archives.
- Cloud restore requires the passphrase and passes it through `RestorePipeline` to `BackupDecryptor`.
- Passphrases are transient, cleared after the operation, and never persisted or logged.
- Do not enable the new envelope for production archive/restore until the DB passphrase and vault data key are safely bundled/re-wrapped; wrapping only the outer ZIP does not make its contents portable.
- Explicitly define manual backup UX separately from unattended WorkManager backup. The latter remains disabled/fail-closed until it has a secure non-plaintext key strategy.
- New archive metadata never contains `vault_auth.json`; restore ignores it in legacy archives.

- [ ] Write failing archive/restore tests proving auth metadata is excluded and wrong passphrase fails closed.
- [ ] Verify the red tests on the branch.
- [ ] Integrate the portable envelope; remove export/import of vault-auth metadata from backup/restore.
- [ ] Run full unit/lint/build CI on the exact SHA.

### Task 3: PIN-wrapped vault data key and compatibility migration

**Files:**
- Modify: `core/security/src/main/java/com/vvf/smartmanager/core/security/CryptoSecurityManager.kt`
- Modify: `core/security/src/test/java/com/vvf/smartmanager/core/security/CryptoSecurityManagerTest.kt`
- Modify: vault/domain use cases and repository paths found by the tree audit.
- Modify: vault restore staging to carry the data key only inside the passphrase-encrypted archive.

**Interfaces:**
- Generate a random vault data key; wrap it with a PIN-derived key using the existing 600,000-iteration PBKDF2-HMAC-SHA256 policy and authenticated encryption.
- Keep the database Keystore alias separate from vault key material.
- Real and decoy PIN verification must not unlock the wrong data-key domain.
- Existing IV-prefixed files remain readable via a controlled legacy path; new writes use an explicit versioned header.

- [ ] Write failing tests for PIN-wrapped key round-trip, wrong PIN, lockout enforcement, and legacy file compatibility.
- [ ] Verify red tests on the branch.
- [ ] Implement versioned key wrapping and a non-destructive compatibility path.
- [ ] Verify no API permits vault-file encryption/decryption without an active authorized session.

### Task 4: Portable DB/vault key bundle and transactional restore

**Files:**
- Modify: `core/domain/src/main/java/com/vvf/smartmanager/core/domain/backup/ArchiveService.kt`
- Modify: `core/domain/src/main/java/com/vvf/smartmanager/core/domain/restore/impl/CryptoBackupDecryptor.kt`
- Modify: `core/domain/src/main/java/com/vvf/smartmanager/core/domain/restore/impl/LocalRestoreApplier.kt`
- Modify: `app/src/main/java/com/vvf/smartmanager/AppCompositionRoot.kt`
- Add/update tests for key-store rollback and new-device restore.

**Interfaces:**
- Archive the SQLCipher passphrase and vault data key only inside the portable encrypted envelope.
- Restore validates the key bundle and rewraps the DB passphrase under the destination Keystore before opening the restored DB.
- A destination vault key is not usable until the user establishes a new local PIN; imported auth metadata is never trusted.
- Rollback restores the previous DB passphrase and vault key wrapping if any apply step fails.

- [ ] Write failing tests for cross-device restore, new local PIN setup, and rollback after key import failure.
- [ ] Verify red tests on the branch.
- [ ] Implement staged key rewrapping and transactional apply/rollback.
- [ ] Run all unit tests, lint, assemble, SQLCipher instrumented tests, and CPAS checks on the same SHA.
- [ ] Update assurance docs and release notes only from verified evidence.

### Task 5: Final verification and review

- [ ] Run `python3 tools/audit/cpas_verify.py --root .` and confirm production status is not PASS when ORT/SBOM evidence is missing.
- [ ] Run unit tests, lint, debug/release assembly, SQLCipher instrumentation, and ORT/SBOM validation on one exact SHA.
- [ ] Review the diff for plaintext key/passphrase handling, logs, downgrade paths, and stale vault-auth export/import.
- [ ] Keep the branch and PR draft until all security gates pass; no P0 may be waived implicitly.
