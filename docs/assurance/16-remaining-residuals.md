# Remaining residuals (honest)

## Fixed this branch

- Non-Drive cloud SPI drivers are **fail-closed stubs** (no fake uploads/lists).
- Vault PIN **Keystore-wrapped** prefs exported as `vault_auth.json` inside encrypted backup; restored on apply.
- Emulator instrumented CI condition fixed; unit/lint/assemble hard gates.

## Still residual (not false-green)

| Item | Why |
|------|-----|
| SQLCipher DB **cross-device** restore | DB file is bound to device Keystore passphrase; portable re-key not implemented |
| Google Drive **resumable** upload | Multipart only; large/flaky networks P1 |
| Real OneDrive/Dropbox/S3/NAS | SPI stubs until dedicated integrations |
| SSD multi-pass shred | Best-effort overwrite; media physics limits |
| `originalPath` in vault rows | Inside SQLCipher; hash optional privacy hardening |

Google Drive path remains the production cloud path for backup/restore.
