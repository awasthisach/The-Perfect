# Part 3 — Release excellence

## Current compliance gate

`GATE-LICENSE-001` is implemented with OSS Review Toolkit (ORT), not FOSSA. A candidate must have a successful **Nightly ORT Compliance Scan** on the exact release SHA. The run publishes `ort-status.json` and the `ort-results-evidence` artifact. The release workflow verifies the status and `scan_revision` before staging ORT SBOMs.

The release gate requires CPAS PASS, ORT PASS for the exact commit, and a non-empty signed APK with its SBOM and provenance retained. A successful workflow alone is not license approval: review all evaluator findings and vulnerabilities, and keep unresolved copyleft findings visible.

## Remaining blockers

- **PROD-002 / PROD-003** remain open until integration and clean-device evidence is attached.
- PROD-008 still requires owner review of ORT findings, precise evidence-backed curations, and a decision on the root project's license. No root `LICENSE` or `NOTICE` has been added because the project owner has not selected a license.
- CI/GitHub Actions dependencies must remain identifiable as CI tooling. Do not suppress GPL/LGPL findings in bundled action code; review them separately from APK policy.
- The ORT scan must meet the SBOM license-coverage threshold and include analyzer, scanner, evaluator, Advisor, and reporter evidence before promotion.
- Configure release secrets before a real ship: `ANDROID_KEYSTORE_BASE64`, `ANDROID_KEYSTORE_PASSWORD`, `ANDROID_KEY_ALIAS`, `ANDROID_KEY_PASSWORD`, and `GOOGLE_SERVICES_JSON_BASE64`.

## Promotion rule

Do not treat a build as production-assured until:

```text
cpas-status.production_status == PASS
ort-status.status == PASS
ort-status.scan_revision == release commit SHA
signed APK verified + SBOM + attestation retained
```
