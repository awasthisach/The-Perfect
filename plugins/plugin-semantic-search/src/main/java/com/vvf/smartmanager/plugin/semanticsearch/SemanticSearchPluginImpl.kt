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
private data class EmbeddingBatchResult(val candidates: List<SemanticCandidate>, val generatedCount: Int)

class SemanticSearchPluginImpl(
    private val client: CloudflareEmbeddingClient = CloudflareEmbeddingClient(),
    private val vectorStore: EmbeddingVectorStore? = null,
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

    /** Remove persistent vectors when consent is revoked or the linked account is cleared. */
    suspend fun clearPersistedEmbeddings() {
        vectorStore?.clear()
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
            val vector = client.embed(listOf(cleanText), "document").single()
            ensureConsent()
            cache(key, vector)
            vector
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
            val embedded = embedCandidates(candidates, embedMissing = candidates.size <= ON_DEMAND_CANDIDATE_LIMIT).candidates
            ensureConsent()
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
            val embedded = embedCandidates(candidates, embedMissing = candidates.size <= ON_DEMAND_CANDIDATE_LIMIT).candidates
            ensureConsent()
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

    /**
     * Background indexing entry point. Call only from a consent-gated WorkManager job.
     * It persists each completed batch in the encrypted Room-backed vector store.
     */
    suspend fun indexCandidates(candidates: List<SemanticCandidate>): Int {
        if (!isModelReady() || candidates.isEmpty()) return 0
        return embedCandidates(candidates, embedMissing = true).generatedCount
    }

    private suspend fun embedCandidates(
        candidates: List<SemanticCandidate>,
        embedMissing: Boolean = true
    ): EmbeddingBatchResult {
        ensureConsent()
        val output = candidates.toMutableList()
        val lookupRequests = candidates.mapNotNull { candidate ->
            if (candidate.embedding?.size == CloudflareEmbeddingClient.EMBEDDING_DIMENSION) return@mapNotNull null
            val text = candidate.textContent.take(CloudflareEmbeddingClient.MAX_TEXT_CHARS)
            if (text.isBlank()) return@mapNotNull null
            val key = cacheKey(text)
            if (cached(key) != null) null else EmbeddingVectorLookup(candidate.fileItem.path, key, MODEL_VERSION)
        }
        val persistedByPath = vectorStore?.getMany(lookupRequests).orEmpty()
        val pendingIndices = mutableListOf<Int>()
        val pendingTexts = mutableListOf<String>()
        val pendingKeys = mutableListOf<String>()
        val pendingPaths = mutableListOf<String>()
        val recordsToPersist = mutableListOf<EmbeddingVectorRecord>()

        candidates.forEachIndexed { index, candidate ->
            val supplied = candidate.embedding?.takeIf { it.size == CloudflareEmbeddingClient.EMBEDDING_DIMENSION }
            if (supplied != null) {
                output[index] = candidate.copy(embedding = supplied)
            } else {
                val text = candidate.textContent.take(CloudflareEmbeddingClient.MAX_TEXT_CHARS)
                if (text.isBlank()) return@forEachIndexed
                val key = cacheKey(text)
                val inMemory = cached(key)
                if (inMemory != null) {
                    output[index] = candidate.copy(embedding = inMemory)
                    if (vectorStore != null) {
                        recordsToPersist.add(
                            EmbeddingVectorRecord(candidate.fileItem.path, key, MODEL_VERSION, inMemory)
                        )
                    }
                } else {
                    val persisted = persistedByPath[candidate.fileItem.path]
                    if (persisted != null && persisted.size == CloudflareEmbeddingClient.EMBEDDING_DIMENSION) {
                        cache(key, persisted)
                        output[index] = candidate.copy(embedding = persisted)
                    } else if (embedMissing) {
                        pendingIndices.add(index)
                        pendingTexts.add(text)
                        pendingKeys.add(key)
                        pendingPaths.add(candidate.fileItem.path)
                    }
                }
            }
        }

        batchIndicesByPayload(pendingTexts).forEach { chunk ->
            ensureConsent()
            val vectors = client.embed(chunk.map { pendingTexts[it] }, "document")
            ensureConsent()
            chunk.forEachIndexed { localIndex, sourceIndex ->
                val vector = vectors[localIndex]
                val key = pendingKeys[sourceIndex]
                cache(key, vector)
                output[pendingIndices[sourceIndex]] = output[pendingIndices[sourceIndex]].copy(embedding = vector)
                recordsToPersist.add(
                    EmbeddingVectorRecord(
                        filePath = pendingPaths[sourceIndex],
                        contentHash = key,
                        modelVersion = MODEL_VERSION,
                        vector = vector
                    )
                )
            }
            // Commit each completed remote batch so a later retry resumes from persisted vectors.
            if (recordsToPersist.isNotEmpty()) {
                vectorStore?.putAll(recordsToPersist)
                recordsToPersist.clear()
            }
        }
        recordsToPersist.chunked(CloudflareEmbeddingClient.MAX_TEXTS).forEach { batch ->
            ensureConsent()
            vectorStore?.putAll(batch)
        }
        return EmbeddingBatchResult(output, pendingTexts.size)
    }

    private fun ensureConsent() {
        if (!isEmbeddingConsentGranted()) {
            throw CancellationException("Embedding consent revoked")
        }
    }

    private fun batchIndicesByPayload(texts: List<String>): List<List<Int>> {
        val batches = mutableListOf<List<Int>>()
        var current = mutableListOf<Int>()
        var currentBytes = 128
        texts.forEachIndexed { index, text ->
            val itemBytes = text.toByteArray(Charsets.UTF_8).size + 64
            if (current.isNotEmpty() &&
                (current.size >= CloudflareEmbeddingClient.MAX_TEXTS ||
                    currentBytes + itemBytes > CloudflareEmbeddingClient.MAX_REQUEST_BYTES)
            ) {
                batches.add(current)
                current = mutableListOf()
                currentBytes = 128
            }
            current.add(index)
            currentBytes += itemBytes
        }
        if (current.isNotEmpty()) batches.add(current)
        return batches
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
        private const val MODEL_VERSION = "gemini-embedding-2:3:768"
        private const val ON_DEMAND_CANDIDATE_LIMIT = 32
    }
}
