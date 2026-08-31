# Second Production Audit — 2026-08-31

This report records only findings supported by repository source inspection or observed GitHub Actions results. Unknowns are explicitly marked.

## A. Architecture

- Public repository, Kotlin primary language, Android application. GitHub metadata identifies Kotlin as the repository language and `main` as the default branch.
- Gradle/Kotlin DSL multi-module build. `settings.gradle.kts` includes one Android app plus core, feature, and plugin modules.
- App entry point: `com.vvf.smartmanager.VVFApplication`, with `MainActivity` as launcher activity.
- Dependency construction is manual in `VVFApplication`; Hilt plugins/dependencies are present but the application explicitly defers Hilt because of AGP compatibility.
- Core layers include model, common, security, database, data, domain, background, cloud-gdrive and plugin SPI. Feature layers include explorer, vault, cleaner, search, cloud, settings and plugins. Cloud/OCR/semantic-search plugins are separate modules.
- Local persistence is Room plus SQLCipher-backed encrypted database. Cloud integration includes Google Drive and plugin-driver abstractions for other providers. Firebase AI/App Check and Google services are configured in the app module.

## B. Ranked evidence-backed findings

### P0 — Credential exposure requires rotation
GitHub Security reported an open secret-scanning alert for a Google API key in `app/google-services.json`. The current `main` tree no longer contains that file and the GitHub commits query for that path currently returns an empty list, so the exposure is not currently present as a live tracked file. However, a credential already reported as publicly exposed must be revoked/rotated; deleting a file does not invalidate the credential.

### P0 — Storage authorization is fail-open
`StorageManager.requireAllowedPhysicalPath()` uses `allowedRoots.isEmpty() || ...`. The same file catches root-discovery exceptions and returns the partial/empty list. This means root discovery failure can turn a failed security-boundary calculation into authorization success. This finding is directly reproduced in the source and tracked as Issue #3.

### P0 — Production storage read paths manufacture demo data
`listDirectory()` invokes `ensureSampleFilesIfEmpty()` when a target directory is unavailable, and `listCategorizedFiles()` invokes `ensureSampleCategoryFiles()` when no results are found. A production file manager must not mutate user storage or synthesize user-visible files during reads.

### P1 — Five cloud providers were synthetic implementations
OneDrive, Dropbox, NextCloud, S3 and NAS drivers previously returned hard-coded quota values, hard-coded remote files, delayed to simulate work, and returned successful upload/download results without network/provider calls. Their existing tests explicitly asserted those synthetic behaviors. This is a functional integrity defect: the UI could report cloud state or successful transfers that never occurred.

Remediation in this branch replaces those implementations with explicit fail-closed placeholders and rewrites the tests to assert rejection/no synthetic data.

### P1 — Release signing previously fell back to debug credentials
`app/build.gradle.kts` previously selected `debugConfig` when the production keystore was absent. This could produce a release artifact signed with the debug key. The branch removes that fallback and requires explicit production signing material; CI uses an explicit unsigned verification mode instead.

### P1 — CI previously masked environment failures
The main CI used `|| true` around Android SDK license/package installation and generated a Gradle wrapper at runtime when missing. That allowed infrastructure problems to be hidden and made the build non-reproducible. The branch now verifies the committed wrapper, provisions a known SDK set, and fails on setup errors.

### P1 — Legacy storage compatibility was unnecessarily broad
The manifest enabled `requestLegacyExternalStorage="true"` while also requesting broad storage permissions. The branch removes the legacy compatibility flag and limits legacy write permission to Android 9 and below.

### P1 — Local credential/config files were not fully protected by ignore rules
The branch adds ignore rules for `.env.*`, signing keys, and all `google-services.json` files. This does not remediate historical secrets; credential rotation remains required.

### P2 — Storage metrics contain fabricated fallback values
`calculateStorageBreakdown()` returns a fixed 64 GiB total / 32 GiB used / 32 GiB free result when its outer calculation fails. This is not a truthful production metric. The correct behavior should surface an unavailable/error state rather than inventing device capacity.

### P2 — `getFileSize()` bypasses the storage boundary
`getFileSize(path)` directly constructs a `File` and reads it without passing through the same physical-path authorization boundary used by mutation methods. This creates an inconsistent security model and must be reviewed together with the storage-boundary remediation.

### P2 — MediaStore query requests a deprecated filesystem DATA column
`queryMediaStoreFiles()` requests `MediaStore.MediaColumns.DATA`. Modern Android storage should prefer content URIs and stream access rather than relying on raw filesystem paths. This is both compatibility and correctness risk on scoped-storage devices.

### P2 — Manual application-wide service locator is a maintainability risk
`VVFApplication` owns a large set of mutable `lateinit` services and constructs the entire graph in `onCreate()`. This makes startup heavy, increases coupling, and makes isolated testing harder. Hilt is already declared in the build but intentionally deferred; a future migration should be done after confirming the AGP/KSP compatibility constraints documented by the project.

## C. Verification evidence

A GitHub Actions run on the earlier hardening PR reached the Android build and executed 298 Gradle tasks before failing at `:app:validateSigningDebug` because `debug.keystore` was absent. The log also shows the SDK setup completed and Gradle 9.3.1 started successfully. The current branch explicitly provisions the ephemeral debug keystore before verification.

The second-pass branch has not yet produced a new completed CI run at the time this report was written, so this report does not claim a green build.

## D. Remediation order

1. Revoke/rotate exposed Google credential.
2. Fix storage authorization to fail closed; add traversal and symlink tests.
3. Remove all production demo-data mutation; move fixtures to tests/debug-only provider.
4. Remove fabricated storage metrics and direct-path reads outside the security boundary.
5. Keep cloud providers fail-closed until real SDK/API implementations exist.
6. Keep release signing fail-closed and separate unsigned CI verification from production release signing.
7. Make CI deterministic and fail on infrastructure errors.
8. Add security/static-analysis gates and expand unit/instrumentation coverage.
9. Migrate manual dependency construction only after the documented Hilt/AGP constraint is resolved.
10. Perform a clean-device validation of storage permissions, MediaStore behavior, vault authentication, backup/restore, and cloud synchronization before release.

## E. Production gate

The repository is **not production-ready** until P0 findings are closed, CI is green on a clean runner, release signing is provisioned with a real production keystore, and end-to-end backup/restore and cloud-provider behavior are validated against real services.
