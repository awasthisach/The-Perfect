# Hilt migration — core and repositories

## Scope

Hilt now owns `CryptoSecurityManager`, encrypted `VVFDatabase`, `StorageManager`,
and the three repositories. Use cases, plugins, cloud/restore composition,
ViewModel factories, and WorkManager wiring remain manual.

The existing version catalog already declares Hilt Android/compiler/navigation
artifacts; the root and app already apply Hilt/KSP. Reuse those versions and KSP:
this change does not introduce kapt or alter AGP/Kotlin.

## Graph

- `AppModule`: singleton crypto, qualified live vault directory, database, DAOs,
  and storage manager. Context parameters use `@ApplicationContext`.
- `RepositoryModule`: singleton permission gate and repositories. The search
  repository always receives crypto; the permission gate is not omitted.
- `VVFApplication`: `@HiltAndroidApp` with injected core/repository fields and the
  same qualified directory used by manual backup and restore composition.
- `AppCompositionRoot`: only the not-yet-migrated cloud/restore factory remains.

## Security and lifecycle invariants

Hilt injects application fields during `super.onCreate()`, before the application's
own initialization body. Leaving recovery in that body could construct Room
against an interrupted DB/key state.

The database provider synchronously calls `AppStartupRecovery.prepare` first:

1. Remove abandoned plaintext staging without deleting rollback snapshots.
2. On devices, recover `filesDir/restore-work/snapshots` journals first.
3. Recover the legacy `cacheDir/restore-work/snapshots` location next.
4. Abort startup on invalid journals or missing rollback material.
5. Only then load SQLCipher, read the wrapped passphrase, and construct Room.

The passphrase is never a Hilt binding or singleton. Its caller-owned `ByteArray`
is wiped in `finally` on success and failure. The existing database builder still
owns a separate copy for SQLCipher's lazy open, wiped when the helper closes.
Database filename, Room migrations, Keystore aliases, passphrase formats, and the
`filesDir/vvf_vault_encrypted` path are unchanged. No plaintext production fallback
or destructive migration is introduced.

JVM/Robolectric still uses in-memory Room without native SQLCipher or device
restore recovery. Plaintext staging cleanup still runs on that path.

Restore callbacks still close Room before snapshots/swaps and restart the process
after a restore has closed it, including rollback/failure. Hilt singletons must
not reuse the closed database after restored disk/key state changes.

## Verification

Use JDK 17, Android platform 36, build-tools 36.0.0, and the checked-in Gradle 9.3.1
wrapper. Local JDK/SDK binaries and `local.properties` must not be committed.

```sh
./gradlew :app:assembleDebug
./gradlew testDebugUnitTest
./gradlew lintDebug
```

The new regression tests cover singleton identity, the shared vault path,
in-memory Room, MainActivity startup, plaintext cleanup, fail-closed invalid
journals, and current-before-legacy database/vault/wrapped-key recovery.

Tests being present does not imply they passed. Check the PR/CI results before
merging. Native SQLCipher and real-device startup additionally require the
existing instrumentation suites and a device/emulator, not just Robolectric.

## Historical compatibility note

An earlier Hilt 2.55 attempt with AGP 9.1.1 was reverted after the plugin reported
`Android BaseExtension not found`. The current catalog uses Hilt 2.60.1. Do not
reintroduce that old version or downgrade AGP in this migration; verify actual
build results when changing this toolchain.
