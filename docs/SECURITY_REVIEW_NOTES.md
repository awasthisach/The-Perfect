# Security Review Notes

## Current hardening baseline

The application now has explicit network cleartext denial at the manifest layer, immutable CI action references, fail-closed release signing, build provenance attestation, and authenticated AES-GCM vault cryptography tests.

## Remaining high-value review item

The secure-vault streaming API intentionally streams plaintext to its destination before the GCM authentication tag is observed. This is safe only when the destination is treated as a temporary/untrusted output until the function returns successfully. Production callers must use a temporary file and atomic promotion on success, as documented in `docs/CRYPTO_INTEGRITY.md`.

The next implementation pass should audit every `decryptFile`/`decryptStream` caller and enforce that invariant in code rather than relying only on caller discipline.
