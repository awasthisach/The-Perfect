# VVF Smart Manager — Production Readiness Audit (2026-08-31)

## Verdict

**NOT YET PRODUCTION RELEASE-READY.**

Current application ID: `com.vvf.smartmanager`  
Compile/Target SDK: 36  
Min SDK: 24  
Gradle: 9.3.1  

A fresh Gradle build is not claimed from the audit sandbox because the Android SDK is not configured there. CI must provide the authoritative green verification.

## Architecture

The repository is a modular Kotlin Android/Compose application. The root Gradle graph contains `app`, multiple `core` modules, multiple `feature` modules, and optional `plugins`. `VVFApplication` is the composition root and currently uses manual dependency injection. `MainActivity` hosts Compose navigation and manually creates feature ViewModels through factory methods. Room/SQLCipher handle local persistence, WorkManager handles background work, Google Drive is the core cloud integration, and `CloudDriverSPI` defines optional cloud providers.

The modular Clean Architecture intent is real, but the source does not support claims that Hilt DI or generated type-safe navigation are already fully migrated.

## Ranked findings

| ID | Severity | Finding | Status |
|---|---|---|---|
| PROD-001 | Critical | A reusable debug keystore and credentials were embedded in Gradle source, enabling shared debug signing. | **Source remediation on red-team branch: embedded bytes/password/alias removed; Android default debug signing restored. Historical Git objects and Google OAuth SHA configuration still require review/rotation.** |
| PROD-002 | Critical | Cloud restore must be verified end-to-end, including production wiring and real backup recovery. | **Implementation exists (`FailClosedRestorePipeline`: download → checksum verification → isolated decrypt/staging → snapshot/apply/rollback) with unit tests. Production composition-root wiring, concrete provider adapters, clean-device recovery drill and durable rollback evidence are not yet verified.** |
| PROD-003 | High | Cloud upload must return and persist a durable provider ID. | **The current `CloudSyncUseCase` consumes `uploaded.remoteId`, rejects blank IDs via `DurableUploadContract`, and persists mappings in `CloudSyncDao`. Verify the actual SPI declaration and each real provider adapter/round-trip; the old Boolean-only claim is stale relative to the current call sites.** |
| PROD-004 | High | Manual dependency graph is concentrated in `VVFApplication` and ViewModel factories. | Open |
| PROD-005 | High | CI previously produced a debug-signed release artifact. | **Fixed on hardening branch** |
| PROD-006 | High | Fresh build verification is unavailable in the audit sandbox. | Open; CI required |
| PROD-007 | Medium | `MANAGE_EXTERNAL_STORAGE` and broad media permissions increase Play/privacy exposure. | Open |
| PROD-008 | Medium | `The-Perfect-main/` duplicates the repository source tree and creates maintenance ambiguity. | Open |
| PROD-009 | Medium | Supply-chain pinning must be checked across every workflow and installer, not inferred from one workflow. | **Partial remediation on red-team branch: the release workflow now installs FOSSA CLI v3.17.16 from a versioned GitHub Release and verifies the published SHA-256 checksum. All workflows and external downloads still require a repository-wide audit before this gate can close.** |
| PROD-010 | Medium | Temporary encrypted backup artifacts require strict lifecycle management. | Improved on hardening branch |
| PROD-011 | Medium | Existing audit documentation contains stale package/SDK/CI claims. | Superseded by this document |

## Changes applied

### Red-team branch follow-up

- Removed the embedded shared debug-keystore bytes and hard-coded password/alias from `app/build.gradle.kts`; Android Gradle's standard local debug keystore is now used.
- This does not purge historical commits. Consider the former key compromised; review the Google OAuth Android client fingerprints and replace the exposed signing identity where it is registered.
- Cloud restore, durable provider IDs, storage permission review and complete workflow supply-chain audit remain open. The release workflow now pins FOSSA CLI v3.17.16 and verifies its published SHA-256 checksum; this is partial supply-chain hardening, not a completed full audit.


- Release signing now requires explicit `KEYSTORE_PATH`, `STORE_PASSWORD`, and `KEY_PASSWORD`; no debug fallback is permitted.
- Signed releases moved to `.github/workflows/release.yml` and require explicit repository secrets.
- Duplicate Android CI workflow removed.
- CI now runs wrapper verification, unit tests, Android lint and debug assembly.
- CI uses least-privilege `contents: read` permissions and concurrency cancellation.
- Coroutine cancellation is preserved in archive/cloud operations.
- Temporary encrypted backup artifacts are deleted after upload attempts.

## Correct remediation order

1. Build/release invariants.
2. Security and permission boundaries.
3. Typed data/cloud contracts.
4. Data repositories and archive integrity.
5. Domain state machines, retries and durable jobs.
6. UI/ViewModel lifecycle and navigation.
7. Observability and redacted diagnostics.
8. Unit/repository/security/contract/CUJ tests.
9. CI supply-chain and dependency gates.
10. Internal rollout, recovery drills and staged release.

## Release gates

- [ ] CI green on the hardening branch.
- [ ] Signed release verified with the real production keystore.
- [ ] Provider upload returns durable remote IDs.
- [ ] Verified restore implemented and tested on a clean device.
- [ ] Storage permissions reduced/Play policy justified.
- [ ] GitHub Actions pinned to reviewed commit SHAs.
- [ ] Secret/dependency scanning enforced.
- [ ] Vault, database migration, cloud and critical-user-journey tests green.
- [ ] Internal release and rollback drill completed.

## Risk

**High for public production release; medium for continued engineering.**

The application should not be marketed as fully production-verified until CI is green and the remaining cloud recovery, provider durability, permission, supply-chain and release-validation gates are closed.
