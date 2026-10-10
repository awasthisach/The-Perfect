# VVF Smart Manager — Production Readiness

Last updated: 2026-10-10 (PR #128 migration audit; see current release gate below)

## Current release gate — 2026-10-10

**Release status: BLOCKED. A green unit-test/lint/debug-build workflow is not a production release approval.**

Verified from GitHub Actions for the migration branch:
- Unit tests, Android lint, debug APK assembly, and FOSSA analysis/test completed successfully on the last fully completed CI run before the vault-dialog cleanup.
- The follow-up CI for the vault-dialog cleanup is running; do not assume its result before it completes.
- SQLCipher compatibility instrumentation was **skipped**, not passed: the hosted runner could not boot the emulator without KVM. CPAS correctly reports production status `BLOCKED` until fresh instrumented evidence is recorded.
- No signed release APK has been verified from production signing secrets, and no physical-device acceptance run is recorded.

| Release gate | Current state | Evidence / action |
|---|---|---|
| Unit tests, lint, debug APK | Last completed run passed; latest code change pending CI | [Latest CI](https://github.com/awasthisach/The-Perfect/actions) |
| SQLCipher instrumentation | **Blocked / skipped** | Requires a KVM-capable runner or verified physical-device test; never record a skip as PASS |
| Google OAuth + Drive workflows | Not physically verified for this migration | Test sign-in/account matching, Changes sync, upload/move/star on a real Android device |
| Neural semantic search | **Disabled fail-closed** | Native App Check authentication and embedding backend must be implemented and live-tested |
| OpenAI provider | **Not integrated** | Worker currently uses Gemini; the Android app must never contain an OpenAI API key |
| Vault | PIN-based | Free-form password lock is not implemented; verify file-source handling before release |
| Cloud restore | Disabled/fail-closed | Must pass integrity, atomic staging, rollback, and clean-device recovery tests |
| Signed production release | Not verified | Requires production keystore, Firebase config, FOSSA credential, and signed-artifact verification |

Do not merge/promote this migration as production-ready until every release-blocking row has passing evidence. The CPAS workflow may finish green while its generated artifact says `BLOCKED`; inspect the artifact's `production_status.value`, not only the workflow conclusion.

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
