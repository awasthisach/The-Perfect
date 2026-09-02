# CPAS Evidence Policy

## Required evidence record

Every verification record must identify the control, test, source commit, CI run and job, execution timestamp, environment, result, expiry, and retained artifact. Artifact-bearing records should include the artifact path and SHA-256 digest. A record without these fields is not production evidence.

## Freshness

Evidence is valid only for the source commit or an explicitly compatible release input. Default validity is 30 days for routine quality controls and one release cycle for release artifacts. Security, cryptography, database, restore, signing, and permission evidence must be regenerated after a relevant code, dependency, toolchain, manifest, schema, or workflow change.

## Retention and immutability

CI must retain the generated status, test reports, coverage reports, artifact digests, and provenance references. The ledger stores references and metadata; it must not store secrets, PINs, tokens, plaintext vault contents, or unreproducible screenshots as the sole proof.

## Invalid evidence

Skipped, cached, disabled, flaky, locally-only, failed, expired, malformed, or commit-mismatched evidence cannot support `PASS`. A failed test may remain in the ledger as `failed` evidence, but it keeps the relevant gate blocked.

## Review

A control owner reviews evidence for correctness. A security reviewer is required for critical security, cryptography, storage, database, cloud restore, and release-signing controls. CI-generated evidence may identify the automation as the reviewer only when the verifier has checked the record and the workflow is protected.
