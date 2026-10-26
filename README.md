# VVF Smart Manager

> Offline-first Android file manager, encrypted vault, and privacy-focused productivity suite.
>
> Organization: [Vishva Vijayaa Foundation](https://thevvf.org/).

**Published GitHub artifact:** [v1.0.0](https://github.com/awasthisach/The-Perfect/releases/tag/v1.0.0) — debug APK only; not a production-signed release.

---

## Download

| Item | Link |
|------|------|
| **GitHub Release v1.0.0 (debug APK only)** | https://github.com/awasthisach/The-Perfect/releases/tag/v1.0.0 |
| **Source (tag)** | https://github.com/awasthisach/The-Perfect/archive/refs/tags/v1.0.0.zip |
| **Clone tag** | `git clone --branch v1.0.0 https://github.com/awasthisach/The-Perfect.git` |

A production-signed APK/AAB and Play Store release have **not been verified or published**. The current GitHub release contains a debug APK only. Do not treat that artifact as a production release.

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

## Production status (checked 2026-10-26)

**Status: BLOCKED — not yet verified as production-ready.** The CPAS workflow may finish successfully while its generated `cpas-status.json` still reports `production_status=BLOCKED`.

- PROD-008 (ORT license compliance): open — [issue #136](https://github.com/awasthisach/The-Perfect/issues/136).
- PROD-009 (deployed Worker v4 + Firebase Auth/App Check acceptance): open — [issue #140](https://github.com/awasthisach/The-Perfect/issues/140).
- PROD-010 (real-account OAuth, published privacy policy, production signing, physical-device acceptance): open — [issue #141](https://github.com/awasthisach/The-Perfect/issues/141).
- Evidence register: [08-risk-register.yaml](docs/assurance/08-risk-register.yaml).
- Checklist: [PRODUCTION_RELEASE_CHECKLIST.md](docs/PRODUCTION_RELEASE_CHECKLIST.md).
- Notes: [RELEASE_NOTES.md](RELEASE_NOTES.md).
- Security: [SECURITY_WHITEPAPER.md](docs/SECURITY_WHITEPAPER.md).

Do not announce production readiness until these blockers are closed with current, independently verifiable evidence.

## License

No `LICENSE` file is currently present in the repository. The owner must add the approved license or explicit proprietary terms before public redistribution; do not infer licensing from this README.
