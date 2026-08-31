# Production Verification Status

## Verified statically

- CI and release workflow action references are immutable commit SHAs.
- Release signing is fail-closed and does not fall back to the Android debug keystore.
- Release workflow injects Firebase configuration only at build time and removes it afterward.
- Release APKs are verified with `apksigner` and receive build provenance attestations.
- Android manifest explicitly denies cleartext traffic.
- Cryptographic tests cover AES-GCM round trips, unique IVs, ciphertext/IV tampering, truncated stream headers, and PIN input bounds.

## Verification still required in GitHub Actions

A real Android SDK-backed runner must execute:

- `./gradlew --no-daemon testDebugUnitTest --stacktrace`
- `./gradlew --no-daemon lintDebug --stacktrace`
- `./gradlew --no-daemon assembleDebug --stacktrace`
- the signed release workflow with production signing/Firebase secrets

Until those runs are green, production readiness remains **not certified**. Static hardening is not a substitute for a successful clean build and test execution.
