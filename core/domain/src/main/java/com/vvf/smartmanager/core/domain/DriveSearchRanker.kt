package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.database.model.DriveIndexFileEntity
import kotlin.math.ln

data class DriveRankedResult(
    val file: DriveIndexFileEntity,
    val score: Double,
    val semanticScore: Double,
    val bm25Score: Double,
    val metadataScore: Double
)

/**
 * Deterministic local ranking. If verified neural cosine scores are provided and explicitly enabled,
 * use the requested 0.55/0.25/0.20 hybrid. Otherwise only local text BM25 and metadata contribute.
 */
object DriveSearchRanker {
    private const val K1 = 1.2
    private const val B = 0.75

    fun rank(
        query: String,
        files: List<DriveIndexFileEntity>,
        semanticCosineScores: Map<String, Float> = emptyMap(),
        embeddingsEnabled: Boolean = false,
        limit: Int = 200
    ): List<DriveRankedResult> {
        val terms = tokenize(query).distinct()
        if (terms.isEmpty() || files.isEmpty() || limit <= 0) return emptyList()

        val candidates = files.filter { it.indexStatus != "REMOTE_REMOVED" }
        if (candidates.isEmpty()) return emptyList()

        val tokenized = candidates.associate { it.driveFileId to tokenize(it.name + " " + it.extractedText) }
        val lengths = tokenized.values.map { it.size }
        val averageLength = lengths.average().takeIf { it > 0.0 } ?: 1.0
        val documentFrequency = terms.associateWith { term ->
            tokenized.values.count { document -> document.any { it == term } }
        }
        val rawBm25 = candidates.associate { file ->
            val tokens = tokenized[file.driveFileId].orEmpty()
            val termFrequency = tokens.groupingBy { it }.eachCount()
            val score = terms.sumOf { term ->
                val tf = termFrequency[term] ?: 0
                if (tf == 0) 0.0 else {
                    val df = documentFrequency[term] ?: 0
                    val idf = ln(1.0 + (candidates.size - df + 0.5) / (df + 0.5))
                    val denominator = tf + K1 * (1.0 - B + B * tokens.size / averageLength)
                    idf * (tf * (K1 + 1.0)) / denominator
                }
            }
            file.driveFileId to score
        }
        val maxBm25 = rawBm25.values.maxOrNull()?.takeIf { it > 0.0 } ?: 1.0
        val useHybrid = embeddingsEnabled && semanticCosineScores.isNotEmpty()

        return candidates.map { file ->
            val bm25 = ((rawBm25[file.driveFileId] ?: 0.0) / maxBm25).coerceIn(0.0, 1.0)
            val metadata = metadataScore(query, terms, file)
            val cosine = semanticCosineScores[file.driveFileId]?.toDouble()
                ?.let { ((it + 1.0) / 2.0).coerceIn(0.0, 1.0) } ?: 0.0
            val score = if (useHybrid) {
                0.55 * cosine + 0.25 * bm25 + 0.20 * metadata
            } else {
                0.80 * bm25 + 0.20 * metadata
            }
            DriveRankedResult(file, score, cosine, bm25, metadata)
        }
            .filter { it.score > 0.0 }
            .sortedWith(
                compareByDescending<DriveRankedResult> { it.score }
                    .thenByDescending { it.file.modifiedTimeMs }
                    .thenBy { it.file.name.lowercase() }
                    .thenBy { it.file.driveFileId }
            )
            .take(limit)
    }

    private fun metadataScore(query: String, terms: List<String>, file: DriveIndexFileEntity): Double {
        val name = file.name.lowercase()
        val phrase = query.trim().lowercase()
        val coverage = if (terms.isEmpty()) 0.0 else terms.count { name.contains(it) }.toDouble() / terms.size
        val base = when {
            name == phrase -> 1.0
            phrase.isNotBlank() && name.contains(phrase) -> 0.85
            coverage > 0.0 -> 0.25 + 0.65 * coverage
            else -> 0.0
        }
        return (base + if (file.starred) 0.02 else 0.0).coerceIn(0.0, 1.0)
    }

    private fun tokenize(value: String): List<String> =
        value.lowercase()
            .split(Regex("[^\\p{L}\\p{Nd}]+"))
            .filter { it.length >= 2 }
}
