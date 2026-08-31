# Release Gate

The release workflow is intentionally separate from the ordinary CI quality gate.

Required production release checks:

- production keystore path and all signing credentials are present;
- Firebase production configuration is injected securely;
- release APK is assembled with the production signing configuration;
- `apksigner verify --verbose` succeeds;
- provenance attestation succeeds;
- the exact attested artifact is retained for distribution.

A green debug/test CI run does not certify a production release. Conversely, release-only credentials must never be required by unit tests, lint, or debug assembly.
