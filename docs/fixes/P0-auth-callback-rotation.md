# P0: Activity config-change auth leak

## Problem
`pendingGoogleDriveSignInCallback` is Activity-scoped. On rotation it is cleared;
token is still applied to `googleDriveService` but UI never updates.

## Fix (already added: `DriveAuthEvents.kt`)

In `MainActivity` googleDriveSignInLauncher success path, replace:

```kotlin
callback?.invoke(result)
```

with:

```kotlin
DriveAuthEvents.emit(result)
```

Remove `pendingGoogleDriveSignInCallback` field and assignments.

In `VVFAppContent`:

```kotlin
LaunchedEffect(Unit) {
    DriveAuthEvents.results.collect { result ->
        // update Cloud UI / Toast
    }
}
```

## Status
- `DriveAuthEvents.kt` is on this branch.
- MainActivity wire-up is applied: sign-in and authorization callbacks emit through `DriveAuthEvents`. `DriveAuthEventsTest` covers callback delivery across collector changes. Physical-device account acceptance remains separate.
