# Drive Semantic Search

**Native Android application** for searching a user's own Google Drive files by keywords and meaning. The intended client is Kotlin + Jetpack Compose + Material 3; this project is not a website wrapper and must not use a WebView for its core experience.

## Product requirements

The binding product specification and staged acceptance gates are in [Drive Semantic Search migration plan](docs/DRIVE_SEMANTIC_SEARCH_MIGRATION_PLAN.md).

- Google account selection is interactive; Drive and Firebase sessions must represent the same account.
- Drive metadata and text are indexed locally. Keyword/metadata search remains usable if neural embeddings are unavailable or disabled.
- Neural embeddings are strictly opt-in. No Gemini API key belongs in the Android app.
- No Google Drive file deletion or trash operation is allowed.
- Offline file bytes belong in app-private storage. Index backup must never include OAuth tokens, Firebase ID tokens, or API keys.
- The embed worker is https://drive-semantic-embed.awasthi-sach.workers.dev. Android must not bypass its authentication/origin controls. Until a documented mobile client is explicitly authorized, use a trusted authenticated proxy or add a verified Android app-check path.

## Technology target

- Kotlin, Jetpack Compose, Material 3; minSdk 26.
- Coroutines and Flow; Room for metadata, extracted text, vectors, vault records and resumable indexing state.
- WorkManager batches of 50 with automatic continuation and a durable resume cursor.
- Local PDF/Office/plain-text extraction. ML Kit OCR only after explicit opt-in to full-content indexing.
- Drive sync uses the Changes API with a full-list fallback and a documented cap of approximately 20,000 files.

## Current status — migration in progress

This repository has existing Smart Manager modules and must be treated as a migration, not as an already-complete Drive Semantic Search product. The migration branch is removing unsafe build configuration and aligning the app identity and permission boundary first. A green build alone is not proof of production readiness.

Do not claim production release readiness until the gates in the migration plan have passing CI evidence, account/session and Drive workflows are verified on a physical Android device, security tests pass, and recovery/backup behavior has been exercised. Cloud restore must remain disabled/fail-closed until integrity verification, atomic staging and rollback are proven.

## Build and test

Use the Gradle wrapper:

    ./gradlew testDebugUnitTest
    ./gradlew lintDebug
    ./gradlew assembleDebug

A release build must use a separately managed production upload key injected by CI secrets. Never commit signing keys or embed their private material in Gradle scripts. Release verification and the remaining security gates are documented in the migration plan.
