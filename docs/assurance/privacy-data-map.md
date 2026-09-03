# Privacy data map (VVF Smart Manager)

**Status:** CURRENT  
**Package:** `com.vvf.smartmanager`

## Data classes

| Class | Location | Sensitivity | Backup | Notes |
|-------|----------|-------------|--------|-------|
| Vault files | App-private encrypted storage | Critical | Excluded (`allowBackup=false`) | AES-GCM + Keystore |
| SQLCipher DB | App-private | Critical | Excluded | Passphrase from Keystore |
| File index metadata | Room/SQLCipher | High | Excluded | Paths, hashes, favorites |
| OAuth tokens (Drive) | App-private | Critical | Excluded | Never log |
| PIN / decoy vault state | Keystore-backed | Critical | Excluded | Lockout policy |
| OCR/semantic embeddings | On-device only | Medium | Excluded | No mandatory network |

## Platform controls

- `android:allowBackup="false"`
- Storage browse fail-closed without grants
- Plugins on-demand via SPI
- Cloud remote only after explicit user sync

Residual: objects on Google Drive remain until user deletes them remotely.
