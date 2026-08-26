# VVF Smart Manager (Master Release v1.0.0)

> **VVF Smart Manager** is an enterprise-grade, offline-first intelligent file manager, encrypted vault, and privacy-preserving AI productivity suite for Android.

---

## 🎨 Brand Identity & Palette
- **Launcher Icon**: Golden Leaf emblem with VVF signature inside.
- **Brand Colors**:
  - **Bhagwa Orange**: `#F47B20` (Primary Accent)
  - **Cosmic Blue**: `#102B52` (Primary Brand Anchor)
  - **Emerald Green**: `#3FA34D` (Success & Safe Indicators)
  - **Sky Cyan**: `#5BC0EB` (Secondary Information & Badges)
  - **Soft Gold**: `#D4A95A` (Vault & Premium Highlights)

---

## 🛠️ Frozen Technology Stack
- **Language**: Kotlin 2.0+ (Coroutines, Flow, Strict Null Safety)
- **UI Framework**: Jetpack Compose + Material Design 3 (Dynamic Color)
- **Architecture**: Modular Clean Architecture (MVI / MVVM)
- **Dependency Injection**: Hilt / Central Application Container
- **Navigation**: Jetpack Navigation Compose with Type-Safe Destinations
- **Local Persistence**: Room Database with SQLite FTS4 Full-Text Search
- **Database Encryption**: SQLCipher 4.5+ (AES-256-CBC)
- **Security & Keystore**: Android Keystore (`AndroidKeyStore`), AES-GCM-256, PBKDF2WithHmacSHA256
- **Background Tasks**: AndroidX WorkManager (Periodic maintenance & sync)
- **On-Demand OCR**: Google ML Kit Text Recognition (SPI Plugin)
- **Semantic Search**: TensorFlow Lite MobileBERT Embeddings (SPI Plugin)
- **Cloud Storage Core**: Google Drive REST API v3 + Jetpack Credential Manager
- **Multi-Cloud Drivers**: OneDrive, Dropbox, NextCloud, S3, WebDAV (SPI Plugins)
- **Build System**: Gradle Kotlin DSL (`build.gradle.kts`) with Version Catalog (`libs.versions.toml`)

---

## 📂 Project Documentation
- [Master Architecture Guide](docs/ARCHITECTURE.md)
- [Plugin Development Guide (SDK)](docs/PLUGIN_DEVELOPMENT_GUIDE.md)
- [Security & Privacy Whitepaper](docs/SECURITY_WHITEPAPER.md)
- [User Guide & Manual](docs/USER_GUIDE.md)
- [Production Audit Report](docs/PRODUCTION_AUDIT_REPORT.md)
- [Release Notes v1.0.0](RELEASE_NOTES.md)

---

## 🏗️ Building the Project

```bash
# Build Debug APK
gradle assembleDebug

# Run Unit & Robolectric CUJ Tests
gradle testDebugUnitTest

# Build Release APK
gradle assembleRelease
```
