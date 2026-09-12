# VVF Smart Manager

> Offline-first Android file manager, encrypted vault, OCR/search, and privacy-focused cloud backup.

**Package:** `com.vvf.smartmanager` · **Min SDK 24** · **Compile/Target SDK 36** · **Kotlin + Jetpack Compose**

---

## What it does

| Area | Capability |
|------|------------|
| **Files** | Browse, categorize, batch ops, recycle bin |
| **Vault** | AES-GCM encrypted sandbox, PIN + biometric |
| **Search** | SQLite FTS4 + tags; on-device OCR (ML Kit); semantic plugin |
| **Cleaner** | Duplicates (size + hash) and junk cleanup |
| **Cloud** | Google Drive backup/restore (core); other drivers via SPI |

---

## Technology stack

- **UI:** Jetpack Compose + Material 3
- **Architecture:** Modular clean layers + MVVM-style ViewModels
- **DI:** Manual composition root (`VVFApplication` / `AppCompositionRoot`)
- **DB:** Room + **SQLCipher** (encrypted)
- **Security:** Android Keystore, vault crypto, protected DB passphrase
- **Background:** WorkManager
- **Cloud:** Google Drive REST v3 (`core/cloud-gdrive`) + cloud-driver SPI
- **Build:** Gradle Kotlin DSL + version catalog

---

## Project structure

```text
app/                  Application entry, navigation, composition root
core/common/          Shared utilities
core/model/           Shared models
core/security/        Keystore / crypto
core/database/        Room + SQLCipher
core/data/            Repositories, snapshot sources, permissions
core/domain/          Use cases, archive/restore pipelines
core/background/      WorkManager workers
core/cloud-gdrive/    Google Drive service
core/plugin-spi/      Plugin contracts
feature/*             UI feature modules (files, vault, search, cloud, …)
plugins/*             OCR, semantic search, optional cloud drivers
docs/                 Architecture and readiness docs
```

---

## Build locally

```bash
# SDK via Android Studio or local.properties
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

Debug APKs from CI: **Actions → VVF Smart Manager CI & Quality Gate → Artifacts → `vvf-smartmanager-debug-apk`**.

### Google Sign-In / Drive (device)

1. Copy `.env.example` values (or set `GOOGLE_WEB_CLIENT_ID` in your secrets flow).
2. Google Cloud Console: **Android** OAuth client with package `com.vvf.smartmanager` + your keystore **SHA-1**.
3. **Web** OAuth client ID is required for `requestIdToken` (see `.env.example`).
4. Enable Google Drive API on the same project.

Without matching SHA-1 you get Google Sign-In **Code 10**.

### Production release

Never use the debug keystore for store builds. `assembleRelease` needs:

```text
KEYSTORE_PATH
STORE_PASSWORD
KEY_ALIAS        # optional; default upload
KEY_PASSWORD
```

CI path: `.github/workflows/release.yml` (signing + unit/lint + FOSSA gates).

---

## Recent hardening (device-verified path)

| Fix | Notes |
|-----|--------|
| Drive upload **HTTP 400** | Multipart/`related` on `upload/drive/v3` (#115) |
| Drive folder **HTTP 404** `VVF_Backups` | Folder name ≠ fileId (#117) |
| Backup **Snapshot failed for: database** | Path resolve + empty placeholder (#118) |
| Release gates | Unit tests + lint before `assembleRelease` (#116) |
| OCR indexing | FTS rebuild after OCR index (#116) |

---

## CI quality gates

| Gate | Policy |
|------|--------|
| Unit tests / lint / assembleDebug | **Hard** |
| Emulator boot | Soft (infra flake tolerated on hosted runners) |
| SQLCipher instrumented (after boot) | Hard when emulator is up; weekly hard job |
| FOSSA analyze | Hard on main; advisory deps test may warn |
| Release workflow | Fail-closed: tests, lint, signing, license |

---

## Production status

- **Source + CI:** actively hardened; unit/lint/debug APK path is green on recent main commits.
- **Device:** Google Drive connect + cloud backup path exercised after #115–#118.
- **Not a Play Store GA claim:** treat public release as conditional on signed release workflow, real-device CUJs, and remaining SPI drivers (OneDrive/Dropbox/etc. remain limited).

Further reading:

- [Security status](SECURITY_STATUS.md)
- [Release notes](RELEASE_NOTES.md)
- [Hindi beginner guide](BEGINNER_GUIDE_HINDI.md)
- [Architecture](docs/ARCHITECTURE.md) (if present under `docs/`)
- [Security whitepaper](SECURITY_WHITEPAPER.md)

---

## License / contribution

Private/enterprise project unless otherwise stated by the owner. Open PRs against `main`; keep secrets out of git (use `.env.example` only as a template).
