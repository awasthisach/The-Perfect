# Production Readiness Audit Report

**Repository:** `awasthisach/The-Perfect`
**Working branch:** `audit/fixes-2026-11`
**Base revision:** `bf35d8e4e8c7241c87df2ff73d39a751f17eb903`
**Report date:** 2026-11-10

## Executive summary

Phases 2–4 were completed on the audit branch. The implementation follows the selected policy:

- **F-2 Strict Offline-First:** enabled by default; every cloud operation is blocked while the toggle is ON. Cloud access is available only when the toggle is explicitly switched OFF.
- **Legacy Drive backups:** backups without a durable, valid SHA-256 checksum in Drive metadata are excluded from new-session history and are rejected before restore. No unverifiable legacy artifact is restored.

The Android SDK is not installed or configured in this sandbox, so Gradle compilation and Android unit-test execution could not be completed locally. Repository audit self-tests passed.

## Phase 2 — F-5 checksum verification

The existing verifier already recomputed SHA-256 from the downloaded artifact and failed closed for missing or malformed expected values. Added the missing blank-checksum regression test:

- `core/domain/src/test/java/com/vvf/smartmanager/core/domain/restore/impl/Sha256BackupVerifierTest.kt`
- Test: `blankExpectedChecksumFailsClosed`
- Commit: `2cb0b18 test(F-5): reject blank backup checksums`

The commit was pushed to `origin/audit/fixes-2026-11`.

## Phase 3 — F-2 strict offline enforcement

The offline policy is now enforced at two layers:

1. `CloudSyncUseCase` rejects authentication, listing, backup, restore, file sync, and backup-history requests when `networkAllowed` is false.
2. `GoogleDriveServiceImpl` rejects Drive API, download, upload, quota, and history calls before any external request. Disconnect still clears local credentials without attempting remote revocation while offline.

The application injects `networkAllowed = { !isOfflineOnlyModeEnabled() }`, using the existing persisted toggle in `VVFApplication`. The default remains offline-only (`true`).

Added regression coverage in `CloudSyncUseCaseTest.kt` for:

- blocking all cloud-domain operations in strict offline mode;
- rejecting a legacy backup with no durable checksum metadata.

## Phase 4 — durable Drive backup restore across sessions

Implemented durable checksum metadata and new-session history restoration:

- Drive list and upload response fields now include `appProperties`.
- Backup uploads write `appProperties.vvf_sha256` with the exact SHA-256 of the encrypted artifact.
- `GoogleDriveService.listCloudBackups()` maps Drive records to `CloudBackupInfo`.
- Only entries with a valid 64-character hexadecimal `vvf_sha256` are returned.
- `CloudViewModel` loads durable Drive backup history during initialization, so history survives process death and app restarts.
- `CloudSyncUseCase` uses the durable checksum for restore and rejects missing/invalid metadata before invoking the restore pipeline.

The legacy policy is intentionally fail-closed. A local checksum calculated after downloading an old artifact is not a trusted expected checksum and therefore is not sufficient to authorize restore.

## Files changed in phases 3–4

- `app/src/main/java/com/vvf/smartmanager/AppCompositionRoot.kt`
- `app/src/main/java/com/vvf/smartmanager/VVFApplication.kt`
- `core/cloud-gdrive/src/main/java/com/vvf/smartmanager/core/cloud/gdrive/DriveApi.kt`
- `core/cloud-gdrive/src/main/java/com/vvf/smartmanager/core/cloud/gdrive/GoogleDriveService.kt`
- `core/cloud-gdrive/src/main/java/com/vvf/smartmanager/core/cloud/gdrive/GoogleDriveServiceImpl.kt`
- `core/domain/src/main/java/com/vvf/smartmanager/core/domain/CloudSyncUseCase.kt`
- `core/domain/src/test/java/com/vvf/smartmanager/core/domain/CloudSyncUseCaseTest.kt`
- `feature/cloud/src/main/java/com/vvf/smartmanager/feature/cloud/CloudViewModel.kt`

## Validation evidence

### Passed

- `git diff --check`
- Static source assertions for balanced Kotlin braces, metadata key presence, checksum guard presence, and network guard presence.
- Repository audit self-tests: **87 tests passed**.

### Blocked by environment

The following commands were attempted but could not start because no Android SDK is installed or configured:

```text
./gradlew :core:domain:testDebugUnitTest :core:cloud-gdrive:testDebugUnitTest --no-daemon --console=plain
./gradlew :core:cloud-gdrive:compileDebugKotlin :core:domain:compileDebugKotlin :feature:cloud:compileDebugKotlin :app:compileDebugKotlin --no-daemon --console=plain
```

Failure:

```text
SDK location not found. Define a valid SDK location with ANDROID_HOME or sdk.dir in local.properties.
```

No fake SDK path or repository `local.properties` was added.

## Open release blockers

The previously identified external-evidence blockers remain open:

- **Issue #141:** real-account OAuth, privacy, signing, and device acceptance evidence.
- **Issue #140:** deployed Worker contract v4 and live authenticated acceptance.
- **Issue #136:** exact-SHA ORT dependency/license compliance evidence.

These cannot be truthfully closed by local source edits alone.

## PR disposition summary

- **#218:** strongest merge candidate after final required-check and dependency review.
- **#224:** candidate, but CodeQL neutral requires explanation before merge.
- **#173, #219, #196:** draft; not mergeable until made ready and reviewed.
- **#211, #208, #206:** hold until current CPAS evidence is present.
- **#213:** non-main base; do not merge directly into `main` without an explicit rebase/base decision.
- **#220, #221:** CPAS/ORT evidence failures; do not merge.
- **#222:** integration-base draft; not a direct main candidate.
- **#225:** audit validation draft; keep separate until exact-SHA CI validates the new commit.
- **#187:** CodeQL failure and draft status; do not merge.

## Next required action

Install/configure Android SDK platform 36 in the CI or validation environment, then run the affected-module compile and targeted test tasks against the exact pushed commit. Only after those checks and required GitHub protections pass should the audit PR be considered for merge.
