# Stabilize residuals — rebuild/stabilize-v1

**Baseline:** `5af6b9e58d85ff3a4e39ed93480b5a6f4e262bbc`  
**Updated:** 2026-10-29

Only items below drive work on this branch. Do not reopen closed risk IDs without new evidence.

## Verified on baseline (source-level)

| Contract | Evidence |
|----------|----------|
| Portable backup + restore (#181) | Merged on main |
| Lockout Keystore + `elapsedRealtime` | `CryptoSecurityManager.kt` LOCKOUT_STATE_KEY + write sites |
| App Check fail-closed outside JVM tests | `VVFApplication.onCreate` no catch-and-skip |
| `loadLibrary("sqlcipher")` before encrypted DB | `VVFApplication` |
| Narrow FileProvider paths | `app/src/main/res/xml/file_paths.xml` |
| Full application composition (not stub) | ~457-line `VVFApplication` with `FileIndexingOutcome` sealed handling |

## Open residuals (work queue)

### Slice 1 — Security confirmation (this slice)

- [ ] Confirm unit tests for lockout + portable crypto still green on this SHA (CI or local).
- [ ] Confirm no remaining wall-clock-only lockout path in production code.
- [ ] Confirm biometric one-use proof still enforced end-to-end (unit coverage present).

### Slice 2 — Database / SQLCipher

- [ ] Room schema JSON committed under module schemas path if KSP export enabled.
- [ ] Legacy fixture DB asset present for compat tests when required by CI hard gate.
- [ ] Instrumented SQLCipher suite green on **this** branch HEAD (not only historical SHAs).

### Slice 3 — Application wiring freeze

- [ ] No second DI graph (Hilt runtime not partially enabled alongside manual root).
- [ ] `AppCompositionRoot` remains the only factory surface for app-scoped graphs.

### Slice 4 — Backup/restore field confidence

- [ ] Device or emulator smoke: portable backup on A → restore on B with passphrase.
- [ ] Interrupted restore recovery still passes instrumented/unit matrix on HEAD.

### Slice 5 — Drive / OAuth

- [ ] Token refresh fail-closed paths reviewed on HEAD (no silent success on refresh failure).
- [ ] Real-account Drive E2E remains residual unless waived with dated waiver.

### Slice 6 — CI honesty

- [ ] `python3 tools/audit/cpas_verify.py --root .` behavior understood: BLOCKED until ORT artifacts + non-local evidence exist.
- [ ] Nightly ORT produces `ort-status.json` PASS for a release candidate SHA.
- [ ] Required GitHub checks match real gates (do not require PASS production_status on every PR if policy is release-only).

### Slice 7 — Store / product honesty

- [ ] README does not claim production-ready while ORT/device residuals open.
- [ ] Privacy policy marked draft or published URL is real.
- [ ] Permission declarations match runtime requests.

## Explicit non-goals (until residuals above close)

- New Drive/semantic-search product features
- Hilt migration
- Multi-branch parallel “fix” PRs into main
- Rewriting the app from scratch

## Definition of done (NO-SHIP until all true on one SHA)

1. Unit + lint + assembleDebug green  
2. SQLCipher instrumented hard gate green on that SHA  
3. ORT SBOM PASS on that SHA  
4. CPAS production_status PASS (or release gate documents intentional BLOCKED with no ship)  
5. No open P0 vault/backup/lockout gaps  
6. Store/README claims match code  
