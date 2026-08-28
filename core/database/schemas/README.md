# Room schema export (tracked in git)

- `exportSchema = true` on `VVFDatabase`
- KSP arg: `room.schemaLocation = $projectDir/schemas`
- Version **2** snapshot: `com.vvf.smartmanager.core.database.VVFDatabase/2.json`

## Regenerate after entity changes

```bash
./gradlew :core:database:kspDebugKotlin
# commit any updated JSON under schemas/
```

If KSP rewrites `identityHash`, commit the new file — that is expected.

## Policy

Schema JSON is **source of truth for migrations**. Do not delete historical version files (`1.json`, `2.json`, …).
