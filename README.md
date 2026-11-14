# VVF Smart Manager

Offline-first Android file manager with an encrypted vault, local search/indexing,
optional Google Drive backup, and a gated path for remote embedding via a Cloudflare
Worker. Application ID: `com.vvf.smartmanager`.

This README is derived from repository source and build files. It does **not** claim
production readiness. Open release blockers are tracked in `docs/assurance/08-risk-register.yaml`
(PROD-008, PROD-009, PROD-010) and GitHub issues #136, #140, #141.

---

## Stack (from Gradle and source)

| Area | Implementation |
|------|----------------|
| Language | Kotlin 2.2, Coroutines/Flow |
| UI | Jetpack Compose + Material 3 |
| Architecture | Modular Clean Architecture (feature / core / plugins) |
| DI | Hilt (app module) + application composition root |
| Persistence | Room + SQLCipher (`net.zetetic:sqlcipher-android`) |
| Security | Android Keystore, AES-GCM vault paths, protected DB passphrase |
| Background | WorkManager |
| Auth / integrity | Firebase Auth, Firebase App Check (Play Integrity + reCAPTCHA) |
| Network | OkHttp, Retrofit, Moshi |
| OCR | ML Kit text recognition (Latin + Devanagari plugins) |
| Local search | Deterministic on-device token/category hash projection — **not** a neural/TFLite model |
| Cloud | Google Drive (user-initiated); optional Cloudflare embedding Worker |
| Build | Gradle Kotlin DSL + version catalog; compile/target SDK 36, min SDK 24 |

---

## Modules (`settings.gradle.kts`)

```text
app/                      Application entry, navigation, EmbeddingWorkerClient
core/common/              Shared utilities
core/model/               Shared models and contracts
core/security/            Keystore / crypto services
core/database/            Room + SQLCipher
core/database-compat/     SQLCipher compatibility fixtures and instrumented tests
core/data/                Repository implementations
core/domain/              Use cases and backup orchestration
core/background/          WorkManager workers
core/cloud-gdrive/        Google Drive integration
core/plugin-spi/          Plugin contracts
feature/explorer/         File browser
feature/vault/            Encrypted vault
feature/cleaner/          Storage cleaner
feature/search/           Local search UI
feature/cloud/            Cloud backup/restore UI
feature/settings/         Settings (offline-only, consent, identity)
feature/plugins/          Plugin host UI
plugins/plugin-ocr/       OCR provider
plugins/plugin-semantic-search/  Local hash-projection similarity
plugins/plugin-cloud-drivers/    Cloud driver SPI implementations
cloudflare/drive-semantic-embed/  Worker v4 source (TypeScript)
docs/assurance/           CPAS constitution, risk register, evidence ledger, ORT pins
```

---

## Embedding path (code contract)

**Worker** (`cloudflare/drive-semantic-embed/src/index.ts`):

- `POST /embed` requires:
  - `Authorization: Bearer <Firebase Auth ID token>`
  - `X-Firebase-AppCheck: <App Check token>`
  - Body: `{ "texts": string[], "mode": "query"|"document", "version"?: "2"|"3"|"4" }`
- Fail-closed when `GEMINI_PRODUCTION_APPROVED !== "true"`, missing tokens, bad origin,
  rate limit, or invalid body.
- Dimension fixed at 768; model configurable via env (default `gemini-embedding-2`).

**Android client** (`app/.../embedding/EmbeddingWorkerClient.kt`):

- Fail-closed: no network I/O when offline-only is on, remote-AI consent is missing,
  base URL is blank, or either Firebase Auth ID token or App Check token cannot be obtained.
- Does not treat a Google / Credential Manager ID token as a Firebase Auth ID token.
- Matches Worker limits (max 32 texts, 8000 chars each, version 2/3/4, dimension 768).

PROD-009 remains **open** until physical-device acceptance with real Firebase Auth +
App Check and Console evidence exists. Source wiring alone is not acceptance evidence.

---

## Build

Use the Gradle wrapper:

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

### Release signing

Release tasks refuse the debug keystore. Required environment variables:

```text
KEYSTORE_PATH
STORE_PASSWORD
KEY_ALIAS
KEY_PASSWORD
```

`google-services.json` is required for production release builds. The supported CI
path is `.github/workflows/release.yml` (secrets must be configured in the repository).

Secrets for local development: copy `.env.example` → `.env` (handled by the secrets
Gradle plugin; `FIREBASE_APPCHECK_DEBUG_TOKEN` is ignored by default).

---

## CI and assurance gates

| Workflow | Role |
|----------|------|
| `ci.yml` | Build, unit tests, lint, quality |
| `nightly-ort.yml` | ORT analyzer / scanner / evaluator / advisor / SBOM (exact candidate SHA) |
| `weekly-instrumented.yml` | Hard SQLCipher instrumented gate (emulator) |
| `cpas-gate.yml` | CPAS fail-closed verification + evidence staging |
| `worker-validation.yml` | Worker unit tests and dry-run bundle |
| `release.yml` | Signed release path |

Heavy candidate workflows cancel superseded **pull_request** runs only
(`cancel-in-progress: ${{ github.event_name == 'pull_request' }}`). Scheduled and
main-branch evidence runs are not cancelled mid-flight.

CPAS artifacts and the risk register live under `docs/assurance/`. Production status
is computed by `tools/audit/cpas_verify.py` and is intentionally **BLOCKED** while
PROD-008 / PROD-009 / PROD-010 remain open.

---

## Known limitations (evidence-based)

- Local “semantic” search is a deterministic hash projection, not a learned embedding model.
- Google Drive access tokens expire (~55 minutes); refresh-token rotation is not implemented.
- Remote embedding requires explicit remote-AI consent, Firebase identity, App Check,
  and a deployed Worker with `GEMINI_PRODUCTION_APPROVED=true`.
- Open release blockers: license/SBOM evidence (PROD-008 / #136), native Worker + App Check
  device acceptance (PROD-009 / #140), real-account OAuth / privacy / signing / device
  acceptance (PROD-010 / #141).
- Do not treat README, CI green on unit tests, or static configuration as production approval.

---

## Further reading

- `docs/assurance/` — CPAS constitution, risk register, evidence ledger, technology pins
- `SECURITY.md` / `SECURITY_STATUS.md` — security posture notes
- `TECHNICAL_DOCUMENTATION.md` — additional technical notes
- Issue tracker — blockers #136, #140, #141
