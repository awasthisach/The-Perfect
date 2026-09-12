# Security and CI Status

This repository uses GitHub's CodeQL Default Setup as the authoritative CodeQL configuration. A repository-owned Advanced CodeQL workflow must not be enabled concurrently with Default Setup because GitHub rejects SARIF uploads from the advanced configuration in that state.

## Current verified state

- Canonical branch: `main`.
- Application CI has a passing run on the current hardening line, including unit tests, lint and debug APK assembly.
- Release workflow is fail-closed for signing, Firebase configuration, CPAS, licensing and APK verification.
- SQLCipher production migration is in the current mainline.
- The previously added repository-owned Advanced CodeQL workflow was removed rather than merged because it conflicted with CodeQL Default Setup.
- Debug and release signing are intentionally separated; release tasks require explicit production signing environment variables and never fall back to the Android debug keystore.

## Remaining external configuration

Black Duck scanning and CodeQL Default Setup are GitHub-side security configuration concerns. They must be configured through the repository/organization security settings when required; they are not replaced by weakening repository workflows.

## Release rule

A green application build alone is not a production-release approval. Release promotion requires the signed-release workflow and its fail-closed verification gates to pass.
