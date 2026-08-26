package com.vvf.smartmanager.core.model

/**
 * Candidate item for semantic embedding and vector similarity indexing.
 */
data class SemanticCandidate(
    val fileItem: FileItem,
    val textContent: String, // Combined filename, tags, extracted OCR text
    val embedding: FloatArray? = null
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SemanticCandidate) return false
        return fileItem.path == other.fileItem.path
    }

    override fun hashCode(): Int {
        return fileItem.path.hashCode()
    }
}

/**
 * Result of semantic vector similarity query.
 */
data class SemanticSearchResult(
    val fileItem: FileItem,
    val similarityScore: Float, // 0.0f to 1.0f (e.g. 0.88 = 88% semantic similarity)
    val matchedConcept: String? = null
)

/**
 * Options for AI Semantic Search query.
 */
data class SemanticSearchOptions(
    val similarityThreshold: Float = 0.80f, // Configurable threshold (70% - 95%)
    val maxResults: Int = 25,
    val requireModelReady: Boolean = true
)

/**
 * Pair of files with computed similarity score.
 */
data class NearDuplicatePair(
    val fileA: FileItem,
    val fileB: FileItem,
    val similarityScore: Float
)

/**
 * Cluster of semantically or visually similar files detected via on-device embedding cosine comparison.
 */
data class NearDuplicateCluster(
    val id: String,
    val baseFile: FileItem,
    val similarFiles: List<FileItem>,
    val averageSimilarity: Float,
    val selectedPaths: Set<String> = emptySet()
) {
    val allFiles: List<FileItem> get() = listOf(baseFile) + similarFiles
    val totalCount: Int get() = allFiles.size
    val totalSizeBytes: Long get() = allFiles.sumOf { it.sizeBytes }
}

/**
 * Suggested conceptual tag from on-device semantic analysis.
 */
data class AiSuggestedTag(
    val tagName: String,
    val confidenceScore: Float, // 0.0f to 1.0f
    val category: String = "Conceptual"
)

/**
 * Summary metrics of on-device AI intelligence analysis.
 */
data class AiIntelligenceSummary(
    val totalScanned: Int = 0,
    val nearDuplicateClustersCount: Int = 0,
    val suggestedTagsCount: Int = 0,
    val currentThresholdPct: Int = 80
)
