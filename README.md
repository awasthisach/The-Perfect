# VVF Smart Manager

**Offline-first Android file manager · Encrypted vault · Private cloud backup**

Search-friendly keywords: *Android file manager*, *encrypted vault*, *offline file manager*, *SQLCipher*, *Google Drive backup*, *privacy file manager*, *secure storage*, *VVF Smart Manager*.

VVF Smart Manager helps you browse, organize, and protect files on-device first. Sensitive content goes in an **encrypted vault** (Android Keystore + AES-GCM). Optional **Google Drive** backup uses a fail-closed restore path. Extra cloud providers are SPI stubs (“coming soon”) — they do not fake successful uploads.

Built by / for **Vishva Vijayaa Foundation** branding.

---

## Try a debug APK (CI build)

Latest **debug** APK from `main` CI (commit after Sprint-2 gates):

1. Open the successful run:  
   **https://github.com/awasthisach/The-Perfect/actions/runs/33837567047**
2. Scroll to **Artifacts** → download **`vvf-smartmanager-debug-apk`**
3. Unzip and install the `.apk` on a device/emulator (USB debugging / unknown sources as needed)

> Debug builds are for evaluation only — **not** Play-signed release. You must be logged into GitHub to download Actions artifacts.

Local build:

```bash
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/
```

---

## Features (honest)

| Area | Status |
|------|--------|
| On-device file browse / organize | Core |
| Encrypted vault (Keystore / AES-GCM) | Core |
| Room + **SQLCipher** database | Core |
| Google Drive backup / restore pipeline | Core (unit-tested; live E2E under waiver until secrets) |
| OneDrive / Dropbox / S3 / NAS plugins | **Fail-closed stubs** — not production multi-cloud yet |
| OCR / semantic search plugins | Plugin architecture |
| CPAS assurance (evidence + fail-closed verifier) | CI gated |

---

## Technology stack

- **Language:** Kotlin + Coroutines / Flow  
- **UI:** Jetpack Compose + Material 3  
- **Architecture:** Modular Clean Architecture  
- **DI:** Manual composition root (Hilt migration incomplete)  
- **Persistence:** Room + SQLCipher (FTS5)  
- **Security:** Android Keystore, AES-GCM vault encryption  
- **Background:** WorkManager  
- **Cloud:** Google Drive + cloud-driver SPI  
- **Build:** Gradle KTS + version catalog · compile/target SDK **36** · min SDK **24**

## Project structure

```text
app/                 Application entry + navigation
core/*               Security, database, domain, data, Drive, plugins SPI
feature/*            Explorer, vault, cloud, search, settings, …
plugins/*            Optional drivers / OCR / semantic search
docs/assurance/      CPAS policy, evidence ledger, gates, waivers
.github/workflows/   CI, CPAS, weekly instrumented hard gate, release
```

## Build & test

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

### Production release

Never ship with the debug keystore. `assembleRelease` needs:

```text
KEYSTORE_PATH
STORE_PASSWORD
KEY_ALIAS        # optional; default upload
KEY_PASSWORD
```

CI release path: `.github/workflows/release.yml` (signing secrets, SBOM, attestation).

## Production / assurance status

- Branch protection on `main` (PR + required checks)  
- CPAS fail-closed verifier + evidence ledger (real CI run IDs)  
- Weekly SQLCipher instrumented hard gate (emulator advisory on PR CI is compensated)  
- Residual risks documented: `docs/assurance/16-remaining-residuals.md`, waiver **WAIVER-021** (cloud E2E)

More detail:

- [docs/assurance/](docs/assurance/)  
- [Architecture](docs/ARCHITECTURE.md) (if present)  
- [Security notes](docs/SECURITY_WHITEPAPER.md) (if present)

## License / contribution

See repository license and `SECURITY` / assurance docs before shipping forks. Prefer PRs to `main`; direct pushes are blocked by design.
