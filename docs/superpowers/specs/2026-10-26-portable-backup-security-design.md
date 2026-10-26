# Portable Backup and Vault-Key Security Design

**Date:** 2026-10-26  
**Baseline:** `main` at `f9b6d95c41963a885b212f5eccec2da97e80b231`  
**Scope:** Phase 3 security remediation for vault keys and backup/restore.

## Intent

Make encrypted backups recoverable on a different device using a user-supplied backup passphrase, while ensuring vault access still requires a PIN or an approved biometric proof. Backup files must not export vault PIN hashes, salts, decoy PIN material, biometric settings, or lockout state.

## Evidence from the current implementation

- `CryptoSecurityManager.encryptFile/decryptFile` use the device-bound `vvf_vault_master_key_v1` Keystore alias.
- `ArchiveService.createArchive` uses those methods for the outer `.vvfbak` file and writes `vault_auth.json` from `exportVaultAuthMetadata()`.
- `CryptoBackupDecryptor` decrypts through the same device-bound key.
- `LocalRestoreApplier` imports `vault_auth.json` before replacing the live DB/vault; auth preferences are not part of rollback.
- `LocalRestoreApplier` documents that both the SQLCipher DB and vault data are device-bound, so cross-device restore is not currently supported.

## Goals

1. Use a portable, versioned backup envelope derived from a user passphrase with PBKDF2-HMAC-SHA256 (600,000 iterations), a random 16-byte salt, AES-256-GCM, and a random 12-byte nonce.
2. Fail closed for wrong passphrases, corrupted headers, truncated data, and GCM authentication failures; never leave a partial plaintext output in the restore staging area.
3. Do not write vault PIN/decoy PIN hashes, salts, biometric flags, or lockout state into new archives. Never import authentication metadata from legacy archives.
4. Include the minimum key material needed to restore the SQLCipher database and vault files only inside the passphrase-encrypted envelope; on restore, rewrap key material under the destination device's Keystore and require a new local vault PIN before exposing restored vault contents.
5. Make restore key updates transactional with the DB/vault swap and rollback. Preserve legacy same-device restore where safely identifiable, but never describe legacy device-bound archives as portable.
6. Keep passphrases and plaintext key material out of logs, persistent UI state, remote metadata, and unencrypted staging artifacts where feasible; clear mutable buffers and remove temporary files on all paths.

## Approaches considered

- **Outer passphrase encryption only:** rejected because the embedded SQLCipher DB and vault files remain encrypted with device-bound keys and still cannot be restored on a different device.
- **Portable encrypted envelope plus a minimal key bundle and local rewrapping:** recommended. It addresses portability without exporting PIN metadata; restore sets up a new local PIN and rewraps the vault data key for the destination device.
- **Keep current device-bound encryption and document the limitation:** rejected because it does not satisfy portable backup and can mislead users into believing backups are recoverable.

## Compatibility and safety

- New archives use a magic header and explicit format version so the decryptor can distinguish them from the legacy IV-prefixed format.
- Legacy archives may be restored only through the existing device-bound path where supported; `vault_auth.json` must be ignored and must never overwrite current authentication preferences.
- Do not silently fall back from a portable decryption/authentication failure to a legacy decrypt path.
- A restore must not replace live DB/vault data or authentication/key state until the staged archive, manifest, and key bundle have been fully validated.
- If a safe key migration for existing vault files cannot be proven, fail closed and retain the original live data rather than attempting an in-place destructive conversion.

## Acceptance tests

- Portable crypto round-trip succeeds with a different crypto-manager/device context.
- Wrong passphrase, header tampering, nonce/salt tampering, ciphertext tampering, and truncation all fail and leave no plaintext destination.
- New archive contains no `vault_auth.json` or PIN/decoy-auth fields.
- Restored DB passphrase is locally rewrapped and opens the restored DB; rollback restores the original passphrase if apply fails.
- Vault files remain inaccessible before a new local PIN is configured and decrypt only after successful PIN unlock.
- Failed PIN attempts cannot be reset or bypassed through another public unlock path; biometric unlock requires the cryptographic proof callback.
- Existing same-device backup behavior is covered by explicit legacy fixtures and never silently misclassified as portable.
