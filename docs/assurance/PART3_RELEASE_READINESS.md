# Part 3 — Release excellence

## What shipped

1. **Evidence pipeline**
   - `generate_evidence.py` path resolution fixed for CI.
   - `evidence-ledger.json` updated with real CI run `33714903184` for STORAGE / RESTORE / WORK controls.
   - `DB-INV-001` still carries local-pre-ci until SQLCipher instrumentation completes on emulator.

2. **Release workflow** (`.github/workflows/release.yml`)
   - Hard-fail CPAS verifier before signing.
   - FOSSA secret required for release (fail-closed).
   - Signed APK verify via `apksigner`.
   - SHA-256 + cert fingerprint manifest (`release-meta/apk-manifest.txt`).
   - Syft SPDX SBOM (`release-meta/sbom.spdx.json`).
   - `release_gate.py` requires CPAS PASS + FOSSA PASS + non-empty APK.
   - GitHub Actions attestation (SLSA-oriented provenance).

3. **Residual blockers (intentional fail-closed)**
   - **PROD-002 / PROD-003** remain `tested` until integration + clean-device evidence.
   - Production CPAS status remains **BLOCKED** while those critical findings are open.
   - Configure secrets before a real ship:
     - `FOSSA_API_KEY`
     - `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`
     - `GOOGLE_SERVICES_JSON_BASE64`

## Promotion rule

Do **not** treat a build as production-assured until:

```
cpas-status.production_status == PASS
foss-status.status == PASS
signed APK verified + SBOM + attestation retained
```
