# Security and CI Status

**Canonical branch:** `main`

## Verified state

- CodeQL open Actions permission alerts cleared by removing retired workflows (#120).
- Application CI: unit tests, lint, debug APK on current main.
- SQLCipher encrypted Room path in production use.
- Release workflow fail-closed for tests, lint, FOSSA, signing.
- Cloud backup path hardened (#115–#118).

## Remaining

- Hosted emulator boot remains environment-sensitive.
- Non-Google cloud SPI drivers may be incomplete.
- Store release requires signed release workflow + broader device CUJs.

## Rule

Green debug CI ≠ production approval. Promote only after signed release gates pass.
