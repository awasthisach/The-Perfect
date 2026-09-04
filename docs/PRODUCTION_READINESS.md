# VVF Smart Manager — Production Readiness

Last updated: 2026-09-05 (post PR #73 merge + honest-worker pass)

## Goal

World-class production-grade Android app: clean multi-module architecture, encrypted local data, honest cloud APIs, reliable CI, maintainable code.

## Status summary

| Area | Status | Notes |
|------|--------|--------|
| CI unit tests + release APK | ✅ | Green on main lineage |
| Java 17 all modules | ✅ | |
| Gradle wrapper | ✅ | |
| SQLCipher DB (no silent prod fallback) | ✅ | In-memory only under Robolectric |
| Network cleartext disabled | ✅ | `network_security_config.xml` |
| Drive REST (no fake data) | ✅ | Token required via `setAccessToken` |
| OAuth skeleton + Activity Result | ✅ | Needs your Google Client ID for live sign-in |
| Explorer permission recovery | ✅ | All-files banner + ON_RESUME reload (PR #73) |
| Vault PIN (async + explicit Submit) | ✅ | No main-thread crypto per digit (PR #73) |
| File indexing (real worker) | ✅ | FileIndexingRuntime + bounded upsert (PR #73) |
| Cloud false-green removed | ✅ | Plugins stay disconnected until real auth |
| Background Cloud/Junk/OCR workers | ✅ | Fail-closed (no simulated success) |
| Room exportSchema | ✅ | |
| ProGuard Retrofit/Moshi/Drive DTOs | ✅ | |
| Hilt full DI | ⏸ | Hilt + AGP 9 blocker; manual DI stable |
| Live OAuth client IDs | ⏸ | Needs Google Cloud SHA-1 + Web client ID |
| Biometric CryptoObject binding | ⏸ | Next security gate |
| Play release keystore in CI | ⏸ | Optional secrets; debug fallback for CI |

## Architecture (verified)

- **App:** `com.vvf.smartmanager` — Compose UI, `VVFApplication` manual DI graph
- **Core:** common, model, security (Keystore/PBKDF2), database (Room+SQLCipher), data, domain, background, cloud-gdrive, plugin-spi
- **Features:** explorer, vault, cleaner, search, cloud, settings, plugins
- **Plugins:** OCR, semantic-search, cloud-drivers

## Security baseline

- `allowBackup=false`
- Encrypted SQLCipher database; passphrase from Android Keystore path
- Vault PIN with lockout + decoy support; async verification
- No simulated Google Drive payloads in production service
- Secret scanning: do not re-commit API keys; use `.env` / CI secrets

See also: [PRODUCTION_STATUS_2026-09.md](PRODUCTION_STATUS_2026-09.md) for scored readiness and next gates.

## Operator steps (you)

1. Google Cloud: Android + Web OAuth clients; put Web client ID in `.env` as `GOOGLE_WEB_CLIENT_ID`
2. Device verification: All-files → Explorer list → Vault Unlock → Search after index
3. After green CI: download **room-schemas** artifact and commit generated JSON if present
4. For Play: upload real keystore secrets (`KEYSTORE_PATH`, passwords)
5. Rotate any historically leaked Google API keys

## Deferred (documented, not blocking debug builds)

- Hilt migration when Hilt supports AGP 9 (or pin AGP 8.x on a branch)
- Broader unit/UI test coverage toward 70%+
- Real multi-cloud driver implementations beyond Drive
- Full biometric CryptoObject vault unlock

## Definition of done (current phase)

- [x] Main CI green (tests + release APK path)
- [x] Material security blockers addressed (DB fallback, cleartext, fake cloud)
- [x] Drive production path + OAuth wiring
- [x] Explorer / Vault / Indexing honesty fixes (PR #73)
- [x] Background workers fail-closed when pipelines missing
- [x] Production readiness documented with honest scores
