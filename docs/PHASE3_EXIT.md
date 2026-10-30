# Phase 3 exit — vault / PIN / portable backup + same-SHA ORT/CPAS (owner criteria)

**Status: PHASE 3 BLOCKED**

**Candidate branch:** `rebuild/stabilize-v1`  
**Recorded candidate SHA (verify before close):** `c78c4a2b7176c23cd6ed7e13aac7e387e45eaf2f`  
**Date:** 2026-10-30

Owner Phase-3 exit criteria require implementation tests **and** ORT SBOMs + CPAS PASS on the **exact** candidate SHA. Implementation evidence is green; ORT/CPAS are not.

## Implementation checklist (code + CI)

| Criterion | Result | Evidence |
|-----------|--------|----------|
| PIN lockout monotonic | PASS | `CryptoSecurityManager` + `SystemClock.elapsedRealtime()` |
| Lockout state authenticated | PASS | Keystore AES-GCM `vault_lockout_state_v2` |
| Portable backup crypto | PASS | `PortableBackupCrypto` / `ArchiveService` ≥12 passphrase |
| Restore fail-closed / recovery | PASS | `LocalRestoreApplier` + unit tests |
| Unit / lint / assembleDebug | PASS | CI run **41037972964** success on `c78c4a2b…` |
| SQLCipher hard gate | PASS | Hard gate run **41037972737** success on `c78c4a2b…` |

## ORT / CPAS (blocking)

| Criterion | Result | Evidence |
|-----------|--------|----------|
| ORT completed on exact SHA | **BLOCKED** | Run **41037972683** pending with **0 jobs** (queued behind long in-progress ORT on older PR SHAs; concurrency `cancel-in-progress: false`) |
| `ort-bom.cyclonedx.json` / `ort-bom.spdx.json` / `ort-status.json` | **MISSING** | `docs/assurance/` has no `ort-*` BOM files on this SHA |
| CPAS production_status PASS | **FAIL** | Run **41037972735** failure — fail-closed without ORT SBOMs |

## Process fix applied after this note

- `nightly-ort.yml`: remove `pull_request` trigger; `cancel-in-progress: true` so a freeze-branch dispatch can finish.
- Operator must **Cancel** stuck ORT runs on old SHAs, then **Actions → Nightly ORT Compliance Scan → Run workflow → branch `rebuild/stabilize-v1`**.

## Explicit non-claims

- Do not treat Phase 3 as PASS under owner criteria until ORT PASS + three artifacts + CPAS PASS on one SHA.
- Issues #136 / #140 / #141 remain open (ORT review, App Check console, OAuth/device).
- PR #187 must stay draft; no merge.

## Decision

**PHASE 3 BLOCKED** — blocker: ORT not completed for candidate SHA (stuck queue / missing SBOMs). Phase 4 not started.
