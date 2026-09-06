# Applied Findings & Pre-Delete File Review (2026-09-06)

## 1. Pre-delete file review feature (new)

**Problem:** Delete confirmation only showed truncated file names.

**Change:** `DeleteConfirmDialog` is now **Review before delete**:
- Summary: file count · folder count · total size
- Scrollable per-item review: name, type, size/count, modified date, full path
- Permanent-delete checkbox with stronger warning UI

**File:** `feature/explorer/.../components/ExplorerDialogs.kt`

## 2. Index stale-row reconciliation

**Problem:** Deleted/moved files stayed in FTS/index forever.

**Changes:**
- `FileDao.deleteStaleByPaths(paths)` (non-trash only)
- `FilePathSnapshot` includes `isTrash`
- Indexing: stale = indexed − scanned → chunked delete; preserve trash/favorite/tags/hash
- FTS rebuild after upserts and/or stale deletes

**Files:** `FileDao.kt`, `VVFApplication.kt`

## 3. Vault PIN change hardening

- Validate new PIN format first
- Old PIN via `matchesRealPin` (no lockout side effect)
- Reset failed attempts on success

**File:** `CryptoSecurityManager.kt`

## 4. Biometric CryptoObject clarity

Already bound in `VaultBiometricUi`. Success path comments + explicit crypto-bound check.

## 5. CloudDriverSPI durable upload identity (PROD-003)

**Problem:** Boolean upload + synthetic remote IDs.

**Change:** `uploadFile(...): CloudUploadResult?`; drivers return null when unavailable; `CloudSyncUseCase` never invents IDs.

**Files:** `PluginSPI.kt`, cloud driver plugins, `CloudSyncUseCase.kt`, tests

## 6. Settings biometric toggle wired

**Problem:** Local `remember { true }` disconnected from vault.

**Change:** `SettingsScreen(initialBiometricEnabled, onBiometricEnabledChange)` wired from `MainActivity` → `vaultAuthUseCase`.

## 7. WorkManager same-process configuration

`VVFApplication` implements `Configuration.Provider` so workers share the app process with `FileIndexingRuntime` bridges.

## Deferred

| Finding | Why |
|--------|-----|
| Full Hilt migration | AGP 9 blocker |
| MANAGE_EXTERNAL_STORAGE reduction | Play/policy + SAF redesign |
| Real multi-cloud OAuth | External credentials |
