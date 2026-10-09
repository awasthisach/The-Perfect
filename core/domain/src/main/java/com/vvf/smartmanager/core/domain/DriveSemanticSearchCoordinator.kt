package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.database.dao.DriveIndexDao
import com.vvf.smartmanager.core.database.dao.DriveVectorRecord

class DriveSemanticSearchCoordinator(
    private val driveIndexDao: DriveIndexDao,
    private val localSearchRepository: DriveSearchRepository,
    private val embeddingProvider: EmbeddingProvider,
    private val embeddingConsentGranted: () -> Boolean,
    private val embeddingBackendReady: () -> Boolean
) {
    suspend fun search(
        query: String,
        typeFilter: DriveSearchTypeFilter = DriveSearchTypeFilter.ALL,
        limit: Int = 200
    ): List<DriveRankedResult> {
        val enabled = query.isNotBlank() && embeddingConsentGranted() && embeddingBackendReady()
        val semanticScores = if (enabled) {
            val queryVector = embeddingProvider.embed(
                listOf(query.trim().take(MAX_QUERY_CHARS)),
                EmbeddingMode.QUERY
            ).getOrNull()?.vectors?.firstOrNull()
            if (queryVector == null || queryVector.size != EXPECTED_DIMENSION) {
                emptyMap()
            } else {
                rankAllVectors(queryVector)
            }
        } else emptyMap()

        // Local keyword/metadata search always remains available if neural requests fail.
        return localSearchRepository.search(
            query = query,
            typeFilter = typeFilter,
            semanticCosineScores = semanticScores,
            embeddingsEnabled = enabled && semanticScores.isNotEmpty(),
            limit = limit
        )
    }

    private suspend fun rankAllVectors(queryVector: FloatArray): Map<String, Float> {
        val scored = mutableListOf<Pair<String, Float>>()
        var offset = 0
        while (true) {
            val page: List<DriveVectorRecord> = driveIndexDao.getEmbeddingVectorsPage(VECTOR_PAGE_SIZE, offset)
            if (page.isEmpty()) break
            page.forEach { record ->
                val vector = EmbeddingVectorCodec.decode(record.embeddingVector, EXPECTED_DIMENSION) ?: return@forEach
                val score = EmbeddingVectorCodec.cosine(queryVector, vector)
                if (score.isFinite()) scored += record.driveFileId to score
            }
            offset += page.size
            if (page.size < VECTOR_PAGE_SIZE) break
        }
        return scored.sortedByDescending { it.second }.take(MAX_SEMANTIC_CANDIDATES).toMap()
    }

    companion object {
        private const val EXPECTED_DIMENSION = 768
        private const val MAX_QUERY_CHARS = 8_000
        private const val VECTOR_PAGE_SIZE = 200
        private const val MAX_SEMANTIC_CANDIDATES = 200
    }
}
