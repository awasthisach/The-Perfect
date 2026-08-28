# Room schema exports

This directory stores Room database schema JSON exports used for migration validation.

- Configured via KSP: `room.schemaLocation = $projectDir/schemas`
- `VVFDatabase` has `exportSchema = true`
- Generate/update after schema changes:
  `./gradlew :core:database:kspDebugKotlin`

Commit the generated JSON files under this directory with migration PRs.
