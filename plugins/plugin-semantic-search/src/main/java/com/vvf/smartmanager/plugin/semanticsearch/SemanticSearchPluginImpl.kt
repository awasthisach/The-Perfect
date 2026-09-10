package com.vvf.smartmanager.plugin.semanticsearch

import com.vvf.smartmanager.core.model.AiSuggestedTag
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.NearDuplicateCluster
import com.vvf.smartmanager.core.model.SemanticCandidate
import com.vvf.smartmanager.core.model.SemanticSearchOptions
import com.vvf.smartmanager.core.model.SemanticSearchResult
import com.vvf.smartmanager.core.plugin.spi.SemanticSearchSPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.sqrt

/**
 * On-device semantic-style ranking using deterministic 128-d hashed embeddings
 * (not a TFLite neural model). Suitable for lightweight offline relevance ranking.
 */
class SemanticSearchPluginImpl : SemanticSearchSPI {

    @Volatile
    private var isModelDownloaded: Boolean = true

    private val embeddingDimension: Int = 128
    private val embeddingCache = ConcurrentHashMap<String, FloatArray>()

    override fun isModelReady(): Boolean = isModelDownloaded

    override suspend fun downloadModel(progressCallback: (Float) -> Unit): Boolean = withContext(Dispatchers.IO) {
        for (i in 1..10) {
            delay(40)
            progressCallback(i / 10f)
        }
        isModelDownloaded = true
        true
    }

    override suspend fun generateEmbedding(text: String): FloatArray = withContext(Dispatchers.Default) {
        val cleanText = text.lowercase().trim()
        if (cleanText.isEmpty()) {
            return@withContext FloatArray(embeddingDimension) { 0f }
        }

        embeddingCache[cleanText]?.let { return@withContext it }

        val vector = FloatArray(embeddingDimension) { 0f }
        val tokens = cleanText.split(Regex("[^\\p{L}\\p{Nd}]+")).filter { it.length >= 2 }

        if (tokens.isEmpty()) {
            return@withContext vector
        }

        for (token in tokens) {
            val tokenHash = token.hashCode()
            val primaryBucket = safeBucket(tokenHash)
            val secondaryBucket = safeBucket(tokenHash * 31)
            vector[primaryBucket] += 1.0f
            vector[secondaryBucket] += 0.5f

            when {
                token in setOf("bill", "invoice", "receipt", "payment", "tax") -> {
                    vector[10 % embeddingDimension] += 2.0f
                    vector[11 % embeddingDimension] += 1.5f
                }
                token in setOf("photo", "image", "picture", "camera") -> {
                    vector[20 % embeddingDimension] += 2.0f
                    vector[21 % embeddingDimension] += 1.5f
                }
                token in setOf("video", "movie", "clip") -> {
                    vector[30 % embeddingDimension] += 2.0f
                    vector[31 % embeddingDimension] += 1.5f
                }
                token in setOf("doc", "document", "pdf", "file") -> {
                    vector[40 % embeddingDimension] += 2.0f
                    vector[41 % embeddingDimension] += 1.5f
                }
                token in setOf("music", "audio", "song") -> {
                    vector[50 % embeddingDimension] += 2.0f
                    vector[51 % embeddingDimension] += 1.5f
                }
                token in setOf("vault", "secure", "lock", "private") -> {
                    vector[60 % embeddingDimension] += 2.0f
                    vector[61 % embeddingDimension] += 1.5f
                }
            }
        }

        var sumSquares = 0.0
        for (v in vector) sumSquares += (v * v).toDouble()
        val norm = sqrt(sumSquares).toFloat()
        if (norm > 0f) {
            for (i in vector.indices) {
                vector[i] = vector[i] / norm
            }
        }

        embeddingCache[cleanText] = vector
        vector
    }

    /** Fold sign bit so Int.MIN_VALUE and negative remainders never yield illegal indices. */
    private fun safeBucket(hash: Int): Int {
        val normalized = hash and Int.MAX_VALUE
        return normalized % embeddingDimension
    }

    override suspend fun search(
        query: String,
        candidates: List<SemanticCandidate>,
        options: SemanticSearchOptions
    ): List<SemanticSearchResult> = withContext(Dispatchers.Default) {
        if (query.isBlank() || candidates.isEmpty()) return@withContext emptyList()
        val queryVector = generateEmbedding(query)
        candidates.mapNotNull { candidate ->
            val text = candidate.indexedText.ifBlank { candidate.fileItem.name }
            val docVector = generateEmbedding(text)
            val score = computeCosineSimilarity(queryVector, docVector)
            if (score >= options.minScore) {
                SemanticSearchResult(
                    fileItem = candidate.fileItem,
                    score = score,
                    matchType = "semantic"
                )
            } else null
        }.sortedByDescending { it.score }
            .take(options.maxResults.coerceAtLeast(1))
    }

    /**
     * Dot product of L2-normalized vectors equals cosine similarity in [0, 1] for non-negative weights.
     */
    private fun computeCosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
        if (vectorA.size != vectorB.size) return 0f
        var dotProduct = 0f
        for (i in vectorA.indices) {
            dotProduct += vectorA[i] * vectorB[i]
        }
        return dotProduct.coerceIn(0f, 1f)
    }

    override suspend fun findNearDuplicates(
        candidates: List<SemanticCandidate>,
        threshold: Float
    ): List<NearDuplicateCluster> = withContext(Dispatchers.Default) {
        if (candidates.size < 2) return@withContext emptyList()
        val vectors = candidates.map { c ->
            val text = c.indexedText.ifBlank { c.fileItem.name }
            c to generateEmbedding(text)
        }
        val used = BooleanArray(vectors.size)
        val clusters = mutableListOf<NearDuplicateCluster>()
        for (i in vectors.indices) {
            if (used[i]) continue
            val (base, baseVec) = vectors[i]
            val members = mutableListOf(base.fileItem)
            used[i] = true
            for (j in i + 1 until vectors.size) {
                if (used[j]) continue
                val score = computeCosineSimilarity(baseVec, vectors[j].second)
                if (score >= threshold) {
                    members.add(vectors[j].first.fileItem)
                    used[j] = true
                }
            }
            if (members.size > 1) {
                clusters.add(
                    NearDuplicateCluster(
                        id = "cluster_${base.fileItem.path.hashCode()}",
                        items = members,
                        averageSimilarity = threshold
                    )
                )
            }
        }
        clusters
    }

    override suspend fun suggestTags(
        fileItem: FileItem,
        indexedText: String
    ): List<AiSuggestedTag> = withContext(Dispatchers.Default) {
        val text = (indexedText.ifBlank { fileItem.name }).lowercase()
        val suggestions = mutableListOf<AiSuggestedTag>()
        fun add(tag: String, conf: Float) {
            if (suggestions.none { it.tag.equals(tag, true) }) {
                suggestions.add(AiSuggestedTag(tag = tag, confidence = conf))
            }
        }
        when {
            listOf("invoice", "bill", "receipt", "tax").any { it in text } -> add("finance", 0.85f)
            listOf("photo", "img", "camera").any { it in text } -> add("photos", 0.8f)
            listOf("pdf", "document", "doc").any { it in text } -> add("documents", 0.8f)
            listOf("mp4", "video", "movie").any { it in text } -> add("videos", 0.8f)
            listOf("mp3", "audio", "song").any { it in text } -> add("audio", 0.75f)
        }
        suggestions
    }
}
