package com.vvf.smartmanager.plugin.semanticsearch

import com.vvf.smartmanager.core.model.AiSuggestedTag
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.NearDuplicateCluster
import com.vvf.smartmanager.core.model.SemanticCandidate
import com.vvf.smartmanager.core.model.SemanticSearchOptions
import com.vvf.smartmanager.core.model.SemanticSearchResult
import com.vvf.smartmanager.core.plugin.spi.SemanticSearchSPI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.security.MessageDigest
import java.util.LinkedHashMap

/**
 * Remote neural embedding adapter. Document text leaves the device only after explicit consent.
 * The Cloudflare worker validates Firebase Auth and App Check; no provider key is stored here.
 */
class SemanticSearchPluginImpl(
    private val client: CloudflareEmbeddingClient = CloudflareEmbeddingClient(),
    private val isEmbeddingConsentGranted: () -> Boolean = { false }
) : SemanticSearchSPI {

    private val vectorCache = object : LinkedHashMap<String, FloatArray>(CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, FloatArray>?): Boolean =
            size > CACHE_SIZE
    }

    override fun isModelReady(): Boolean {
        if (!isEmbeddingConsentGranted()) {
            clearCachedEmbeddings()
            return false
        }
        return client.hasSignedInUser()
    }

    /** Immediately drop in-memory vectors when the user revokes neural-search consent. */
    fun clearCachedEmbeddings() {
        synchronized(vectorCache) { vectorCache.clear() }
    }

    override suspend fun downloadModel(progressCallback: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        if (!isEmbeddingConsentGranted() || !client.hasSignedInUser()) return@withContext false
        progressCallback(0f)
        val ready = client.healthCheck()
        progressCallback(if (ready) 1f else 0f)
        ready
    }

    override suspend fun generateEmbedding(text: String): FloatArray = withContext(Dispatchers.IO) {
        if (!isModelReady() || text.isBlank()) return@withContext FloatArray(0)
        val cleanText = text.take(CloudflareEmbeddingClient.MAX_TEXT_CHARS)
        val key = cacheKey(cleanText)
        cached(key)?.let { return@withContext it }
        try {
            client.embed(listOf(cleanText), "document").single().also { cache(key, it) }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            FloatArray(0)
        }
    }

    override suspend fun searchSimilar(
        query: String,
        candidates: List<SemanticCandidate>,
        options: SemanticSearchOptions
    ): List<SemanticSearchResult> = withContext(Dispatchers.IO) {
        if (!isModelReady() || query.isBlank() || candidates.isEmpty()) return@withContext emptyList()
        try {
            val queryVector = client.embed(listOf(query.take(500)), "query").single()
            val embedded = embedCandidates(candidates)
            embedded.mapNotNull { candidate ->
                val vector = candidate.embedding ?: return@mapNotNull null
                val score = computeCosineSimilarity(queryVector, vector)
                if (score < options.similarityThreshold.coerceIn(0f, 1f)) null
                else SemanticSearchResult(
                    fileItem = candidate.fileItem,
                    similarityScore = score,
                    matchedConcept = "Neural embedding match"
                )
            }.sortedByDescending { it.similarityScore }.take(options.maxResults.coerceIn(1, 200))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            // Hybrid use case retains local FTS/metadata results when remote inference fails.
            emptyList()
        }
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
        return (dotProduct / (kotlin.math.sqrt(normA) * kotlin.math.sqrt(normB)))
            .toFloat()
            .coerceIn(0f, 1f)
    }

    override suspend fun findNearDuplicates(
        candidates: List<SemanticCandidate>,
        similarityThreshold: Float
    ): List<NearDuplicateCluster> = withContext(Dispatchers.IO) {
        if (!isModelReady() || candidates.size < 2) return@withContext emptyList()
        try {
            val embedded = embedCandidates(candidates)
            val visited = mutableSetOf<String>()
            val clusters = mutableListOf<NearDuplicateCluster>()
            val threshold = similarityThreshold.coerceIn(0.70f, 0.95f)

            for (i in embedded.indices) {
                val base = embedded[i]
                if (base.embedding == null || base.fileItem.path in visited) continue
                val similar = mutableListOf<FileItem>()
                var sum = 0f
                for (j in i + 1 until embedded.size) {
                    val other = embedded[j]
                    val vector = other.embedding ?: continue
                    if (other.fileItem.path in visited) continue
                    val score = computeCosineSimilarity(base.embedding, vector)
                    if (score >= threshold) {
                        similar.add(other.fileItem)
                        sum += score
                        visited.add(other.fileItem.path)
                    }
                }
                if (similar.isNotEmpty()) {
                    visited.add(base.fileItem.path)
                    val all = (listOf(base.fileItem) + similar).sortedBy { it.lastModified }
                    clusters.add(
                        NearDuplicateCluster(
                            id = "cluster_${cacheKey(base.fileItem.path).take(16)}",
                            baseFile = all.first(),
                            similarFiles = all.drop(1),
                            averageSimilarity = sum / similar.size,
                            selectedPaths = emptySet()
                        )
                    )
                }
            }
            clusters.sortedByDescending { it.averageSimilarity }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }
    }

    override suspend fun suggestTags(candidate: SemanticCandidate): List<AiSuggestedTag> =
        withContext(Dispatchers.Default) {
            // Tag generation is intentionally unavailable until a separately evaluated model
            // contract is implemented; don't label heuristics as AI output.
            emptyList()
        }

    private suspend fun embedCandidates(candidates: List<SemanticCandidate>): List<SemanticCandidate> {
        val output = candidates.toMutableList()
        val pendingIndices = mutableListOf<Int>()
        val pendingTexts = mutableListOf<String>()
        val pendingKeys = mutableListOf<String>()

        candidates.forEachIndexed { index, candidate ->
            val supplied = candidate.embedding?.takeIf { it.size == CloudflareEmbeddingClient.EMBEDDING_DIMENSION }
            if (supplied != null) {
                output[index] = candidate.copy(embedding = supplied)
            } else {
                val text = candidate.textContent.take(CloudflareEmbeddingClient.MAX_TEXT_CHARS)
                val key = cacheKey(text)
                val existing = cached(key)
                if (existing != null) {
                    output[index] = candidate.copy(embedding = existing)
                } else if (text.isNotBlank()) {
                    pendingIndices.add(index)
                    pendingTexts.add(text)
                    pendingKeys.add(key)
                }
            }
        }

        pendingTexts.indices.chunked(CloudflareEmbeddingClient.MAX_TEXTS).forEach { chunk ->
            val vectors = client.embed(chunk.map { pendingTexts[it] }, "document")
            chunk.forEachIndexed { localIndex, sourceIndex ->
                val vector = vectors[localIndex]
                cache(pendingKeys[sourceIndex], vector)
                output[pendingIndices[sourceIndex]] = output[pendingIndices[sourceIndex]].copy(embedding = vector)
            }
        }
        return output
    }

    private fun cacheKey(text: String): String =
        MessageDigest.getInstance("SHA-256")
            .digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

    private fun cached(key: String): FloatArray? = synchronized(vectorCache) { vectorCache[key]?.copyOf() }

    private fun cache(key: String, vector: FloatArray) {
        synchronized(vectorCache) { vectorCache[key] = vector.copyOf() }
    }

    companion object {
        private const val CACHE_SIZE = 512
    }
}
