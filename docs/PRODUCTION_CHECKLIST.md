# Production Readiness Checklist

This checklist is an evidence log, not a declaration that the app is production-ready. Do not close PROD-008, PROD-009, or PROD-010 until the required evidence is attached and independently verified. Keep CPAS fail-closed.

## 1. ORT dependency, license, vulnerability and SBOM review — PROD-008 / Issue #136

- [ ] Merge the ORT cache/VCS fixes only after the PR checks pass.
- [ ] Run **Nightly ORT Compliance Scan** against the exact commit intended for release (use `workflow_dispatch` and select that commit's branch if the scheduled run has not scanned it).
- [ ] Confirm the workflow completed successfully and download the `ort-results-evidence` artifact for that exact commit SHA.
- [ ] Verify `ort-status.json` says `PASS` and its `scan_revision` equals the release commit SHA.
- [ ] Review `evaluation-result.json` for every license-policy violation; identify the affected package, license, path, and distribution impact.
- [ ] Review `advisor-result.json` for vulnerabilities and assess severity, exploitability, affected versions, and remediation.
- [ ] Review `scan-result.json`, analyzer output, and generated CycloneDX/SPDX SBOMs. Confirm the reports describe the intended dependency graph and are not missing or malformed.
- [ ] Fix, upgrade, replace, or document each finding with evidence. Do **not** suppress findings or fabricate a PASS status.
- [ ] Attach the ORT artifact URL, scanned commit SHA, decision/remediation notes, and reviewer to Issue #136.
- [ ] Confirm CPAS remains blocked until the required ORT evidence and all other required production evidence pass.

## 2. Firebase App Check and embedding Worker — PROD-009 / Issue #140

- [ ] In Google Play Console, open **Setup → App integrity** and copy the **App signing key certificate SHA-256** fingerprint. Do not substitute the upload-key fingerprint.
- [ ] In Firebase Console, select project `thevvforg` and the Android app package `com.vvf.smartmanager`.
- [ ] Open **App Check**, register the Android app, choose **Play Integrity**, and configure the verified Play app / SHA-256 details required by Firebase.
- [ ] Confirm the installed build uses the correct Firebase project configuration and signing identity. Verify debug builds separately; a debug token is not production proof.
- [ ] Deploy the Worker API contract required by Issue #140 (v4). Verify it validates a Firebase ID token **and** a valid Firebase App Check token; a Drive access token must not be used as the Worker authentication token.
- [ ] Verify the deployed Worker health response reports contract version `4` and the expected embedding model/dimension.
- [ ] On a real Android device, send an authenticated request with valid Firebase Auth + App Check and capture a successful response (HTTP 200).
- [ ] On a real Android device or controlled test client, send requests with missing and invalid App Check tokens and capture rejection (HTTP 403). Confirm no embedding data is returned on rejection.
- [ ] Attach the Worker deployment/version, redacted request/response evidence, device/build SHA, and test date to Issue #140.
- [ ] Only enable enforcement after valid clients have been verified; confirm invalid requests fail closed.

## 3. Google OAuth, Play signing, privacy and device acceptance — PROD-010 / Issue #141

### Google Cloud OAuth
- [ ] In Google Cloud Console, select the correct project and inspect the OAuth consent screen / Google Auth Platform audience and publishing status.
- [ ] Move the app from **Testing** to **In production** only after the app name, support email, developer contact, authorized domains, homepage, privacy-policy URL, and requested scopes are correct.
- [ ] Complete Google verification for any scopes that require it; do not claim verification merely because publishing status changed.
- [ ] Verify the Web Client ID used by the Android app matches the intended Firebase/Google Cloud OAuth client configuration and `BuildConfig.GOOGLE_WEB_CLIENT_ID`.
- [ ] Use least-privilege Google Drive scopes; do not request full Drive access unless the feature and Google policy explicitly require it.

### Signing and Play Console
- [ ] Configure Play App Signing and confirm the app-signing certificate fingerprint.
- [ ] Configure protected CI secrets for the upload keystore, keystore password, key alias/password, and Firebase configuration. Never commit these values or the keystore.
- [ ] Build and verify a signed release APK/AAB; retain the artifact, SHA-256 checksum, signing certificate fingerprint, version name/code, and commit SHA.
- [ ] Upload the AAB to an internal/closed testing track and resolve Play Console pre-launch/security/policy findings.
- [ ] Complete the applicable closed-testing requirement for the account type before requesting production access. Record tester count, test duration, and Play Console evidence.
- [ ] Verify package name, version code, app signing, install/update, launch, sign-in, Drive authorization, vault encryption/decryption, restore/export, and semantic-search consent on a real device.

### Privacy policy and Data Safety
- [ ] The branch must contain a complete privacy-policy draft; if `docs/PRIVACY_POLICY_DRAFT.md` is absent, create it before publishing.
- [ ] Replace all placeholders with the legal entity/developer name, support contact email, stable HTTPS policy URL, data collected, purpose, storage/processing locations, retention/deletion rules, third-party services, security practices, and age restrictions.
- [ ] Explain Google sign-in and Drive access, local vault/database processing, optional neural embedding processing, explicit consent, and how users can revoke consent/delete data.
- [ ] Host the final policy on a stable public HTTPS URL and confirm it is accessible without sign-in.
- [ ] Complete the Play Console Data Safety form and app-content declarations to match the actual shipped build and network behavior.
- [ ] Ensure the Play Store listing privacy-policy URL and in-app policy link both point to the published policy.
- [ ] Attach OAuth status, policy URL, Data Safety submission, signed artifact/checksum, and real-device test evidence to Issue #141.

## 4. Final release gate

- [ ] Debug CI passes unit tests, Android lint, `assembleDebug`, and uploads the APK artifact for the exact commit.
- [ ] SQLCipher instrumentation has passed in the designated hard-gate environment; an emulator infrastructure skip is not evidence of a pass.
- [ ] Nightly ORT has a successful, exact-SHA artifact with all required reports and no unresolved policy/security findings.
- [ ] PROD-009 and PROD-010 have the external console and real-device evidence listed above.
- [ ] Run `python3 tools/audit/cpas_verify.py --root .` and the release gate on the candidate commit; preserve the output.
- [ ] Do not describe the app as production-ready while CPAS is blocked or any owner-controlled acceptance remains unverified.

## Evidence record

For each completed item, record the date, exact commit SHA, run/artifact URL, console evidence (redact secrets), tester/device, outcome, and reviewer. Never store OAuth tokens, Firebase service credentials, signing passwords, private keys, or unredacted user data in the repository.
