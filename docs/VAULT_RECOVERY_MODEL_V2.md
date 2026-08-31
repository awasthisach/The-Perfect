# Vault Recovery Model

Vault filesystem mutations are treated as untrusted until the operation completes successfully.

- Journal recovery may mutate only paths canonically contained by the vault directory.
- Failed encryption rolls back the database metadata and attempts secure removal of the incomplete encrypted artifact.
- Restore/export decrypts into a sibling temporary file and publishes only after authenticated decryption succeeds.
- If secure shredding fails after restore, the encrypted source and database record are retained for recoverability.
- Existing plaintext destinations are never overwritten.

This fail-safe policy prevents cleanup failures from becoming silent data loss.
