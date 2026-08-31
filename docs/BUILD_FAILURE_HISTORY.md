# Build Failure History

## 2026-08-31 — CI run 33350761024

Runner setup, JDK 17, Android SDK provisioning, `local.properties`, and Gradle wrapper verification succeeded. The material failure was `:plugins:plugin-cloud-drivers:compileDebugKotlin`.

Root cause: `CloudDriverSPI.uploadFile()` returned `CloudUploadResult`, while existing cloud driver implementations returned `Boolean`. This source-incompatible interface change stopped compilation before tests could execute.

The same run also reported missing `google-services.json` during debug configuration. Production release configuration remains fail-closed; non-release CI now ignores the missing Firebase file.

Remediation: restore the current Boolean SPI contract for compatibility. Any future CloudUploadResult migration must update the SPI, all implementations, and all consumers atomically and be verified by CI before merge.

## 2026-08-31 — CodeQL run 33386972502

CodeQL initialization itself succeeded, but the advanced workflow failed during its Gradle build at `:app:validateSigningDebug` because the build used a repository-local `debug.keystore` that was not present on the hosted runner.

The same CodeQL run also exposed a repository configuration conflict: the repository's GitHub Code Scanning default setup was enabled while an advanced CodeQL workflow was installed. GitHub therefore rejected the uploaded SARIF result with `CodeQL analyses from advanced configurations cannot be processed when the default setup is enabled`.

Remediation: use AGP's generated debug signing key instead of a checked-in debug keystore, stop the CodeQL workflow from installing the obsolete Android `tools` package, and remove the redundant advanced CodeQL workflow so the repository's enabled default setup is the single CodeQL authority.
