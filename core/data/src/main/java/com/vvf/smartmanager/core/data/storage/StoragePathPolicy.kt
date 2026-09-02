package com.vvf.smartmanager.core.data.storage

/**
 * Pure storage-boundary policy (STORAGE-INV-001 / PROD-001).
 *
 * Fail-closed rules:
 * - Empty approved roots → deny everything
 * - Candidate must be exactly a root or a strict descendant (separator-aware)
 * - Null bytes and empty paths are rejected by callers via requireAllowedPhysicalPath
 *
 * Callers must pass already-canonical absolute paths (File.canonicalFile.absolutePath).
 */
object StoragePathPolicy {

    fun isPathWithinApprovedRoots(
        candidateAbsolutePath: String,
        approvedRootAbsolutePaths: List<String>
    ): Boolean {
        if (candidateAbsolutePath.isBlank()) return false
        if (approvedRootAbsolutePaths.isEmpty()) return false
        // Reject embedded nulls (defense in depth; canonical paths should not contain them)
        if (candidateAbsolutePath.indexOf('\u0000') >= 0) return false

        val candidate = candidateAbsolutePath.trimEnd(File.separatorChar)
        return approvedRootAbsolutePaths.any { rootRaw ->
            val root = rootRaw.trimEnd(File.separatorChar)
            if (root.isEmpty()) return@any false
            candidate == root || candidate.startsWith(root + File.separator)
        }
    }

    fun denialMessage(path: String, approvedRootAbsolutePaths: List<String>): String {
        return if (approvedRootAbsolutePaths.isEmpty()) {
            "Access denied: no approved storage roots could be discovered"
        } else {
            "Access denied: Path '$path' is outside approved storage boundaries"
        }
    }

    // Local alias — must be val (not const val): java.io.File.separator is not a compile-time constant
    private object File {
        val separator: String = java.io.File.separator
        val separatorChar: Char = java.io.File.separatorChar
    }
}
