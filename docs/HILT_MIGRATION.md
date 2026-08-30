# Hilt Migration Plan (VVF Smart Manager)

## Blocker (2026-08-28)

Enabling Hilt **2.55** on **AGP 9.1.1** fails at configuration time:

```
Failed to apply plugin 'com.google.dagger.hilt.android'.
Caused by: java.lang.IllegalStateException: Android BaseExtension not found.
```

Phase A was **reverted** so CI stays green. Manual DI in `VVFApplication` remains the runtime graph.

## Next attempt prerequisites

1. Upgrade Hilt to a release that supports AGP 9 (track [google/dagger](https://github.com/google/dagger/releases) — try **≥ 2.56** when available with AGP 9 notes).
2. Or temporarily pin AGP to 8.x only for a Hilt migration branch (not preferred for production track).
3. Apply order: `android.application` → `kotlin` → `ksp` → `hilt`.
4. Then `@HiltAndroidApp`, modules, `@HiltViewModel`, `@AndroidEntryPoint`.

## Phases (when unblocked)

### Phase A — Foundation
- Plugin + KSP compiler + `@HiltAndroidApp`
- Keep manual `onCreate` DI until ViewModels migrate

### Phase B — Core bindings
- `@Module` `@InstallIn(SingletonComponent::class)` for DB, crypto, repos, use cases

### Phase C — UI
- `@AndroidEntryPoint` on `MainActivity`
- `@HiltViewModel` + `hiltViewModel()`; remove `provideFactory`

### Phase D — WorkManager
- `@HiltWorker` + `HiltWorkerFactory`

## Verification

- `./gradlew testDebugUnitTest assembleRelease`
- Vault / OCR / Cloud CUJs
