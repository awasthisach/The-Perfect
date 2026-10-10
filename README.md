# Drive Semantic Search

Android-native Google Drive search app built with Kotlin, Jetpack Compose, Material 3, Room, Coroutines/Flow, and WorkManager. This project must not be packaged as a WebView wrapper.

## Product contract

- Google account selection is interactive; Drive OAuth and Firebase Auth must refer to the same account.
- Google Drive access is read/write only for user-confirmed upload, folder creation, move, and star actions. The app must never delete Drive files.
- Local keyword/full-text and metadata search remains available when neural search is disabled or unavailable.
- Neural search requires explicit, revocable consent. Consent defaults to off.
- No Gemini API key or other server secret belongs in the APK.
- File bytes belong in app-private storage. Backups must never include OAuth tokens, Firebase ID tokens, or API keys.
- Minimum supported Android version: API 26.

## Architecture

- app/: Compose application shell, preferences, and dependency wiring.
- core/cloud-gdrive/: Google OAuth/Firebase account linking, Drive REST API, paginated metadata listing, Drive changes cursor contracts, and explicit Drive mutation methods.
- core/domain/: local search and consent-gated semantic/keyword hybrid ranking.
- core/security/: Android Keystore AES-GCM protection and PBKDF2-HMAC-SHA256 vault PIN derivation.
- core/database/: Room/SQLCipher persistence.
- core/background/: WorkManager indexing.
- feature/*: UI features.
- docs/ANDROID_EMBEDDING_AUTH.md: mobile embedding authentication, consent, and proxy requirements.
- docs/DRIVE_SEMANTIC_SEARCH_IMPLEMENTATION_GAP.md: implementation and release-gate audit.

## Build and test

    ./gradlew testDebugUnitTest
    ./gradlew lintDebug
    ./gradlew assembleDebug

A release build must use managed release signing secrets. The repository must not contain a reusable signing key. The Google OAuth Android client must be registered with the actual package name and signing certificate fingerprints.

## Release status

**Not production-ready until all gates in the implementation-gap audit are verified.** In particular, the trusted mobile embedding proxy/App Check path, complete resumable Drive-to-Room indexing pipeline, on-device PDF/Office/text extraction, offline pinning and eviction, JSON index import/export, full screen flow, and real-device OAuth/Drive tests require passing evidence. A green CI run alone does not establish production readiness.
