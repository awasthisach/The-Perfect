# Drive Semantic Search

Android-native Google Drive search app built with Kotlin, Jetpack Compose, Material 3, Room, Coroutines/Flow, and WorkManager. This project must not be packaged as a WebView wrapper.

## Product contract

- Google account selection is interactive; Drive OAuth and Firebase Auth must refer to the same account.
- Google Drive actions must be explicit and user-confirmed. The app must never delete Drive files.
- Local keyword/full-text and metadata search remains available when neural search is disabled or unavailable.
- Neural search requires explicit, revocable consent. Consent defaults to off.
- No Gemini API key or other server secret belongs in the APK.
- File bytes belong in app-private storage. Backups must never include OAuth tokens, Firebase ID tokens, or API keys.
- Minimum supported Android version: API 26.

## Technology and architecture

- **Language/UI:** Kotlin, Coroutines/Flow, Jetpack Compose + Material 3
- **Architecture:** modular Clean Architecture / MVVM-style ViewModels; manual application composition root
- **Navigation:** Jetpack Navigation Compose
- **Persistence:** Room + SQLCipher; schema version 7 preserves prior local data through migrations
- **Security:** Android Keystore, AES-GCM vault encryption, protected database passphrase
- **Background work:** AndroidX WorkManager for bounded Drive indexing
- **Text extraction:** plain text and native Google Workspace exports; bounded PDF/image and DOCX/XLSX/PPTX extraction when the user opts in
- **Search:** Room FTS4/substring/metadata fallback plus a real 768-dimensional `gemini-embedding-2` adapter through the Cloudflare Worker. Embedding contract v4 uses Gemini's documented retrieval prompt format and regenerates old version-3 document vectors. Neural ranking remains opt-in and consent-gated; deployment and authenticated Android end-to-end acceptance of the Worker path are still release blockers.
- **Cloud:** Google Drive REST integration, paginated file listing/changes feed, user-authorized move/star/folder APIs
- **Offline copies:** app-private storage with a 200 MiB / 80-file cap and oldest-first eviction
- **Index backup:** user-selected JSON import/export, with credentials and local file bytes excluded
- **Build:** Gradle Kotlin DSL + version catalog
- **Compile/Target SDK:** 36
- **Minimum SDK:** 26

## Project structure

```text
app/                 Application entry point, Drive worker, extraction, offline/backup UI
core/common/         Shared utilities
core/model/          Shared models/contracts
core/security/       Cryptographic and Keystore services
core/database/       Room/SQLCipher persistence
core/data/           Repository/data implementations
core/domain/         Business use cases and backup orchestration
core/background/     WorkManager jobs
core/cloud-gdrive/   Google Drive integration
core/plugin-spi/     Plugin contracts
feature/*             User-facing feature modules
plugins/*             Optional provider/engine implementations
docs/                Architecture, security and readiness documentation
```

## Build and test

Use the Gradle wrapper rather than a system Gradle installation:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

## Signing and Google OAuth

- Do not commit a reusable debug or release keystore or hard-coded signing passwords.
- Debug builds use Gradle-managed default debug signing.
- Production release tasks require `KEYSTORE_PATH`, `STORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD` through the CI secret-injection path. Never fall back to the debug keystore for release builds.
- Production release builds also require the production `google-services.json`.
- The Android OAuth client must use application ID `com.vvf.smartmanager` and the actual signing certificate SHA fingerprints. Debug and release signing fingerprints differ. Removing the formerly embedded debug keystore can change the debug SHA-1, so verify Google Cloud OAuth client registration before relying on sign-in.

## Current implementation and release status

