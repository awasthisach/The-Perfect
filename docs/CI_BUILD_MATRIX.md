# CI Build Matrix

The CI quality gate intentionally runs in dependency order:

1. Gradle wrapper verification
2. `testDebugUnitTest`
3. `lintDebug`
4. `assembleDebug`

Release signing and Firebase production configuration are not required for the debug/test/lint path. They are validated only by the protected release workflow.

Android SDK provisioning is explicit and limited to the API 36 platform, Build Tools 36.0.0, and platform-tools. License acceptance is non-interactive on the hosted runner.
