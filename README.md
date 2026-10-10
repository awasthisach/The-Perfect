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
- **Persistence:** Room + SQLCipher
- **Security:** Android Keystore, AES-GCM vault encryption, protected database passphrase
- **Background work:** AndroidX WorkManager
- **OCR:** ML Kit plugin
- **Search:** existing on-device semantic plugin plus local FTS/metadata fallback; cloud embedding integration is not yet production-ready
- **Cloud:** Google Drive REST integration and cloud-driver SPI
- **Build:** Gradle Kotlin DSL + version catalog
- **Compile/Target SDK:** 36
- **Minimum SDK:** 26

## Project structure

```text
app/                 Application entry point and navigation
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
- The Android OAuth client must use application ID `com.vvf.smartmanager` and the actual signing certificate SHA fingerprints. Debug and release signing fingerprints differ. Removing the formerly embedded debug keystore can change the debug SHA-1, so verify the Google Cloud OAuth client registration before relying on sign-in.

## Current implementation and release status

**Not production-ready until every gate in the implementation-gap audit is closed with passing evidence.** The current app still contains legacy file-manager features and is not yet a complete Drive Semantic Search v1 implementation. In particular, the durable resumable Drive-to-Room indexer, complete PDF/Office/text extraction pipeline, trusted mobile embedding proxy/App Check, offline pinning and eviction, safe JSON index import/export, complete product-specific navigation/actions, and real-device OAuth/Drive verification remain release blockers.

See:
- [Drive Semantic Search implementation-gap audit](docs/DRIVE_SEMANTIC_SEARCH_IMPLEMENTATION_GAP.md)
- [Mobile embedding authentication requirements](docs/ANDROID_EMBEDDING_AUTH.md)
- [Architecture Guide](docs/ARCHITECTURE.md)
- [Security Whitepaper](docs/SECURITY_WHITEPAPER.md)
- [Production Readiness Audit](docs/PRODUCTION_READINESS_2026-08-31.md)

Cloud restore remains fail-closed until download, integrity verification, atomic staging, rollback, and recovery tests are complete. A green CI run alone does not establish production readiness.
