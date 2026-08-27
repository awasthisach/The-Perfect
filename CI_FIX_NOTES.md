# CI remediation

PR #1 requires restoration of executable permission for `gradlew`. Hosted CI failed with `./gradlew: Permission denied` after the PR removed the chmod step. This note is temporary audit evidence and should be removed once the workflow is corrected and hosted CI is green.
