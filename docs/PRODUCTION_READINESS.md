# VVF Smart Manager — Production Readiness

Last updated: 2026-08-29 (post CI #61/#62 green)

## Goal

World-class production-grade Android app: clean multi-module architecture, encrypted local data, honest cloud APIs, reliable CI, maintainable code.

## Status summary

| Area | Status | Notes |
|------|--------|--------|
| CI unit tests + release APK | ✅ | Runs #61, #62 green |
| Java 17 all modules | ✅ | |
| Gradle wrapper 9.3.1 | ✅ | |
| SQLCipher DB (no silent prod fallback) | ✅ | In-memory only under Robolectric tests |
| Network cleartext disabled | ✅ | `network_security_config.xml` |
| Drive REST (no fake data) | ✅ | Token required via `setAccessToken` |
| OAuth skeleton | ✅ | `GoogleDriveAuth` + docs |
| Room exportSchema | ✅ | KSP generates JSON; CI uploads `room-schemas` artifact |
| ProGuard Retrofit/Moshi/Drive DTOs | ✅ | |
| Hilt full DI | ⏸ | Hilt 2.55 + AGP 9 BaseExtension blocker; manual DI stable |
| Live OAuth client IDs | ⏸ | Needs your Google Cloud SHA-1 + Web client ID |
| Play release keystore in CI | ⏸ | Optional secrets; CI uses debug fallback |
| Codecov/FOSSA tokens | ⏸ | Optional secrets |

## Architecture (verified)

- **App:** `com.vvf.smartmanager` — Compose UI, `VVFApplication` manual DI graph
- **Core:** common, model, security (Keystore/PBKDF2), database (Room+SQLCipher), data, domain, background, cloud-gdrive, plugin-spi
- **Features:** explorer, vault, cleaner, search, cloud, settings, plugins
- **Plugins:** OCR, semantic-search, cloud-drivers

## Security baseline

- `allowBackup=false`
- Encrypted SQLCipher database; passphrase from Android Keystore path
- Biometric hooks for vault
- No simulated Google Drive payloads in production service
- Secret scanning: do not re-commit API keys; use `.env` / CI secrets

## Operator steps (you)

1. Google Cloud: Android + Web OAuth clients; put Web client ID in `.env` as `GOOGLE_WEB_CLIENT_ID`
2. Wire Connect button → `GoogleDriveAuth.buildDriveSignInIntent` (see `docs/GOOGLE_DRIVE_SETUP.md`)
3. After green CI: download **room-schemas** artifact and commit generated JSON if present
4. For Play: upload real keystore secrets (`KEYSTORE_PATH`, passwords)
5. Rotate any historically leaked Google API keys

## Deferred (documented, not blocking release builds)

- Hilt migration when Hilt supports AGP 9 (or pin AGP 8.x on a branch)
- Broader unit/UI test coverage toward 70%+
- Real multi-cloud driver implementations beyond Drive

## Definition of done (this phase)

- [x] Main CI green (tests + release APK)
- [x] Material security blockers addressed (DB fallback, cleartext, fake cloud)
- [x] Drive production path + OAuth skeleton
- [x] CI archives schemas and test reports
- [x] Production readiness documented
