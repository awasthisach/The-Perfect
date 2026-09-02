# VVF Continuous Production Assurance System (CPAS)

This directory is the repository's assurance control plane. It defines how product purpose, architecture, technology choices, security/reliability invariants, tests, risks, and release gates are linked to verifiable evidence. The controls are fail-closed: an unverified, stale, skipped, cached, or missing result cannot support a production `PASS`.

## Source-of-truth order

1. [`00-cpas-constitution.md`](00-cpas-constitution.md) defines status semantics, evidence principles, ownership, and source-of-truth rules.
2. [`04-technology-registry.yaml`](04-technology-registry.yaml) records critical technology contracts.
3. [`05-security-reliability-invariants.yaml`](05-security-reliability-invariants.yaml) records security and reliability invariants.
4. [`06-requirements-traceability.yaml`](06-requirements-traceability.yaml) maps requirements to controls, implementation areas, tests, and CI jobs.
5. [`07-test-matrix.yaml`](07-test-matrix.yaml) defines test levels and minimum traceability.
6. [`08-risk-register.yaml`](08-risk-register.yaml) tracks findings through CI-verified closure.
7. [`09-gate-catalog.yaml`](09-gate-catalog.yaml) defines gate owners, commands, thresholds, evidence, and blocking behavior.
8. [`evidence-ledger.schema.json`](evidence-ledger.schema.json) defines the evidence record contract; [`evidence-ledger.json`](evidence-ledger.json) stores records.
9. [`FINAL_CPAS_SHEET.md`](FINAL_CPAS_SHEET.md) is the human-readable policy/index and must not be treated as a manually maintained live-status database.

## Rule

A production claim is valid only when the corresponding requirement, control/invariant, automated verification, and CI evidence exist.

## Lifecycle

Purpose -> Requirements -> Architecture -> Technology contracts -> Invariants -> Tests -> CI evidence -> Risk remediation -> Release gate

## Remediation loop

Detect -> Collect evidence -> Classify -> Reproduce -> Root cause -> Official research -> Minimal repair -> Targeted test -> Relevant CI -> Record evidence -> Close

Findings are not closed merely because code was edited.

## Current implementation order

1. Establish repository inventory and purpose baseline.
2. Register critical technologies and their contracts.
3. Register critical security/reliability invariants.
4. Map requirements to tests and evidence.
5. Enforce production gates in CI.
6. Run the same evidence-driven loop against existing blockers.

## Verification

Run the deterministic verifier from the repository root:

```bash
python3 tools/audit/cpas_verify.py --root .
```

Run verifier self-tests:

```bash
python3 -m unittest discover -s tools/audit -p 'test_*.py' -v
```

The verifier emits `cpas-status.json`. It checks required artifacts, the 49-point sheet structure, canonical invariant IDs, traceability references and paths, risk blockers, evidence entry shape, duplicate evidence IDs, and evidence freshness. An empty ledger or any open critical/high finding keeps the computed status `BLOCKED` by design.

## Safety of automation

Low-risk mechanical defects may be automatically remediated. Security boundaries, permissions, cryptography, database migration, restore/recovery, and destructive data operations require controlled evidence-driven changes and regression tests.
