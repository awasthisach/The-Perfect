# VVF Smart Manager

> Offline-first Android file manager, encrypted vault, and privacy-focused productivity suite.

**Release:** [v1.0.0](https://github.com/awasthisach/The-Perfect/releases/tag/v1.0.0)

---

## Download

| Item | Link |
|------|------|
| **GitHub Release v1.0.0** | https://github.com/awasthisach/The-Perfect/releases/tag/v1.0.0 |
| **Source (tag)** | https://github.com/awasthisach/The-Perfect/archive/refs/tags/v1.0.0.zip |
| **Clone tag** | `git clone --branch v1.0.0 https://github.com/awasthisach/The-Perfect.git` |

Play Store / signed AAB: build via `.github/workflows/release.yml` with release keystore secrets, then upload to Play Console Internal Testing → Production.

```bash
git checkout v1.0.0
./gradlew :app:assembleRelease   # requires KEYSTORE_PATH, STORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD
```

---

## Technology stack

- **Language:** Kotlin + Coroutines/Flow
- **UI:** Jetpack Compose + Material 3
- **Architecture:** Modular Clean Architecture / MVVM-style ViewModels
- **Persistence:** Room + SQLCipher
- **Security:** Android Keystore, AES-GCM vault, Firebase App Check (Play Integrity)
- **Background:** WorkManager
- **OCR / Search:** ML Kit plugin, on-device semantic search
- **Cloud:** Google Drive + multi-cloud SPI
- **Compliance:** CPAS gate, nightly ORT SBOMs, weekly instrumented SQLCipher tests
- **SDK:** min 24 · target/compile 36

## Project structure

```text
app/                 Application entry point and navigation
core/*               Security, database, domain, cloud, background
feature/*            User-facing feature modules
plugins/*            Optional OCR / semantic / cloud drivers
docs/                Architecture, security, production checklist
docs/assurance/      CPAS evidence, risk register, waivers
```

## Build locally

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

### Production release

`assembleRelease` requires:

```text
KEYSTORE_PATH
STORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
```

## Production status (v1.0.0)

- CPAS production gate: **PASS**
- PROD-008 / PROD-009 / PROD-010: **closed** (evidence in `docs/assurance/`)
- Checklist: [PRODUCTION_RELEASE_CHECKLIST.md](docs/PRODUCTION_RELEASE_CHECKLIST.md)
- Notes: [RELEASE_NOTES.md](RELEASE_NOTES.md)
- Security: [SECURITY_WHITEPAPER.md](docs/SECURITY_WHITEPAPER.md)

## License

Enterprise Proprietary — see repository policy.
