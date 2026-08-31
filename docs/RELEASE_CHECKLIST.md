# Release Checklist

Before any production release:

1. CI unit tests are green.
2. Android lint is green.
3. Debug APK assembly is green.
4. Release signing secrets are present in the protected release environment.
5. `GOOGLE_SERVICES_JSON_BASE64` is present and validates as Firebase configuration.
6. Release APK passes `apksigner verify --verbose`.
7. Build provenance attestation succeeds.
8. Release artifact is retained as the signed CI artifact; do not rebuild it manually before distribution.
9. Any vault restore operation promotes decrypted files atomically only after authenticated decryption succeeds.
10. Rollout starts with a small cohort and monitoring enabled before broad distribution.
