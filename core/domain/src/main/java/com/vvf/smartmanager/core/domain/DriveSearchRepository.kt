package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.database.dao.DriveIndexDao
import com.vvf.smartmanager.core.database.model.DriveIndexFileEntity

enum class DriveSearchTypeFilter {
    ALL, DOCUMENTS, SPREADSHEETS, PRESENTATIONS, IMAGES
}

/**
 * Local-first Drive search. Candidate retrieval and ranking never require network access; a caller
 * may provide verified cosine scores only when consent and the authenticated embedding backend are on.
 */
class DriveSearchRepository(
    private val driveIndexDao: DriveIndexDao,
    private val embeddingConsentGranted: () -> Boolean = { false },
    private val embeddingBackendReady: () -> Boolean = { false }
) {
    suspend fun search(
        query: String,
        typeFilter: DriveSearchTypeFilter = DriveSearchTypeFilter.ALL,
        semanticCosineScores: Map<String, Float> = emptyMap(),
        embeddingsEnabled: Boolean = false,
        limit: Int = 200
    ): List<DriveRankedResult> {
        val terms = query.lowercase()
            .split(Regex("[^\\p{L}\\p{Nd}]+"))
            .filter { it.length >= 2 }
            .distinct()
            .take(MAX_QUERY_TERMS)
        val hybridEnabled = embeddingsEnabled && embeddingConsentGranted() && embeddingBackendReady() &&
            semanticCosineScores.isNotEmpty()
        if (terms.isEmpty() && !hybridEnabled) return emptyList()

        val candidates = linkedMapOf<String, DriveIndexFileEntity>()
        for (term in terms) {
            driveIndexDao.searchLocalText(term, CANDIDATES_PER_TERM).forEach { file ->
                if (matchesType(file, typeFilter)) candidates.putIfAbsent(file.driveFileId, file)
            }
        }
        if (hybridEnabled) {
            driveIndexDao.getFilesByDriveIds(semanticCosineScores.keys.take(MAX_RESULTS)).forEach { file ->
                if (matchesType(file, typeFilter)) candidates.putIfAbsent(file.driveFileId, file)
            }
        }
        return DriveSearchRanker.rank(
            query = query,
            files = candidates.values.toList(),
            semanticCosineScores = semanticCosineScores,
            embeddingsEnabled = embeddingsEnabled && embeddingConsentGranted() && embeddingBackendReady(),
            limit = limit.coerceIn(1, MAX_RESULTS)
        )
    }

    private fun matchesType(file: DriveIndexFileEntity, filter: DriveSearchTypeFilter): Boolean {
        val mime = file.mimeType.lowercase()
        return when (filter) {
            DriveSearchTypeFilter.ALL -> true
            DriveSearchTypeFilter.DOCUMENTS -> mime.startsWith("text/") ||
                mime.contains("pdf") || mime.contains("document") || mime.contains("wordprocessingml") ||
                mime.contains("msword")
            DriveSearchTypeFilter.SPREADSHEETS -> mime.contains("spreadsheet") ||
                mime.contains("excel") || mime.contains("csv")
            DriveSearchTypeFilter.PRESENTATIONS -> mime.contains("presentation") ||
                mime.contains("powerpoint")
            DriveSearchTypeFilter.IMAGES -> mime.startsWith("image/")
        }
    }

    companion object {
        private const val MAX_QUERY_TERMS = 8
        private const val CANDIDATES_PER_TERM = 200
        private const val MAX_RESULTS = 200
    }
}
