# CI Security Baseline

- Workflow permissions are least-privilege (`contents: read`).
- Pull request workflows do not use `pull_request_target`.
- Release signing is isolated from ordinary CI and requires explicit secrets.
- Release artifacts are produced only by the signed release workflow.
- GitHub Action versions are reviewed as part of dependency maintenance; immutable commit pinning remains a hardening task.
- CI must not generate or use a debug keystore for release artifacts.
