# CI Security Baseline

- Workflow permissions are least-privilege; ordinary CI uses `contents: read` only.
- Pull request workflows do not use `pull_request_target`.
- Release signing is isolated from ordinary CI and requires explicit secrets.
- Release artifacts are produced only by the signed release workflow.
- Third-party GitHub Actions used by CI and release workflows are pinned to immutable commit SHAs with version comments.
- The repository no longer contains a self-modifying wrapper bootstrap workflow; the Gradle wrapper is committed and verified by CI.
- Signed release APKs are verified with `apksigner` and receive build provenance attestations.
- CI must not generate or use a debug keystore for release artifacts.