**Not production-ready.** Latest code evidence on candidate SHA `f853cc56ea9e34712b0ed9028780cfe43ea4ba89`: Android unit tests, lint, release-runtime dependency resolution and debug APK assembly passed in [CI run #38975222097](https://github.com/awasthisach/The-Perfect/actions/runs/38975222097); the SQLCipher instrumented hard gate passed in [run #38975221973](https://github.com/awasthisach/The-Perfect/actions/runs/38975221973); and the Worker build/default-deny smoke gate passed in [run #38975225554](https://github.com/awasthisach/The-Perfect/actions/runs/38975225554). The overall CI remains failed because FOSSA has 34 unresolved license-policy findings, and CPAS remains fail-closed on PROD-008/009/010. The Worker can now be deployed safely with embedding disabled; enabling the Gemini path still requires verified paid billing/provider data handling and native App Check acceptance. Re-run mandatory checks on the final candidate SHA after any changes.

The main release blockers are:

1. **Native neural embedding acceptance:** the Worker source requires the owner-controlled `GEMINI_PRODUCTION_APPROVED` flag to be true before health can pass or `/embed` can accept requests. It defaults to false. The deployment workflow permits a disabled v4 rollout but blocks enabling embeddings unless the dispatch confirms paid-tier Gemini billing and provider data handling. Firebase Console shows the Android app is unregistered with App Check; Play Integrity has no SHA-256 configured. The connected Google account has no Play Console developer account, so the actual Play App Signing fingerprint is unavailable. Do not enable neural indexing until these owner-level prerequisites and valid/invalid token acceptance tests are complete. Never spoof `Origin`, embed a Gemini key in the APK, or send a Drive access token to the embedding endpoint.
2. **FOSSA license gate:** [CI run #38975222097](https://github.com/awasthisach/The-Perfect/actions/runs/38975222097) on candidate SHA `f853cc56ea9e34712b0ed9028780cfe43ea4ba89` still reports **34 unresolved policy issues**: 30 policy flags and 4 policy conflicts across seven Maven coordinates, with no vulnerability-type findings in the structured report. All seven coordinates are present in the release runtime dependency graph. The per-issue API returns all 34 records, but its details fields are empty; the documented attribution endpoint returns HTTP 200 with no dependency rows. Matched-file evidence and FOSSA's required correction metadata are therefore still unavailable from this export. No license findings were suppressed or blanket-approved. Keep the hard gate failed closed.
3. **Google OAuth/Firebase configuration:** read-only inspection confirmed Firebase Android app `com.vvf.smartmanager` (App ID `1:1058219630435:android:759d656a98d3b50aee3d56`) and an Android OAuth client for the correct package. The previous `d2tokpcf...` web client ID is not present in Google Cloud; `.env.example` now points to the existing Firebase-generated Web client ID. Firebase Project Settings currently shows no SHA certificate fingerprints, and the only visible Android OAuth SHA-1 may be stale. The protected release workflow must verify the ID against `google-services.json`; then compare the actual upload/release signing fingerprints and test sign-in, denial, token refresh, account switching and sign-out on a device.
4. **Drive access scope:** the app requests `drive.readonly` for the full read-only corpus and `drive.file` for app-created backups, not full Drive-management access. `drive.readonly` is restricted; public release requires applicable Google OAuth verification and review of restricted-scope security-assessment and User Data Policy requirements because opted-in extracted text is sent to Gemini through the Worker. Successful OAuth does not prove every Drive file is accessible.
5. **Data integrity and privacy:** code-level restore tests now cover full and partial backup manifests, preserving excluded database/vault components, rejecting a missing declared database before touching live data, and keeping an explicit rollback snapshot available after successful restore. Cloud backup downloads are bounded to 200 MiB and backup creation rejects larger artifacts. Unit tests and SQLCipher instrumentation passed on the cited candidate SHA. Real-account remote download/hash verification, process-death recovery, and physical-device end-to-end restore acceptance remain unverified.
6. **OAuth and Gemini data handling:** Google Auth Platform is External + Testing and not verified; the `drive.readonly` scope's verification status was not confirmed in the console inspection. Google Cloud project `thevvforg` has no linked billing account. The Worker now blocks embeddings until the owner verifies the paid tier and provider data-handling terms and explicitly enables the approval flag. Complete restricted-scope verification and any required security assessment before public launch.
7. **Device and release acceptance:** complete a clean-device acceptance run for sign-in, indexing, keyword and neural search, offline behavior, backup/restore, migration and consent. Build a release-signed AAB in the protected CI environment, verify its signature and provenance, and confirm Play Console declarations and rollout monitoring. Debug APK assembly is not a production release.
8. **Release evidence:** update this README and the release checklist only when evidence changes. Do not merge the draft or call the app production-ready until every blocker has a linked, reproducible result.

A green CI workflow is necessary but not sufficient. See the implementation-gap audit and mobile embedding authentication requirements below.

- [Drive Semantic Search implementation-gap audit](docs/DRIVE_SEMANTIC_SEARCH_IMPLEMENTATION_GAP.md)
- [Mobile embedding authentication requirements](docs/ANDROID_EMBEDDING_AUTH.md)
- [Architecture Guide](docs/ARCHITECTURE.md)
- [Security Whitepaper](docs/SECURITY_WHITEPAPER.md)
- [Release Checklist](docs/RELEASE_CHECKLIST.md)

Cloud restore has fail-closed code paths and passing unit/instrumentation evidence, but public release remains blocked until a real-account remote backup download, SHA-256 re-verification, atomic restore/rollback, and physical-device recovery acceptance are recorded.
