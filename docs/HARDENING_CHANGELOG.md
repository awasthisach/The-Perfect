# Hardening Changelog

## 2026-08-31

- Removed duplicate nested source tree.
- Removed self-modifying wrapper bootstrap workflow.
- Pinned GitHub Actions used by CI/release to immutable commit SHAs.
- Added signed-release build provenance attestation.
- Made release signing fail closed; debug signing is debug-only.
- Added build-time Firebase configuration validation for production release.
- Added explicit manifest cleartext-traffic denial.
- Added crypto regression tests for IV uniqueness, ciphertext/IV tampering, truncated streams, streaming byte preservation, and PIN format boundaries.
- Documented the atomic-promotion requirement for authenticated vault decryption.
