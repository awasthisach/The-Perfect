# VVF Smart Manager — Master Architecture Blueprint & Technical Documentation

**Package Name:** `com.vvf.smartmanager`  
**Target Platform:** Android (minSdk 24; targetSdk 36)  
**Technology Stack:** Kotlin, Jetpack Compose, Material 3, Android KeyStore, Room + SQLCipher, WorkManager, ML Kit OCR, deterministic token-hash semantic-search heuristic (128 dimensions; no TFLite model), Google Drive REST API.

---

## 1. System Architecture Overview

VVF Smart Manager is designed as an **Offline-First, Plugin-Extensible, Enterprise-Grade Smart File Manager & Secure Vault**.

```
+-----------------------------------------------------------------------+
|                              App Core                                 |
|  +---------------------+  +--------------------+  +----------------+  |
|  | File Explorer       |  | Duplicate Cleaner  |  | Secure Vault   |  |
|  | (Level 1-2 Cleaning)|  | (Level 1, 2 & 3)   |  | (Fake Vault)   |  |
|  +---------------------+  +--------------------+  +----------------+  |
|  +---------------------+  +--------------------+  +----------------+  |
|  | Core Search Engine  |  | Cloud Driver Core  |  | Settings & Sync|  |
|  | (FTS4 SQLite Core)  |  | (Google Drive)     |  | System         |  |
|  +---------------------+  +--------------------+  +----------------+  |
+-----------------------------------------------------------------------+
                                   |
                  Plugin Service Provider Interface (SPI)
                                   |
+-----------------------------------------------------------------------+
|                         On-Demand Plugins                             |
|  +-------------------+   +--------------------+   +----------------+  |
|  | ML Kit OCR        |   | AI Semantic Search |   | Cloud Plugins  |  |
|  | Plugin (12MB)     |   | 128-d hash heuristic |   | (OneDrive, S3) |  |
|  +-------------------+   +--------------------+   +----------------+  |
+-----------------------------------------------------------------------+
```

---

## 2. Core Modules Specification

### Core Modules (`:core`)
1. **`:core:database`**:
   - Encrypted Room Database backed by **SQLCipher**.
   - FTS4 Virtual Tables for full-text indexed searches across file names, tags, and extracted OCR text.
2. **`:core:security`**:
   - AES-256-GCM hardware-backed encryption via `AndroidKeyStore`.
   - PBKDF2 with HMAC-SHA256 key derivation for PIN/Password validation.
   - Dual-PIN architecture (Real Vault PIN & Decoy/Fake Vault PIN).
3. **`:core:background`**:
   - WorkManager orchestration for periodic junk scanning and storage indexing. Scheduled cloud backup is intentionally disabled until portable key wrapping and end-to-end restore are implemented and verified.
4. **`:core:plugin-spi`**:
   - Central SPI interfaces (`IOcrEngine`, `ISemanticSearchEngine`, `CloudDriverSPI`) isolating core features from downloadable modules.

### On-Demand Downloadable Plugins (`:plugins`)
- **`plugin-ocr`**: ML Kit Text Recognition plugin for extracting text from PDFs and images on-demand.
- **`plugin-semantic-search`**: Lightweight deterministic 128-dimensional token-hash heuristic. It is not a learned model or TFLite embedding model; semantic quality is limited and should not be represented as neural semantic search.
- **`plugin-cloud-drivers`**: Modular cloud sync drivers for OneDrive, Dropbox, NextCloud, S3, and local NAS storage.

---

## 3. Security Architecture & Threat Model

- **Database Encryption**: All SQLite databases are encrypted at rest using 256-bit AES keys managed through SQLCipher.
- **Vault File Storage**: Vault files are stored in app-private storage (`context.filesDir/vault/`) with filenames SHA-256 hashed and payload content encrypted with AES-GCM.
- **Fake Vault Mechanism**: Inputting the Decoy PIN opens an isolated secondary vault with mock/decoy items to protect against physical coercion.

---

## 4. Architectural Decision Log (ADR)

1. **ADR-001: Strict Core vs Plugin Separation**
   - *Decision*: Core APK contains zero heavy ML models or third-party cloud SDKs.
   - *Rationale*: Guarantees initial cold start < 10 seconds and keeps base APK download size minimal (< 15 MB).
2. **ADR-002: Offline-First Local Processing**
   - *Decision*: OCR and the current token-hash search heuristic execute locally. The current semantic-search implementation does not use TFLite or a learned embedding model.
   - *Rationale*: Absolute privacy compliance and zero server maintenance costs.
3. **ADR-003: Core Search Primacy**
   - *Decision*: FTS4 SQL search remains the primary search engine; AI Semantic Search works as an additive, non-blocking enhancement.
   - *Rationale*: Ensures instant, accurate search results even if AI models are disabled or downloading.

---

## 5. Brand Guidelines Compliance

- **Launcher Icon**: Custom golden leaf adaptive launcher icon with VVF brand logo motif.
- **Color Palette**:
  - Bhagwa Orange: `#F47B20` (Primary Action & Highlights)
  - Cosmic Blue: `#102B52` (Surface Canvas & Header Contrast)
  - Emerald Green: `#3FA34D` (Success & Verified Indicators)
  - Sky Cyan: `#5BC0EB` (Interactive Accents)
  - Soft Gold: `#D4A95A` (Vault & Security Badges)
