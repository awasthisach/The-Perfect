# Vault Recovery Model

Vault filesystem mutations are treated as untrusted until the operation completes successfully.

## Guarantees

- Journal paths are never trusted for arbitrary filesystem deletion; recovery may mutate only paths canonically contained by the vault directory.
- Failed encryption removes the corresponding database metadata and attempts secure removal of the incomplete encrypted artifact.
- Restore/export decrypts into a sibling temporary file and publishes only after authenticated decryption succeeds.
- A failed secure shred after restore does not erase the database record or encrypted source; this preserves recoverability and makes the remaining encrypted copy discoverable.
- Existing plaintext destinations are never overwritten by restore/export.

## Operational consequence

A successful restore can leave the encrypted vault copy present if secure shredding is unavailable. This is intentional fail-safe behavior: confidentiality cleanup failure must not become silent data loss.
