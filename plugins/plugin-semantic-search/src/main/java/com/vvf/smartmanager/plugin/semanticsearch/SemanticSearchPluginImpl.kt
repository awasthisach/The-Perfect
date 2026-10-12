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
 * Temporary deterministic hash-vector implementation retained for compatibility only.
 * It is not a neural embedding model. Semantic ranking and similarity-based duplicate grouping
 * fail closed until a real, authenticated embedding provider is integrated.
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
                        matchedConcept = extractMatchedConcept(query, candidate.textContent)
                    )
                )
            }
        }

        results.sortedByDescending { it.similarityScore }.take(options.maxResults)
    }

    override fun computeCosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float {
        if (vectorA.size != vectorB.size || vectorA.isEmpty()) return 0f
        var dotProduct = 0f
        for (i in vectorA.indices) {
            dotProduct += vectorA[i] * vectorB[i]
        }
        return dotProduct.coerceIn(0f, 1f)
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

    override suspend fun suggestTags(candidate: SemanticCandidate): List<AiSuggestedTag> = withContext(Dispatchers.Default) {
        val cleanText = candidate.textContent.lowercase()
        val tags = mutableListOf<AiSuggestedTag>()

        val conceptDefinitions = mapOf(
            "invoice" to Pair("Finance", 0.95f),
            "bill" to Pair("Finance", 0.92f),
            "receipt" to Pair("Finance", 0.94f),
            "tax" to Pair("Finance", 0.90f),
            "gst" to Pair("Finance", 0.91f),
            "medical" to Pair("Health", 0.96f),
            "prescription" to Pair("Health", 0.95f),
            "hospital" to Pair("Health", 0.93f),
            "report" to Pair("Documents", 0.82f),
            "contract" to Pair("Legal", 0.96f),
            "agreement" to Pair("Legal", 0.94f),
            "nda" to Pair("Legal", 0.95f),
            "affidavit" to Pair("Legal", 0.93f),
            "resume" to Pair("Career", 0.95f),
            "cv" to Pair("Career", 0.94f),
            "certificate" to Pair("Education", 0.92f),
            "degree" to Pair("Education", 0.90f),
            "ticket" to Pair("Travel", 0.94f),
            "flight" to Pair("Travel", 0.93f),
            "train" to Pair("Travel", 0.92f),
            "hotel" to Pair("Travel", 0.90f),
            "photo" to Pair("Media", 0.85f),
            "screenshot" to Pair("Media", 0.88f),
            "id" to Pair("Identity", 0.89f),
            "aadhaar" to Pair("Identity", 0.97f),
            "pan" to Pair("Identity", 0.97f),
            "passport" to Pair("Identity", 0.98f),
            "urgent" to Pair("Priority", 0.85f),
            "official" to Pair("Work", 0.82f)
        )

        for ((keyword, categoryConfidence) in conceptDefinitions) {
            if (cleanText.contains(keyword)) {
                tags.add(
                    AiSuggestedTag(
                        tagName = keyword,
                        confidenceScore = categoryConfidence.second,
                        category = categoryConfidence.first
                    )
                )
            }
        }

        if (tags.isEmpty()) {
            val ext = candidate.fileItem.extension
            when (ext) {
                "pdf", "doc", "docx" -> tags.add(AiSuggestedTag("document", 0.80f, "General"))
                "jpg", "png", "jpeg", "webp" -> tags.add(AiSuggestedTag("photo", 0.80f, "Media"))
                "mp4", "mkv" -> tags.add(AiSuggestedTag("video", 0.80f, "Media"))
                "mp3", "wav" -> tags.add(AiSuggestedTag("audio", 0.80f, "Media"))
                "apk" -> tags.add(AiSuggestedTag("installer", 0.85f, "System"))
                "zip", "rar" -> tags.add(AiSuggestedTag("archive", 0.80f, "System"))
            }
        }

        tags.sortedByDescending { it.confidenceScore }.distinctBy { it.tagName }.take(6)
    }

    private fun extractMatchedConcept(query: String, text: String): String {
        return "Semantic Match (${query.take(20)})"
    }
}
