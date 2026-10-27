# Production Readiness Checklist — The-Perfect

This checklist records owner-controlled release work. Completing a checkbox requires actual console or device evidence; do not mark a gate complete based on code changes alone.

## PROD-008 — Open-source compliance / ORT (Issue #136)

- [ ] Run **Nightly ORT Compliance Scan** on the exact candidate commit (manual dispatch if needed), then review the successful run in GitHub Actions.
- [ ] Download the `ort-results-evidence` artifact and verify `ort-status.json.scan_revision` exactly matches the release commit SHA; retain the run URL and artifact.
- [ ] Confirm Analyzer, Scanner, Evaluator, Advisor, and SBOM/Reporter outputs exist and are valid.
- [ ] Review every Evaluator license finding and Advisor vulnerability; record package, version, license/CVE, severity, disposition, and supporting evidence.
- [ ] Fix or document findings through precise, evidence-backed curations; do not add blanket allowlists or suppress unresolved findings.
- [ ] Re-run ORT after remediation and attach results to Issue #136.
- [ ] Keep CPAS blocked until its risk register and gate evidence are legitimately updated.

## PROD-009 — Firebase App Check / Worker (Issue #140)

- [ ] In Google Play Console, open **Setup → App integrity** and copy the SHA-256 fingerprint for the **App signing key certificate** (not just the upload key).
- [ ] In Firebase Console, confirm the Android app package is `com.vvf.smartmanager` in project `thevvforg`.
- [ ] Register the Android app in Firebase App Check and configure **Play Integrity** with the correct Play app/signing identity.
- [ ] Confirm the production Worker is deployed with the accepted version and requires both Firebase Authentication and App Check; never use a Drive OAuth token as the Worker bearer token.
- [ ] From a real Play-installed Android build, test a valid Firebase ID token + valid App Check token and capture the expected successful response.
- [ ] Test missing/invalid App Check and confirm the endpoint rejects it (HTTP 403); retain redacted logs without tokens.
- [ ] Confirm the Worker health/version matches the release acceptance criteria (v4, not the older v3 response) and attach evidence to Issue #140.

## PROD-010 — OAuth, privacy, signing, and real-device acceptance (Issue #141)

- [ ] In Google Cloud Console, verify OAuth consent-screen app name, support email, authorized domains, scopes, and privacy-policy URL.
- [ ] Move OAuth publishing status from Testing to Production only after the required consent-screen verification and scope review are complete.
- [ ] Verify the Web Client ID in Firebase/Google configuration matches the value supplied to `BuildConfig.GOOGLE_WEB_CLIENT_ID`; do not commit credentials or API secrets.
- [ ] Replace every placeholder in the privacy policy: legal entity/controller name, contact email, hosted HTTPS URL, data collection/use/sharing, retention/deletion, security, and age restrictions.
- [ ] Publish the final privacy policy on a stable public HTTPS URL and link it in Play Console.
- [ ] Complete Play Console Data safety declarations accurately for the app and all SDKs; align them with actual data flows and the privacy policy.
- [ ] Configure Play App Signing and protect the upload key; add only required secrets to GitHub Actions secrets, never to source control.
- [ ] Build and install the signed candidate on a physical Android device. Test first launch, sign-in, Drive authorization, vault encrypt/decrypt/restore, offline behavior, search, App Check, and update/rotation behavior.
- [ ] Record device model, Android version, build commit, artifact SHA-256, test results, and sanitized logs. Do not mark device acceptance complete without this evidence.

## Final release evidence

- [ ] Latest PR CI: unit tests, lint, and `assembleDebug` pass on the exact candidate commit.
- [ ] Latest ORT run: all required stages completed; findings reviewed and dispositioned.
- [ ] CPAS verifier passes without weakening fail-closed policy.
- [ ] Firebase App Check, OAuth/privacy, Play signing, and real-device evidence are attached to Issues #136, #140, and #141.
- [ ] Debug APK is for development/testing only; publish a production release only through the signed release workflow after every required gate passes.
