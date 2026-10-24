# Production Release Checklist

## Pre-Release Verification

### 1. CPAS Gate Status
- [ ] Run: `python3 tools/audit/cpas_verify.py --root .`
- [ ] Verify: `cpas-status.json` shows `"status": "PASS"`
- [ ] Verify: All checks pass (including `ort_sboms_present` after first nightly run)

### 2. Build Verification
- [ ] Run: `./gradlew clean :app:assembleRelease`
- [ ] Verify: Build succeeds without errors
- [ ] Verify: APK/AAB generated in `app/build/outputs/`

### 3. Firebase App Check (PROD-009)
- [ ] Verify: Play App Signing SHA-256 registered in Firebase Console
- [ ] Verify: Play Integrity API enabled in Google Cloud Console
- [ ] Test: Install APK on physical device
- [ ] Test: Check Logcat for "AppCheck" → token generation successful
- [ ] Test: Firebase Console → App Check → Requests tab shows activity

### 4. ORT Compliance (PROD-008)
- [ ] Verify: Nightly ORT workflow ran successfully
- [ ] Verify: Auto-PR merged with real SBOMs
- [ ] Verify: `docs/assurance/ort-bom.cyclonedx.json` exists (not stub)
- [ ] Verify: `docs/assurance/ort-bom.spdx.json` exists (not stub)
- [ ] Verify: `docs/assurance/ort-status.json` shows `"status": "PASS"`

### 5. OAuth & Privacy (PROD-010)
- [ ] Verify: Google Cloud Console OAuth client SHA-1 matches Play Store signing key
- [ ] Verify: Privacy policy URL published and accessible
- [ ] Verify: Play Console → App content → Privacy policy filled
- [ ] Test: Install APK on physical device
- [ ] Test: Google Sign-In works with real account
- [ ] Test: Google Drive OAuth flow completes successfully

### 6. Evidence Freshness
- [ ] Verify: `docs/assurance/evidence-ledger.json` has entries ≤14 days old
- [ ] Verify: Weekly Instrumented Hard Gate ran successfully
- [ ] Verify: Auto-PR merged with fresh evidence

### 7. Risk Register
- [ ] Verify: `docs/assurance/08-risk-register.yaml` has no open critical/high findings
- [ ] Verify: PROD-008 closed with ORT evidence reference
- [ ] Verify: PROD-009 closed with App Check verification reference
- [ ] Verify: PROD-010 closed with OAuth verification reference

## Release Process

### Step 1: Internal Testing Track

```bash
# Build release APK
./gradlew :app:assembleRelease

# Upload to Play Console Internal Testing
# (Use Play Console UI or Google Play Developer API)
```

### Step 2: Physical Device Testing
- Install from Internal Testing Track on 2-3 physical devices
- Test all critical flows:
  - [ ] Google Sign-In
  - [ ] Google Drive backup/restore
  - [ ] Vault encryption/decryption
  - [ ] File indexing and search
  - [ ] OCR processing
  - [ ] Semantic search
- Verify no crashes in Firebase Crashlytics

### Step 3: Production Release
- Promote from Internal Testing → Production in Play Console
- Monitor for 24 hours:
  - [ ] Crash-free rate > 99%
  - [ ] No ANR spikes
  - [ ] App Check tokens generating successfully
  - [ ] OAuth flows working
- If issues detected, rollback immediately

## Post-Release

### Update Documentation
- [ ] Update `RELEASE_NOTES.md` with version and changes
- [ ] Update `docs/assurance/08-risk-register.yaml` with closure dates
- [ ] Update `docs/assurance/evidence-ledger.json` with release evidence

### Monitor
- [ ] Firebase Crashlytics: no new crash spikes
- [ ] Firebase App Check: token generation successful
- [ ] Google Play Console: no policy violations
- [ ] ORT nightly scan: continues to pass
