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

**Not production-ready.** The branch includes the native Kotlin/Compose app, resumable Drive indexing, local text extraction, encrypted Room-backed embedding-vector storage, consent-gated Cloudflare neural embeddings, and JSON index import/export. Protected Worker v4 health/auth-negative checks exist, but real native App Check acceptance is still unverified. Current candidate head is `809786f9f3683ec3e39a6412fe83677c624daf73`; CI is being rerun for this head. The last completed CPAS run remains fail-closed on PROD-008/009/010. Do not use older SHA results as proof for the current head.

The main release blockers are:

1. **Native neural embedding acceptance:** protected deployment [run #38676382484](https://github.com/awasthisach/The-Perfect/actions/runs/38676382484) now verifies Worker health contract v4 and rejection of `/embed` requests without Firebase credentials. This does not yet prove a valid signed-in Android request with Firebase Auth + App Check succeeds. Test the valid and invalid token paths on a physical device. Never spoof `Origin`, embed a Gemini key in the APK, or send a Drive access token to the embedding endpoint.
2. **FOSSA license gate:** the latest inspected scan in [CI run #38726927541](https://github.com/awasthisach/The-Perfect/actions/runs/38726927541) reports **34 unresolved policy issues** at candidate SHA `c66042670bd6d2b92bc178ae89f37d31a2ebe20c`: 30 policy flags and 4 policy conflicts across seven Maven coordinates; the report has no vulnerability-type findings. The `releaseRuntimeClasspath` filter is configured experimentally in `.fossa.yml`, but the scan/dashboard does not provide enough evidence to claim that this scope filter removed or excluded any findings. Review each finding against its exact dependency path, packaged artifact, and license evidence. Keep the FOSSA gate fail-closed; do not blanket-allow or suppress licenses, and do not treat `fossa analyze` success as license approval. The CI credential is present and analysis/test run successfully enough to return structured findings; do not claim that issue details are unavailable solely because of the credential.
3. **Google OAuth/Firebase configuration:** read-only inspection confirmed Firebase Android app `com.vvf.smartmanager` (App ID `1:1058219630435:android:759d656a98d3b50aee3d56`) and an Android OAuth client for the correct package. The previous `d2tokpcf...` web client ID is not present in Google Cloud; `.env.example` now points to the existing Firebase-generated Web client ID. Firebase Project Settings currently shows no SHA certificate fingerprints, and the only visible Android OAuth SHA-1 may be stale. The protected release workflow must verify the ID against `google-services.json`; then compare the actual upload/release signing fingerprints and test sign-in, denial, token refresh, account switching and sign-out on a device.
4. **Drive access scope:** the app requests `drive.readonly` for the full read-only corpus and `drive.file` for app-created backups, not full Drive-management access. `drive.readonly` is restricted; public release requires applicable Google OAuth verification and review of restricted-scope security-assessment and User Data Policy requirements because opted-in extracted text is sent to Gemini through the Worker. Successful OAuth does not prove every Drive file is accessible.
5. **Data integrity and privacy:** test full-list/change-feed pagination, interruption/restart checkpoints, expired tokens, account isolation, consent revocation, vector deletion, malformed/oversized index backups, and atomic restore/rollback against existing encrypted databases.
6. **OAuth and Gemini data handling:** the app requests `https://www.googleapis.com/auth/drive.readonly` for corpus reads and `https://www.googleapis.com/auth/drive.file` for app-created backups. Confirm restricted-scope verification and any required annual CASA/security assessment before public launch. Confirm the Worker key belongs to a paid Gemini API project with active Cloud Billing; Google's free tier may use submitted content to improve products. Review abuse-monitoring retention and privacy disclosures.
7. **Device and release acceptance:** complete a clean-device acceptance run for sign-in, indexing, keyword and neural search, offline behavior, backup/restore, migration and consent. Build a release-signed AAB in the protected CI environment, verify its signature and provenance, and confirm Play Console declarations and rollout monitoring. Debug APK assembly is not a production release.
8. **Release evidence:** update this README and the release checklist only when evidence changes. Do not merge the draft or call the app production-ready until every blocker has a linked, reproducible result.

A green CI workflow is necessary but not sufficient. See the implementation-gap audit and mobile embedding authentication requirements below.

- [Drive Semantic Search implementation-gap audit](docs/DRIVE_SEMANTIC_SEARCH_IMPLEMENTATION_GAP.md)
- [Mobile embedding authentication requirements](docs/ANDROID_EMBEDDING_AUTH.md)
- [Architecture Guide](docs/ARCHITECTURE.md)
- [Security Whitepaper](docs/SECURITY_WHITEPAPER.md)
- [Release Checklist](docs/RELEASE_CHECKLIST.md)

Cloud restore remains fail-closed until download, integrity verification, atomic staging, rollback, and recovery tests are complete.
