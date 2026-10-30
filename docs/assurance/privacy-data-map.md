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
| OCR-extracted text / FTS tags | Room/SQLCipher | High | Included when user creates a database backup; archive is encrypted before upload | OCR text and tags are indexed locally; a user-selected database archive can carry the DB to Google Drive |
| Similarity token vectors | In-memory process cache | Medium | Not persisted as a vector index | Deterministic hashed token features (128 dimensions), not neural embeddings; no search text is sent to a remote embedding service by this implementation |

## Platform controls

- `android:allowBackup="false"`
- Storage browse fail-closed without grants
- Plugins on-demand via SPI
- Cloud remote only after explicit user sync

Residual: objects on Google Drive remain until user deletes them remotely.


## App Check and backend enforcement

The Android app installs the Play Integrity App Check provider outside JVM unit tests. This client-side installation is not evidence that Firebase or any custom backend has enforcement enabled. Treat protected remote operations as unverified until each backend's enforcement configuration and rejection behavior are independently tested. No claim of server-side enforcement is made here.
