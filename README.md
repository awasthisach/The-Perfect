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
- **Persistence:** Room + SQLCipher; schema version 4 preserves prior local data through migrations
- **Security:** Android Keystore, AES-GCM vault encryption, protected database passphrase
- **Background work:** AndroidX WorkManager for bounded Drive indexing
- **Text extraction:** plain text and native Google Workspace exports; bounded PDF/image and DOCX/XLSX/PPTX extraction when the user opts in
- **Search:** Room FTS4/substring/metadata fallback. The currently wired deterministic hash-token similarity plugin is **not a neural embedding model**.
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

**Not production-ready.** The branch now includes a Room-backed Drive index worker, local text extraction, bounded offline pinning, and JSON index import/export. Those paths still need end-to-end failure/restart, migration, consent, and real-device verification.

The main release blockers are:

1. A trusted mobile embedding proxy or worker-side Android App Check is not deployed. The app must not call the Cloudflare embedding worker directly or spoof an allowed Origin. The current deterministic hash-token plugin is not a neural model, so true neural semantic ranking remains unavailable.
2. Google OAuth/Firebase configuration and the new Gradle-managed debug/release signing fingerprints must be verified in the actual Google Cloud/Firebase project.
3. Drive move/star/folder suggestion and confirmation flows, product-specific navigation, duplicate review, and vault/offline/backup UX need full end-to-end acceptance testing. Legacy file-manager functionality remains in the app.
4. The index worker commits its cursor only after a full successful pass; interruption during the full listing replays that listing rather than resuming at a persisted list-page boundary. Failure/cancellation behavior needs dedicated tests.
5. Run and retain passing unit, lint, debug/release build, emulator/instrumented, SQLCipher migration, and real-device Google OAuth/Drive evidence.

A green CI workflow alone does not establish production readiness. See the implementation-gap audit and mobile embedding authentication requirements below.

- [Drive Semantic Search implementation-gap audit](docs/DRIVE_SEMANTIC_SEARCH_IMPLEMENTATION_GAP.md)
- [Mobile embedding authentication requirements](docs/ANDROID_EMBEDDING_AUTH.md)
- [Architecture Guide](docs/ARCHITECTURE.md)
- [Security Whitepaper](docs/SECURITY_WHITEPAPER.md)
- [Production Readiness Audit](docs/PRODUCTION_READINESS_2026-08-31.md)

Cloud restore remains fail-closed until download, integrity verification, atomic staging, rollback, and recovery tests are complete.
