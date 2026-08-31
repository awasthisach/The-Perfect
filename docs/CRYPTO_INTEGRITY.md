# Cryptographic Integrity Contract

The secure-vault crypto layer uses AES-GCM authenticated encryption with a 12-byte IV and a 128-bit authentication tag. The test suite explicitly protects these invariants:

- repeated encryption of the same plaintext produces different IVs;
- ciphertext tampering is rejected;
- IV tampering is rejected;
- truncated encrypted-stream headers are rejected;
- streaming encryption/decryption preserves the exact byte count and payload;
- vault PIN input remains strictly bounded to 4–6 decimal digits.

## Important file-operation rule

`decryptStream` writes plaintext as it is streamed and only learns that the GCM authentication tag is valid when the encrypted stream reaches EOF. Callers that replace or overwrite a durable file MUST decrypt into a temporary destination first and atomically promote it only after `decryptStream` returns successfully. They must delete the temporary plaintext on any failure.

This prevents a failed/tampered ciphertext from becoming the application's durable restored file.
