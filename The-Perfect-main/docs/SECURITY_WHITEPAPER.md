# VVF Smart Manager — Security Whitepaper

## Zero-Knowledge Architecture

- Database passphrase is generated on-device (256-bit), encrypted with Android Keystore (AES-256-GCM), and never leaves the device.
- Vault files are streamed with AES-256-GCM; plaintext is never written to disk.
- PIN is hardened with **PBKDF2-HMAC-SHA256 at 600,000 iterations** (OWASP 2023 recommended minimum).
- Decoy / plausible-deniability PIN supported.
- `android:allowBackup="false"` — no cloud backup of app data.

## Cryptography

| Asset | Algorithm | Notes |
|-------|-----------|-------|
| SQLCipher DB | AES-256 (SQLCipher) + Keystore-wrapped passphrase | Hardware-backed when available |
| Vault files | AES-256-GCM streaming (64 KB buffers) | IV prepended |
| PIN | PBKDF2-HMAC-SHA256, 600,000 iterations, 256-bit | Salt + Keystore-encrypted hash |
| Master keys | Android Keystore AES-256-GCM | User-auth gated for vault content key |

## Biometrics

Vault content key may require `BIOMETRIC_STRONG` or device credential (API 30+).
Devices without strong biometrics can still unlock with PIN.

## Network / Telemetry

- Core vault and file manager operate fully offline.
- Optional cloud drivers and Firebase AI require explicit user action and network.
- No automatic telemetry of vault contents.

## Known migration note

PIN hashes created before the 600k-iteration upgrade must be re-set by the user after update.
