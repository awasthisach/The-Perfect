package com.vvf.smartmanager.core.data.storage

/**
 * Pure storage-boundary policy (STORAGE-INV-001 / PROD-001).
 *
 * Fail-closed rules:
 * - Empty approved roots → deny everything
 * - Blank / null-byte paths → deny
 * - Path segments "." or ".." → deny (callers should canonicalize; this is defense-in-depth)
 * - Candidate must be exactly a root or a strict descendant (separator-aware, no prefix confusion)
 *
 * Callers should pass already-canonical absolute paths (File.canonicalFile.absolutePath).
 */
object StoragePathPolicy {

    fun isPathWithinApprovedRoots(
        candidateAbsolutePath: String,
        approvedRootAbsolutePaths: List<String>
    ): Boolean {
        if (candidateAbsolutePath.isBlank()) return false
        if (approvedRootAbsolutePaths.isEmpty()) return false
        if (candidateAbsolutePath.indexOf('\u0000') >= 0) return false
        // Defense-in-depth: reject non-canonical relative segments even if caller forgot to canonicalize
        if (containsDotSegment(candidateAbsolutePath)) return false

        val sep = java.io.File.separatorChar
        val candidate = candidateAbsolutePath.trimEnd(sep)
        return approvedRootAbsolutePaths.any { rootRaw ->
            val root = rootRaw.trimEnd(sep)
            if (root.isEmpty()) return@any false
            candidate == root || candidate.startsWith(root + sep)
        }
    }

    fun denialMessage(path: String, approvedRootAbsolutePaths: List<String>): String {
        return if (approvedRootAbsolutePaths.isEmpty()) {
            "Access denied: no approved storage roots could be discovered"
        } else {
            "Access denied: Path '$path' is outside approved storage boundaries"
        }
    }

    private fun containsDotSegment(path: String): Boolean {
        val sep = java.io.File.separatorChar
        // Normalize to check segments; also catch Unix-style even on mixed inputs
        val normalized = path.replace('/', sep).replace('\\', sep)
        val parts = normalized.split(sep)
        return parts.any { it == ".." || it == "." }
    }
}
