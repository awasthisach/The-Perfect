package com.vvf.smartmanager.core.plugin.spi

import com.vvf.smartmanager.core.model.AiSuggestedTag
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.NearDuplicateCluster
import com.vvf.smartmanager.core.model.SemanticCandidate
import com.vvf.smartmanager.core.model.SemanticSearchOptions
import com.vvf.smartmanager.core.model.SemanticSearchResult

/**
 * Standard SPI contract for On-Device AI Semantic Search Engine plugin.
 * Guarantees zero cloud telemetry, 100% on-device vector embedding and similarity scoring.
 */
interface ISemanticSearchEngine {
    val pluginId: String get() = "plugin.semantic.tflite"
    val displayName: String get() = "TFLite On-Device Semantic AI Search"
    val version: String get() = "1.0.0"
    val isEnabled: Boolean get() = true

    /**
     * Checks if the lightweight on-device TFLite model is downloaded and initialized.
     */
    fun isModelReady(): Boolean

    /**
     * Downloads/initializes the lightweight embedding model.
     * @param progressCallback Progress report from 0.0f to 1.0f.
     */
    suspend fun downloadModel(progressCallback: (Float) -> Unit = {}): Boolean

    /**
     * Generates a normalized high-dimensional semantic vector embedding for the given text.
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
     * Suggests conceptual tags and categories for a candidate based on on-device semantic projection.
     */
    suspend fun suggestTags(candidate: SemanticCandidate): List<AiSuggestedTag>

    /**
     * Vector Cosine Similarity computation:
     * cos(theta) = (A . B) / (||A|| * ||B||)
     */
    fun computeCosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float
}
