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
    private val client: EmbeddingClient = CloudflareEmbeddingClient(),
    private val vectorStore: EmbeddingVectorStore? = null,
    private val isEmbeddingConsentGranted: () -> Boolean = { false },
    private val isFullContentConsentGranted: () -> Boolean = { false }
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

    /** Abort active remote calls before clearing vectors when consent is revoked. */
    fun cancelPendingEmbeddingRequests() {
        client.cancelPendingRequests()
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
        ensureConsent()
        progressCallback(if (ready) 1f else 0f)
        ready
    }

    override suspend fun generateEmbedding(text: String): FloatArray = withContext(Dispatchers.IO) {
        if (!isModelReady() || text.isBlank()) return@withContext FloatArray(0)
        val cleanText = text.take(CloudflareEmbeddingClient.MAX_TEXT_CHARS)
        val key = cacheKey(cleanText)
        cached(key)?.let { return@withContext it }
        try {
            ensureConsent()
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
            ensureConsent()
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
            .coerceIn(-1f, 1f)
    }

    override suspend fun findNearDuplicates(
        candidates: List<SemanticCandidate>,
        similarityThreshold: Float
    ): List<NearDuplicateCluster> = withContext(Dispatchers.IO) {
        // Neural similarity is scoped to the consented Drive corpus; never upload local-device file text.
        val driveCandidates = candidates.filter { it.fileItem.path.startsWith("gdrive://") }
        if (!isModelReady() || driveCandidates.size < 2) return@withContext emptyList()
        try {
            val embedded = embedCandidates(
                driveCandidates,
                embedMissing = driveCandidates.size <= ON_DEMAND_CANDIDATE_LIMIT
            ).candidates
            ensureConsent()
            val visited = mutableSetOf<String>()
            val clusters = mutableListOf<NearDuplicateCluster>()
            val threshold = similarityThreshold.coerceIn(0.70f, 0.95f)

            for (i in embedded.indices) {
                val base = embedded[i]
                val baseVector = base.embedding ?: continue
                if (base.fileItem.path in visited) continue
                val similar = mutableListOf<FileItem>()
                var sum = 0f
                for (j in i + 1 until embedded.size) {
                    val other = embedded[j]
                    val vector = other.embedding ?: continue
                    if (other.fileItem.path in visited) continue
                    val score = computeCosineSimilarity(baseVector, vector)
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
        val fullContentConsentAtStart = isFullContentConsentGranted()
        // A search may have loaded rows just before full-content consent was revoked. Sanitize
        // those candidates here as well as in the background worker, immediately before embedding.
        val safeCandidates = candidates.map { candidate ->
            if (fullContentConsentAtStart || !isFullContentFile(candidate.fileItem)) {
                candidate
            } else {
                val safeText = buildString {
                    append(candidate.fileItem.name)
                    if (candidate.fileItem.tags.isNotEmpty()) {
                        append(' ').append(candidate.fileItem.tags.joinToString(" "))
                    }
                }.take(CloudflareEmbeddingClient.MAX_TEXT_CHARS)
                candidate.copy(
                    fileItem = candidate.fileItem.copy(indexedText = ""),
                    textContent = safeText,
                    embedding = null
                )
            }
        }
        val output = safeCandidates.toMutableList()
        val fullContentPaths = safeCandidates.asSequence()
            .filter { isFullContentFile(it.fileItem) }
            .map { it.fileItem.path }
            .toSet()
        val lookupRequests = safeCandidates.mapNotNull { candidate ->
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

        safeCandidates.forEachIndexed { index, candidate ->
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
            if (fullContentConsentAtStart && !isFullContentConsentGranted() &&
                chunk.any { sourceIndex -> isFullContentFile(safeCandidates[pendingIndices[sourceIndex]].fileItem) }
            ) throw CancellationException("Full-content consent revoked")
            val vectors = client.embed(chunk.map { pendingTexts[it] }, "document")
            ensureConsent()
            if (fullContentConsentAtStart && !isFullContentConsentGranted() &&
                chunk.any { sourceIndex -> isFullContentFile(safeCandidates[pendingIndices[sourceIndex]].fileItem) }
            ) throw CancellationException("Full-content consent revoked")
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
                ensureConsent()
                if (fullContentConsentAtStart && !isFullContentConsentGranted() &&
                    recordsToPersist.any { it.filePath in fullContentPaths }
                ) throw CancellationException("Full-content consent revoked")
                vectorStore?.putAll(recordsToPersist)
                recordsToPersist.clear()
            }
        }
        recordsToPersist.chunked(CloudflareEmbeddingClient.MAX_TEXTS).forEach { batch ->
            ensureConsent()
            if (fullContentConsentAtStart && !isFullContentConsentGranted() &&
                batch.any { it.filePath in fullContentPaths }
            ) throw CancellationException("Full-content consent revoked")
            vectorStore?.putAll(batch)
        }
        if (fullContentConsentAtStart && !isFullContentConsentGranted() &&
            safeCandidates.any { isFullContentFile(it.fileItem) }
        ) throw CancellationException("Full-content consent revoked")
        return EmbeddingBatchResult(output, pendingTexts.size)
    }

    private fun ensureConsent() {
        if (!isEmbeddingConsentGranted()) {
            throw CancellationException("Embedding consent revoked")
        }
    }

    private fun isFullContentFile(file: FileItem): Boolean {
        val mime = file.mimeType.orEmpty().lowercase()
        val extension = file.name.substringAfterLast('.', "").lowercase()
        return mime == "application/pdf" ||
            mime.startsWith("image/") ||
            mime in setOf("application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation") ||
            extension in setOf("pdf", "jpg", "jpeg", "png", "webp", "bmp", "heic", "docx", "xlsx", "pptx")
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
