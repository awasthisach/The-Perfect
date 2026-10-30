# Production ship checklist (authoritative)

**Integration branch:** `rebuild/stabilize-v1`  
**Policy:** Ship only when every box is evidenced on the **same git SHA**. Until then status is **NO-SHIP**.

This follows industry release practice: signed binary + automated gates + SBOM + device acceptance + honest public claims (Google Play policy, OWASP MASVS-aligned crypto handling, SLSA-oriented release attestation already in `release.yml`).

## A. Code integrity (must be true)

- [x] Single DI style: manual `AppCompositionRoot` / `VVFApplication` (unused Hilt removed from app module on stabilize branch).
- [x] SQLCipher native load before encrypted Room open.
- [x] Play Integrity App Check provider factory is installed outside JVM unit tests.
- [ ] Server-side App Check enforcement and rejection behavior verified for every protected Firebase/custom backend; client provider installation alone is insufficient.
- [x] PIN lockout uses authenticated state + monotonic elapsed realtime.
- [x] Portable backup passphrase envelope; no vault_auth.json export as authority.
- [x] FileProvider paths limited to share/exports.
- [x] `allowBackup=false` + network cleartext disabled in manifest.
- [x] Release signing refuses debug keystore fallback (Gradle hard fail).

## B. Automated verification (same SHA)

- [ ] `./gradlew testDebugUnitTest` green
- [ ] `./gradlew lintDebug` green
- [ ] `./gradlew assembleDebug` green
- [ ] SQLCipher instrumented hard gate green on this SHA
- [ ] Nightly ORT: `ort-status.json` status PASS for this SHA; CycloneDX + SPDX present under `docs/assurance/`
- [ ] `python3 tools/audit/cpas_verify.py --root .` → `production_status` PASS
- [ ] `python3 tools/audit/release_gate.py` PASS with APK path

## C. Device / account acceptance (cannot be faked in public CI)

- [ ] Physical device: vault PIN lockout after N failures
- [ ] Physical device: portable backup on device A → restore on device B with passphrase
- [ ] Physical device: App Check tokens visible in Firebase console
- [ ] Real Google account: Drive sign-in + upload/download smoke
- [ ] Biometric unlock one-shot (no replay)

## D. Store / legal honesty

- [ ] Privacy policy URL live (not placeholder)
- [ ] Play Data safety form matches actual data collection (Drive OAuth, crash/analytics if any); merged release manifest and All files access declaration reviewed/approved
- [ ] README does not claim production-ready until B+C complete
- [ ] Internal testing track upload of **signed** release APK/AAB

## E. Explicit blockers if unchecked

| Gap | Effect |
|-----|--------|
| Missing ORT PASS on SHA | CPAS production BLOCKED; release.yml fails |
| Unit/lint/instrumented red | Do not merge / do not ship |
| Device OAuth untested | Cloud features residual risk |
| Placeholder privacy | Play policy rejection risk |

## Decision log (stabilize)

| Decision | Rationale |
|----------|-----------|
| Stabilize existing app; no greenfield rewrite | #181 already delivered portable backup/restore; thrash cost of rewrite is higher |
| One integration branch | Parallel PRs caused 2500+ conflict builds |
| Remove unused Hilt from app | Catalog had Hilt but no `@HiltAndroidApp`; dual-DI risk |
| Keep CPAS fail-closed on missing ORT | Industry SBOM expectation; do not weaken for green checks |
