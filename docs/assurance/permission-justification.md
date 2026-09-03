# Storage permission justification (PROD-007 / Issue #20)

**Package:** `com.vvf.smartmanager`  
**Product:** VVF Smart Manager — offline-first file manager + encrypted vault  

## Why broad access is requested

| Permission | API range | Why |
|---|---|---|
| `MANAGE_EXTERNAL_STORAGE` | 30+ | Core file-manager UX: browse primary shared storage tree, rename/move across folders, recycle-bin restore. Media-only grants cannot list arbitrary user documents. |
| `READ_MEDIA_IMAGES` / `VIDEO` / `AUDIO` | 33+ | Categorized media views when All-files access is not granted; degraded mode, not full substitute. |
| `READ_EXTERNAL_STORAGE` | ≤32 | Legacy full browse on older API levels. |

## Fail-closed runtime policy

- `StorageAccessPolicy` maps SDK + grants → `NONE` / `MEDIA_ONLY` / `ALL_FILES` / `LEGACY_FULL`.
- `StoragePermissionGate.requireBrowsePrimaryTree()` refuses listing when level is insufficient (throws; UI surfaces `needsStoragePermission`).
- No silent demo/sample file creation when grants are missing (PROD-004 / STORAGE-INV-002).

## Play policy alignment

- App is a **file manager** primary use case; All files access is limited to user-initiated browse/manage flows.
- Declaration/rationale for Play Console must state: “File manager for user-owned files on shared storage; vault data stays in app-private encrypted storage.”
- Vault and SQLCipher DB use app-private storage + Keystore — not dependent on `MANAGE_EXTERNAL_STORAGE`.

## Tests

- `StorageAccessPolicyTest` (unit)
- Explorer ViewModel catches gate denial and sets `needsStoragePermission`
