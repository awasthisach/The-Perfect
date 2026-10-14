# Release Checklist

This is a fail-closed checklist. A skipped check is not a pass. Do not mark the app production-ready until each applicable item has linked evidence.

## Automated build and supply chain

- [ ] Current candidate SHA passes `testDebugUnitTest`, `lintDebug`, and `assembleDebug`.
- [ ] Dedicated SQLCipher instrumentation workflow runs on an Android emulator and passes; do not count a soft emulator skip as success.
- [ ] CodeQL and CPAS Production Gate pass on the same candidate SHA.
- [ ] FOSSA analysis and policy test both pass on the same candidate SHA. Review all findings with an API key that can read issue details; triage licenses and vulnerabilities rather than bypassing the gate.
- [ ] Worker TypeScript build and deploy dry-run pass.
- [ ] The exact release candidate SHA and all evidence URLs are recorded.

## Embedding Worker and privacy

- [ ] Confirm `cloudflare/drive-semantic-embed/src/index.ts` is deployed to `https://drive-semantic-embed.awasthi-sach.workers.dev` through the protected manual deployment workflow.
- [ ] Public health check returns `ok=true`, model `gemini-embedding-2`, version `4`, dimension `768`.
- [ ] A native Android request without an `Origin` header returns 401 without credentials and succeeds only with valid Firebase Auth ID token plus Firebase App Check token.
- [ ] A real signed-in Android device completes `POST /embed`; verify no Drive OAuth token, Gemini key, or document text appears in logs.
- [ ] Fresh-install neural consent defaults off; revoke consent during indexing and verify calls cancel and persisted vectors are removed.
- [ ] When embedding is unavailable or consent is off, local keyword/FTS/metadata search still works.

## Google account and Drive

- [ ] Verify Firebase project, Android app ID, package `com.vvf.smartmanager`, OAuth client, and actual signing certificate fingerprints in Google Cloud/Firebase.
- [ ] Test sign-in, denied permissions, token refresh/expiry, account switching and sign-out on a physical device.
- [ ] Confirm production OAuth requests `drive.readonly` for full-corpus read access and `drive.file` only for app-created backup files; verify the actual granted corpus and account behavior. `drive.readonly` is restricted: complete applicable OAuth verification and review the restricted-scope security-assessment and Google API User Data Policy requirements before public release, because opted-in extracted text is transmitted to Gemini through the Worker. Do not claim full-Drive coverage until real-account evidence exists.
- [ ] Verify paginated full indexing, changes feed, removed/trashed files, the corpus cap, account isolation, and interruption/resume checkpoints.

## Storage, backup and restore

- [ ] Test Room migrations against a pre-existing encrypted database with retained user data.
- [ ] Test malformed/oversized backup, invalid checksum, interrupted download, cancellation, duplicate restore, network loss, process death, rollback and retry.
- [ ] Verify SHA-256 against the actual downloaded bytes and confirm failed restore never replaces valid existing data.
- [ ] Test offline quota eviction and file integrity; no cloud file is deleted by this product.

## Android release and distribution

- [ ] Release signing uses protected CI secrets and never falls back to the debug keystore.
- [ ] Production Firebase configuration is injected securely and validates for the release application ID.
- [ ] Release AAB is built in CI; verify signer certificate and provenance/attestation.
- [ ] Install the exact signed artifact on a clean supported Android device and test login, indexing, keyword search, neural search, offline mode, consent, backup/restore and upgrade.
- [ ] Confirm Android/Google Play policy eligibility for every declared permission, privacy policy, Data safety disclosures, account/data deletion obligations where applicable, and app-access instructions.
- [ ] Configure staged rollout, crash/ANR monitoring and rollback criteria.

## Release decision

- [ ] All blockers have evidence and a named owner.
- [ ] README and implementation-gap audit match the candidate SHA and actual deployed services.
- [ ] PR is no longer Draft only after review and all required checks pass.
- [ ] Do not call the app production-ready if a required external configuration or real-device test remains unverified.
