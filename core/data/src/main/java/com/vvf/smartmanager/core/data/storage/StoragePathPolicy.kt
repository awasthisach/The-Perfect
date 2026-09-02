package com.vvf.smartmanager.core.data.storage

/**
 * Pure storage-boundary policy (STORAGE-INV-001 / PROD-001).
 * Empty approved roots must fail closed and never broaden filesystem access.
 */
object StoragePathPolicy {

    fun isPathWithinApprovedRoots(
        candidateAbsolutePath: String,
        approvedRootAbsolutePaths: List<String>
    ): Boolean {
        if (approvedRootAbsolutePaths.isEmpty()) return false
        return approvedRootAbsolutePaths.any { root ->
            candidateAbsolutePath == root ||
                candidateAbsolutePath.startsWith(root + java.io.File.separator)
        }
    }

    fun denialMessage(path: String, approvedRootAbsolutePaths: List<String>): String {
        return if (approvedRootAbsolutePaths.isEmpty()) {
            "Access denied: no approved storage roots could be discovered"
        } else {
            "Access denied: Path '$path' is outside approved storage boundaries"
        }
    }
}
