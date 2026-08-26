# VVF Smart Manager — Master Architecture Guide

## 1. System Overview
**VVF Smart Manager** (`com.vvf.smartmanager`) is an enterprise-grade, offline-first intelligent file manager and security suite built for Android using modern Android Architecture Guidelines.

### Core Frozen Technology Stack
- **Language**: Kotlin 2.0+ (Strict Type-Safety, Coroutines, Flow)
- **UI Framework**: Jetpack Compose + Material Design 3 (M3 Dynamic Color System)
- **Architecture**: Clean Architecture (MVI / MVVM) + Modular Layering
- **Dependency Injection**: Hilt / Central Application Factory Architecture
- **Navigation**: Jetpack Navigation Compose with Type-Safe Destinations
- **Local Persistence**: Room Database 2.6+ with SQLite FTS4 Full-Text Indexing
- **Database Encryption**: SQLCipher 4.5+ AES-256-CBC with Salt & Key-Derivation
- **Security & Keystore**: Android Keystore Provider (`AndroidKeyStore`), AES-GCM-256, PBKDF2WithHmacSHA256
- **Background Engine**: AndroidX WorkManager (Battery-Optimized Periodic Sync & Housekeeping)
- **OCR Engine**: Google ML Kit Text Recognition (On-Demand SPI Plugin Module)
- **Semantic Search**: TensorFlow Lite (TFLite) MobileBERT On-Device Embeddings (SPI Plugin Module)
- **Cloud Storage Core**: Google Drive REST API v3 + Jetpack Credential Manager
- **Multi-Cloud Drivers**: OneDrive, Dropbox, Nextcloud, S3-Compatible, NAS (SPI Plugins)
- **Build System**: Gradle Kotlin DSL (`build.gradle.kts`) with Version Catalog (`libs.versions.toml`)

---

## 2. Multi-Module Project Structure

```
vvf-smart-manager/
├── app/                        # Main Android Application Target & Navigation Host
├── core/
│   ├── common/                 # Base Dispatchers, Extensions, Result Wrappers
│   ├── model/                  # Domain Models (FileItem, VaultItem, Category, PluginDescriptor)
│   ├── database/               # Room Entities, DAOs, SQLite FTS4, SQLCipher Config
│   ├── security/               # Android Keystore, AES-GCM-256 Vault Encryption, Biometrics
│   ├── data/                   # Repository Implementations (Storage, Vault, Search, Cleaner)
│   ├── domain/                 # Clean Architecture Use Cases (FileOps, DuplicateMatch, VaultAuth)
│   ├── background/             # WorkManager Workers (RecycleBinCleanup, CloudSync, Maintenance)
│   ├── cloud-gdrive/           # Google Drive REST Client & Credential Manager OAuth Flow
│   └── plugin-spi/             # Service Provider Interfaces (IOcrEngine, ISemanticSearchEngine, CloudSPI)
├── feature/
│   ├── explorer/               # Storage Overview, File Browser, Breadcrumbs, Category Tabs
│   ├── vault/                  # PIN Keypad, Biometric Auth, AES-Encrypted Vault Locker
│   ├── cleaner/                # Duplicate Hash Scanner (L1/L2), Junk Cleaner, AI Intelligence
│   ├── search/                 # Core FTS4 Search, Metadata Tagging, AI Semantic Matching
│   ├── cloud/                  # Multi-Cloud Sync Manager, Remote Directory Browser
│   ├── plugins/                # Plugin Store, Model Downloader, On-Demand Lifecycle Manager
│   └── settings/               # Dark/Light Theme, Security Policies, Storage Rules
└── plugins/
    ├── plugin-ocr/             # ML Kit Text Recognition Engine SPI Implementation
    ├── plugin-semantic-search/ # TFLite On-Device Embedding Generator SPI Implementation
    └── plugin-cloud-drivers/   # Multi-Cloud Drivers (Dropbox, OneDrive, NextCloud, S3, WebDAV)
```

---

## 3. Data Flow & Layer Responsibilities

```
┌────────────────────────────────────────────────────────┐
│                   Jetpack Compose UI                   │
│   (ExplorerScreen, VaultScreen, CleanerScreen, etc.)   │
└───────────────────────────┬────────────────────────────┘
                            │ UI Events / StateFlow
┌───────────────────────────▼────────────────────────────┐
│                       ViewModels                       │
│    (ExplorerViewModel, VaultViewModel, SearchVM...)    │
└───────────────────────────┬────────────────────────────┘
                            │ Suspend Invocation
┌───────────────────────────▼────────────────────────────┐
│                    Domain Use Cases                    │
│ (GetDirectoryFilesUseCase, LockFileInVaultUseCase...)   │
└───────────────────────────┬────────────────────────────┘
                            │ Repository Contracts
┌───────────────────────────▼────────────────────────────┐
│                    Data Repository                     │
│    (StorageRepository, VaultRepository, SearchRepo)    │
└─────────────┬────────────────────────────┬─────────────┘
              │ Local                      │ Cloud / SPI
┌─────────────▼──────────────┐ ┌───────────▼─────────────┐
│  Room DB (FTS4+SQLCipher)  │ │   Plugin SPI Registry   │
│   + Android Keystore API   │ │ (ML Kit, TFLite, GDrive)│
└────────────────────────────┘ └─────────────────────────┘
```

---

## 4. Performance & Cold Start SLA

- **Target Cold Start**: < 10.0 seconds on physical devices (< 2.0 seconds on JVM/Robolectric benchmark).
- **Background Deferral**: WorkManager jobs and heavy AI model initializations are deferred to background coroutines (`Dispatchers.IO` with `SupervisorJob`).
- **Memory Pressure Policy**: Handles `ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL` and `onLowMemory()` by dumping temporary OCR buffers, releasing inference caches, and initiating garbage collection.
- **Database Query SLA**: Indexed queries execute in < 100ms for batches exceeding 100 items.
