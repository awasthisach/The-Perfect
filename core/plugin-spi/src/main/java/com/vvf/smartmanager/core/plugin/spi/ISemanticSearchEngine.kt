package com.vvf.smartmanager.core.plugin.spi

import com.vvf.smartmanager.core.model.AiSuggestedTag
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.NearDuplicateCluster
import com.vvf.smartmanager.core.model.SemanticCandidate
import com.vvf.smartmanager.core.model.SemanticSearchOptions
import com.vvf.smartmanager.core.model.SemanticSearchResult

/**
 * Compatibility SPI for on-device deterministic hashed text-similarity ranking.
 * Implementations must not describe token/category hashing as learned or neural embeddings.
 */
interface ISemanticSearchEngine {
    val pluginId: String get() = "plugin.semantic.hashed-text-similarity"
    val displayName: String get() = "On-Device Hashed Text Similarity"
    val version: String get() = "1.0.0"
    val isEnabled: Boolean get() = true

    /**
     * Checks whether the built-in deterministic hashing engine is ready. No model download is required.
     */
    fun isModelReady(): Boolean

    /**
     * Legacy compatibility hook. The built-in deterministic hashing engine ships in the app;
     * this method performs no model download and makes no network request.
     * @param progressCallback Reports initialization completion (1.0f).
     */
    suspend fun downloadModel(progressCallback: (Float) -> Unit = {}): Boolean

    /**
     * Generates a normalized deterministic token/category hash vector for text-similarity ranking.
     * This is not a learned embedding or neural model.
     */
    suspend fun generateEmbedding(text: String): FloatArray

    /**
     * Computes cosine similarities between query embedding and candidates,
     * filtering candidates above the similarity threshold (70% - 95%) and sorting by relevance.
     */
    suspend fun searchSimilar(
        query: String,
        candidates: List<SemanticCandidate>,
        options: SemanticSearchOptions = SemanticSearchOptions()
    ): List<SemanticSearchResult>

    /**
     * Detects near-duplicate clusters among candidate files using pairwise vector cosine similarity
     * with the specified threshold (e.g. 0.70f to 0.95f).
     */
    suspend fun findNearDuplicates(
        candidates: List<SemanticCandidate>,
        similarityThreshold: Float = 0.80f
    ): List<NearDuplicateCluster>

    /**
     * Suggests tags from deterministic on-device keyword/category rules.
     */
    suspend fun suggestTags(candidate: SemanticCandidate): List<AiSuggestedTag>

    /**
     * Vector Cosine Similarity computation:
     * cos(theta) = (A . B) / (||A|| * ||B||)
     */
    fun computeCosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float
}
