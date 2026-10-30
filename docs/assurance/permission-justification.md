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


## Play declaration status and minimization

The manifest currently declares `MANAGE_EXTERNAL_STORAGE`, legacy `READ_EXTERNAL_STORAGE` (API 32 and lower), and `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO`, and `READ_MEDIA_AUDIO` (API 33+). The broad all-files permission is justified only by the app's primary file-manager flows; media grants provide a degraded media-only mode when all-files access is absent. Runtime access is expected to remain user-initiated and fail-closed when the required grant is missing.

These source declarations do **not** mean Google Play has approved the All files access declaration. Before submission, the release owner must verify the merged release manifest, remove any permission not required by the shipped feature set, complete the Play Console All files access declaration with the file-manager rationale above, and verify the Data safety / app-content forms. Approval status is currently **not verified**. No location, contacts, camera, or microphone permission is declared directly in `app/src/main/AndroidManifest.xml`; transitive merged-manifest output still requires release-build inspection.
