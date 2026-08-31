# VVF Smart Manager (Production Hardening)

> **VVF Smart Manager** is an offline-first Android file manager, encrypted vault, and privacy-focused productivity suite.

---

## Technology stack

- **Language:** Kotlin + Coroutines/Flow
- **UI:** Jetpack Compose + Material 3
- **Architecture:** Modular Clean Architecture / MVVM-style ViewModels
- **Dependency injection:** Manual application composition root (Hilt migration is not yet complete)
- **Navigation:** Jetpack Navigation Compose with centralized route constants
- **Persistence:** Room + SQLCipher
- **Security:** Android Keystore, AES-GCM vault encryption, protected database passphrase
- **Background work:** AndroidX WorkManager
- **OCR:** ML Kit plugin
- **Semantic search:** on-device plugin architecture
- **Cloud:** Google Drive core integration plus cloud-driver SPI
- **Build:** Gradle Kotlin DSL + version catalog
- **Compile/Target SDK:** 36
- **Minimum SDK:** 24

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

## Build locally

Use the Gradle wrapper rather than a system Gradle installation:

```bash
# Configure Android SDK through Android Studio or local.properties
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

### Production release

Production releases **must never use the Android debug keystore**. `assembleRelease` requires these environment variables:

```text
KEYSTORE_PATH
STORE_PASSWORD
KEY_ALIAS        # optional; defaults to upload
KEY_PASSWORD
```

The supported CI release path is `.github/workflows/release.yml`, which requires the corresponding GitHub Actions secrets.

## Current production status

This repository is under active production hardening and is **not yet independently verified for public release**. The current evidence-based readiness assessment is maintained in:

- [Production Readiness Audit](docs/PRODUCTION_READINESS_2026-08-31.md)
- [Architecture Guide](docs/ARCHITECTURE.md)
- [Security Whitepaper](docs/SECURITY_WHITEPAPER.md)

Cloud restore remains intentionally fail-closed until download, integrity verification, atomic staging, rollback, and recovery tests are complete.
