# Production status (evidence-based)

**Decision: BLOCKED — not production-ready**

Updated: 2026-10-21

## Fixed in source (`fix/p0-vault-nio-publish`)

| Item | Evidence |
|------|----------|
| Vault NIO publish | Commit `77ae65a` — `SecureVaultRepository` uses `Files.move` + shred temp |
| DriveAuthEvents bus | Commit `ef64bcd` — `DriveAuthEvents.kt` |
| Auth wire-up guide | `docs/fixes/P0-auth-callback-rotation.md`, `patches/001-mainactivity-driveauth-events.patch` |

`MainActivity` still uses `pendingGoogleDriveSignInCallback` until patch 001 is applied and CI-verified on that SHA.

## Blocks production (not closable by code alone)

| Blocker | Issue | Required |
|---------|-------|----------|
| PROD-008 ORT license | #136 | ORT artifacts on exact SHA + package review |
| PROD-009 App Check | #140 | Firebase App Check + real device Auth path |
| PROD-010 OAuth/privacy/signing/device | #141 | Console + privacy HTTPS + signed device QA |
| Lint fail PR #153 | CI | Fix from lint reports artifact |
| FOSSA App contexts | Settings | Disable FOSSA integration; update branch protection |
| Signing secrets | Actions | Keystore + google-services (never paste values in chat) |

## Required sequence

1. Apply MainActivity / VVFApplication callback removal (patch 001).
2. Fix Android lint (unit tests already pass on PR #153).
3. ORT PASS + human review (#136).
4. External gates #140 / #141.
5. Signed AAB from exact SHA + physical-device matrix.
6. Only then CPAS `production_status == PASS`.

Do not merge PR #153 to `main` while Build/Test/Lint or CPAS fail.
