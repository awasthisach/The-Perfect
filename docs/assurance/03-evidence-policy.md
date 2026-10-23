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

## Generated candidate ledger versus historical records

`historical-evidence-ledger.json` preserves earlier audit records; it is not an input to production approval and is not asserted to satisfy the current generated-ledger schema. Never invent artifact provenance to upgrade these records.

The verifier defaults to `candidate-evidence.json` under `--root`. Generate it from completed Actions runs for the checked-out candidate:

```sh
candidate_sha="$(git rev-parse HEAD)"
python3 tools/audit/collect_evidence.py --revision "$candidate_sha" --output candidate-evidence.json
python3 tools/audit/cpas_verify.py --root . --candidate-sha "$candidate_sha" --ledger candidate-evidence.json
```

Absent candidate evidence remains BLOCKED. Relative `--ledger` paths are resolved under `--root`; absolute paths are also supported. CPAS waits within its run for the candidate's unit/native producers; successful tests still cannot close owner/device risks. Automatic producer triggers cover every CPAS push/PR candidate without path filters. For a manual dispatch, first dispatch CI, native and Worker checks on the same candidate ref; the read-only gate does not create producer runs. Candidate code is never executed through a privileged `workflow_run` or `pull_request_target` trigger.
