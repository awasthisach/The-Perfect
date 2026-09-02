# CPAS Constitution

**Schema version:** `1.0`  
**Document version:** `1.0.0`  
**Status:** `CURRENT` for governance rules; individual controls retain their own status.  
**Authoritative branch:** `main`  
**Source commit:** populated by CI evidence; this document does not contain a fabricated commit.  
**Review cadence:** quarterly and after any security, persistence, restore, release, or toolchain change.

## Purpose

The Continuous Production Assurance System (CPAS) is the repository’s evidence-driven control system for VVF Smart Manager. It separates policy from live verification and keeps production status blocked unless required evidence is reproducible and current.

## Status semantics

| Status | Meaning | May support production PASS? |
|---|---|---:|
| `CURRENT` | The control is implemented and verified by acceptable evidence for the referenced commit and environment. | Yes, subject to all other gates. |
| `TARGET` | Desired control or future-state design that is not yet fully enforced. | No. |
| `DRAFT` | Under review and not authoritative. | No. |
| `OBSERVED` | Static inference or an observed condition awaiting runtime verification. | No. |
| `SUPERSEDED` | Replaced by a newer control or document. | No. |

## Evidence principles

A claim is not verified by prose, file existence, a cached result, a skipped test, or a green unrelated job. Acceptable evidence identifies the control or invariant, test or verification, source commit, CI run, execution environment, result, and retained artifact or report. Evidence is stale after its declared expiry or when its source commit no longer matches the evaluated revision.

## Fail-closed rules

Open critical or high findings, an empty or malformed evidence ledger, missing required references, stale evidence, failed required tests, unknown control IDs, and inconsistent computed status keep the production result `BLOCKED`. Waivers must be explicit, approved, time-bounded, and linked to compensating controls; an expired waiver is itself blocking.

## Ownership and review

Each control and finding must have an owner. Security, cryptography, storage, database, cloud restore, release signing, and destructive-data controls require a named technical reviewer before closure. The verifier is deterministic, produces machine-readable output, and is tested against malformed and false-green inputs.

## Source-of-truth hierarchy

Machine-readable assurance artifacts are authoritative for current status: technology registry, invariant registry, test matrix, risk register, traceability map, gate catalog, evidence ledger, and generated CPAS status. `FINAL_CPAS_SHEET.md` is the human-readable policy/index; it must not duplicate live counts without a generated reference.
