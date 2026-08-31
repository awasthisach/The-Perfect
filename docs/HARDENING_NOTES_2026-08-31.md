# Hardening Notes — 2026-08-31

This pass focused on secure-vault filesystem and failure semantics.

## Changes

1. Encryption failure now rolls back the corresponding vault metadata row before propagating the error.
2. Journal recovery no longer trusts persisted paths for arbitrary filesystem mutation; cleanup is restricted to canonical paths inside the vault directory.
3. Restore/export never overwrites an existing plaintext destination.
4. Restore/export authenticates encrypted content in a temporary sibling file before publishing it.
5. A failed post-restore secure shred preserves the encrypted source and metadata rather than reporting a destructive success state.
6. Release verification and crypto integrity contracts remain documented separately.

## Verification limitation

The GitHub connector can inspect and modify repository source, but a clean Android SDK execution is still required to certify compilation, tests, lint, and release assembly. Static review therefore remains evidence-backed but not equivalent to a green production build.
