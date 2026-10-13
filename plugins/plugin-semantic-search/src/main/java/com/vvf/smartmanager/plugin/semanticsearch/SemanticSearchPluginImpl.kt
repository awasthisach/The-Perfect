package com.vvf.smartmanager.plugin.semanticsearch

import com.vvf.smartmanager.core.model.AiSuggestedTag
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.NearDuplicateCluster
import com.vvf.smartmanager.core.model.SemanticCandidate
import com.vvf.smartmanager.core.model.SemanticSearchOptions
import com.vvf.smartmanager.core.model.SemanticSearchResult
import com.vvf.smartmanager.core.plugin.spi.SemanticSearchSPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Fail-closed semantic search adapter. No real neural provider is configured in this build.
 * Search, duplicate clustering, and AI-tag suggestions must not fabricate model output.
 */
class SemanticSearchPluginImpl : SemanticSearchSPI {

    @Volatile
    private var isModelDownloaded: Boolean = false

    override fun isModelReady(): Boolean = isModelDownloaded

    override suspend fun downloadModel(progressCallback: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        // No neural model is bundled or safely available from the current app configuration.
        // Never simulate a successful model download or advertise hash vectors as neural embeddings.
        isModelDownloaded = false
        false
    }

    override suspend fun generateEmbedding(text: String): FloatArray = withContext(Dispatchers.Default) {
        // No real neural model is configured. Returning a zero-length vector prevents callers
        // from mistaking deterministic token hashes or handcrafted category buckets for embeddings.
        FloatArray(0)
    }

    override suspend fun searchSimilar(
        query: String,
        candidates: List<SemanticCandidate>,
        options: SemanticSearchOptions
    ): List<SemanticSearchResult> = withContext(Dispatchers.Default) {
        if (!isModelReady() || query.isBlank() || candidates.isEmpty()) {
            return@withContext emptyList()
        }

        val queryEmbedding = generateEmbedding(query)
        val results = mutableListOf<SemanticSearchResult>()

        for (candidate in candidates) {
            val candidateEmbedding = candidate.embedding ?: generateEmbedding(candidate.textContent)
            val score = computeCosineSimilarity(queryEmbedding, candidateEmbedding)

            if (score >= options.similarityThreshold) {
                results.add(
                    SemanticSearchResult(
                        fileItem = candidate.fileItem,
                        similarityScore = score,
                        matchedConcept = null
                    )
                )
            }
        }

        results.sortedByDescending { it.similarityScore }.take(options.maxResults)
    }

    override fun computeCosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
        if (vectorA.size != vectorB.size || vectorA.isEmpty()) return 0f
        var dotProduct = 0.0
        var normA = 0.0
        var normB = 0.0
        for (i in vectorA.indices) {
            val a = vectorA[i].toDouble()
            val b = vectorB[i].toDouble()
            if (!a.isFinite() || !b.isFinite()) return 0f
            dotProduct += a * b
            normA += a * a
            normB += b * b
        }
        if (normA <= 0.0 || normB <= 0.0) return 0f
        // Ranking thresholds are expressed on a non-negative 0..1 similarity scale.
        return (dotProduct / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)))
            .toFloat()
            .coerceIn(0f, 1f)
    }

    override suspend fun findNearDuplicates(
        candidates: List<SemanticCandidate>,
        similarityThreshold: Float
    ): List<NearDuplicateCluster> = withContext(Dispatchers.Default) {
        if (!isModelReady() || candidates.size < 2) return@withContext emptyList()

        val validThreshold = similarityThreshold.coerceIn(0.70f, 0.95f)

        val candidateWithEmbeddings = candidates.map { candidate ->
            val emb = candidate.embedding ?: generateEmbedding(candidate.textContent)
            candidate.copy(embedding = emb)
        }

        val visited = mutableSetOf<String>()
        val clusters = mutableListOf<NearDuplicateCluster>()

        for (i in candidateWithEmbeddings.indices) {
            val base = candidateWithEmbeddings[i]
            if (visited.contains(base.fileItem.path)) continue

            val baseEmb = base.embedding ?: continue
            val similarFiles = mutableListOf<FileItem>()
            var similaritySum = 0f

            for (j in (i + 1) until candidateWithEmbeddings.size) {
                val target = candidateWithEmbeddings[j]
                if (visited.contains(target.fileItem.path)) continue

                val targetEmb = target.embedding ?: continue
                val score = computeCosineSimilarity(baseEmb, targetEmb)

                if (score >= validThreshold) {
                    similarFiles.add(target.fileItem)
                    similaritySum += score
                    visited.add(target.fileItem.path)
                }
            }

            if (similarFiles.isNotEmpty()) {
                visited.add(base.fileItem.path)
                val avgScore = similaritySum / similarFiles.size
                val allSorted = (listOf(base.fileItem) + similarFiles).sortedBy { it.lastModified }
                val defaultSelected = emptySet<String>()

                clusters.add(
                    NearDuplicateCluster(
                        id = "cluster_${base.fileItem.path.hashCode()}",
                        baseFile = allSorted.first(),
                        similarFiles = allSorted.drop(1),
                        averageSimilarity = avgScore,
                        selectedPaths = defaultSelected
                    )
                )
            }
        }

        clusters.sortedByDescending { it.averageSimilarity }
    }

    override suspend fun suggestTags(candidate: SemanticCandidate): List<AiSuggestedTag> =
        withContext(Dispatchers.Default) {
            // Handwritten keyword rules with invented confidence scores are not AI output.
            // Return no model suggestions until a real provider is authenticated and configured.
            emptyList()
        }

}
