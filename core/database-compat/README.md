# database-compat

Instrumented SQLCipher compatibility checks against a **CI-generated** encrypted fixture.

## Fixture

Hard-gate workflow creates:

`src/androidTest/assets/legacy-4.5.4.db`

with passphrase `vvf-sqlcipher-compatibility-test` (see `.github/workflows/weekly-instrumented.yml`).

After a green hard-gate run, prefer recording SHA-256 of that DB in this README so drift is visible.

## Tests

- Correct passphrase reads expected row
- Wrong passphrase fails closed
