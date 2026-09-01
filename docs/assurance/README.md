# VVF Continuous Production Assurance System (CPAS)

This directory is the repository's assurance control plane. It defines how product purpose, architecture, technology choices, security/reliability invariants, tests, risks, and release gates are linked to verifiable evidence.

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

## Safety of automation

Low-risk mechanical defects may be automatically remediated. Security boundaries, permissions, cryptography, database migration, restore/recovery, and destructive data operations require controlled evidence-driven changes and regression tests.
