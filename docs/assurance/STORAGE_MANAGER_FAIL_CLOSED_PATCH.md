# StorageManager.kt production wiring (apply to main)

## 1. requireAllowedPhysicalPath — replace body with:

```kotlin
    fun requireAllowedPhysicalPath(path: String): File {
        require(path.isNotBlank()) { "Physical path cannot be blank" }
        val candidate = File(path).canonicalFile
        val rootPaths = getAllowedStorageRoots().map { it.absolutePath }
        // Fail-closed: empty approved roots must never broaden access (PROD-001 / STORAGE-INV-001).
        require(StoragePathPolicy.isPathWithinApprovedRoots(candidate.absolutePath, rootPaths)) {
            StoragePathPolicy.denialMessage(path, rootPaths)
        }
        return candidate
    }
```

## 2. listDirectory — replace sample seeding with empty list:

```kotlin
        val targetDir = File(directoryPath)
        // Production listing must not create sample/demo files (STORAGE-INV-002).
        if (!targetDir.exists() || !targetDir.isDirectory) {
            return emptyList()
        }
```

## 3. listCategorizedFiles — remove ensureSampleCategoryFiles call:

Delete the block:
```kotlin
        if (results.isEmpty()) {
            ensureSampleCategoryFiles(category, results)
        }
```
