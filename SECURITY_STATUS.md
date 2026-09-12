# Security and CI Status

**Canonical branch:** `main`

This repository uses GitHub **CodeQL Default Setup** as the authoritative CodeQL configuration. Do not enable a concurrent Advanced CodeQL workflow (SARIF upload conflicts).

## Verified state (recent main)

- Application CI: unit tests, lint, and debug APK assembly on the hardening line.
- **SQLCipher** encrypted Room database is the production path.
- **Release workflow** (`.github/workflows/release.yml`): fail-closed for unit tests, lint, FOSSA analyze, signing, and APK verification — no debug-keystore fallback.
- Cloud backup path hardened for Drive upload format, folder name vs fileId, and database snapshot staging (#115–#118).
- FileProvider must not expose `<root-path>`; restore applies are staged/fail-closed by design in domain pipelines.

## Remaining external / product work

- Black Duck / org-level scanners: configure in GitHub security settings if required.
- Hosted-runner Android emulator boot remains environment-sensitive (soft gate on PR CI; weekly instrumented hard path).
- Non-Google cloud SPI drivers may still be stubs — do not advertise them as production-complete.
- Public store release still requires signed release workflow success + broader real-device CUJs.

## Release rule

A green **debug** CI run is **not** production approval. Promote only after the **signed release** workflow and its fail-closed gates pass.
