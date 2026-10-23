# Release checklist (fail-closed)

## Candidate build
- [ ] Current candidate SHA passes the lightweight audit tests, Android unit tests, Android lint, and `assembleDebug`.
- [ ] Debug APK artifact uploaded from that same successful SHA.
- [ ] Weekly SQLCipher instrumentation hard gate passes; soft emulator skip is not accepted as success.
- [ ] CodeQL and CPAS Production Gate pass.

## Open-source compliance
- [ ] Nightly ORT Analyzer, Scanner, Evaluator, Advisor, Reporter and SBOM generation complete for the exact release SHA.
- [ ] Review every license violation and vulnerability. Fix or approve each finding with package-specific evidence; no blanket suppressions.
- [ ] `ort-status.json` is PASS for the exact SHA and required reports/SBOMs validate.
- [ ] Release workflow verifies successful ORT evidence for the exact commit and fails closed if it is missing, stale, malformed, or blocked.

## Signing and Play
- [ ] Protected signing and Firebase configuration secrets are present; release builds never fall back to the debug keystore.
- [ ] CI builds a signed release APK and signed Google Play App Bundle (AAB), verifies both signatures, records SHA-256, generates SBOM evidence, uploads both artifacts and attests both.
- [ ] Play App Signing certificate fingerprint is registered in Firebase App Check and matches the intended package `com.vvf.smartmanager`.
- [ ] OAuth consent-screen verification, privacy policy, Data Safety, permissions and app-access declarations are complete.

## Worker and user data
- [ ] Worker v4 deployment is verified. Production Gemini embedding remains disabled until billing/provider data-handling approval and explicit consent are complete.
- [ ] Physical-device test proves Firebase Auth + App Check success and invalid/missing-token rejection.
- [ ] Physical-device test covers sign-in/denied scopes, indexing, keyword search, embedding consent and revocation, offline behavior, vault restore/export, backup/restore, upgrade and account switching.
- [ ] No tokens, API keys or document text appear in logs.

## Owner/admin actions
- [ ] Repository owner uninstalls the retired FOSSA GitHub App and removes only its obsolete required status contexts. Keep ORT, CPAS, CI, and CodeQL requirements.
- [ ] Complete Firebase App Check/Play Integrity registration.
- [ ] Complete Google OAuth verification, privacy policy publication, Play signing and Data Safety declarations.
- [ ] Attach all evidence to Issues #136, #140 and #141.

**Do not merge or declare production-ready while CPAS is BLOCKED or any required evidence is missing.**
