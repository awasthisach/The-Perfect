# Hilt Migration Plan (VVF Smart Manager)

## Current state (post-foundation)

- Manual DI lives in `VVFApplication.onCreate()`.
- Feature ViewModels use `ViewModelProvider.Factory` / `provideFactory`.
- Catalog already contains Hilt 2.55, KSP compiler, Navigation Compose, WorkManager Hilt.

## Goal

Replace manual DI with `@HiltAndroidApp` + `@Module` / `@InstallIn` + `@HiltViewModel` without changing product behavior.

## Phased rollout (dedicated PR recommended)

### Phase A — Foundation (safe, no behavior change)
1. Apply `alias(libs.plugins.hilt)` + KSP hilt-compiler on `:app`.
2. Annotate `VVFApplication` with `@HiltAndroidApp` (keep existing `onCreate` DI).
3. Add empty/skeleton modules under `app/src/main/java/.../di/`.
4. Keep all `provideFactory` paths working so CI stays green.

### Phase B — Core bindings
1. `@Module` `@InstallIn(SingletonComponent::class)` for:
   - `CryptoSecurityManager`, `VVFDatabase`, repositories, use cases, plugins, Drive.
2. Prefer constructor injection; use `@Provides` only where Android `Context` / builders are required.
3. Do **not** delete manual `onCreate` until Phase C verifies inject paths.

### Phase C — UI layer
1. `@AndroidEntryPoint` on `MainActivity`.
2. Each feature ViewModel → `@HiltViewModel` + `@Inject constructor`.
3. Compose: `hiltViewModel()` instead of `viewModel(factory = ...)`.
4. Remove `provideFactory` companions.
5. Remove leftover `lateinit` graph from `VVFApplication` once unused.

### Phase D — WorkManager / background
1. `@HiltWorker` + `HiltWorkerFactory` in `Configuration.Provider`.
2. Verify periodic indexing / junk scan still schedules.

## Constraints

- Encrypted SQLCipher DB must remain required in production (no silent in-memory fallback).
- Do not leak passphrase / PIN in logs.
- One module group per PR if CI time is limited; never merge a half-migrated ViewModel graph.

## Verification checklist

- [ ] `./gradlew testDebugUnitTest`
- [ ] `./gradlew assembleRelease`
- [ ] Vault unlock / lock CUJ
- [ ] OCR plugin toggle
- [ ] Cloud screen connect flow
