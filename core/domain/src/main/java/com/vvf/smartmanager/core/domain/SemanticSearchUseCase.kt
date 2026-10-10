package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.data.FileManagerRepository
import com.vvf.smartmanager.core.data.SearchRepository
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.SearchFilter
import com.vvf.smartmanager.core.model.SearchMatchType
import com.vvf.smartmanager.core.model.SemanticCandidate
import com.vvf.smartmanager.core.model.SemanticSearchOptions
import com.vvf.smartmanager.core.model.SemanticSearchResult
import com.vvf.smartmanager.core.plugin.spi.ISemanticSearchEngine
import kotlinx.coroutines.flow.first

/**
 * Hybrid search. Local FTS/metadata results are always retained; the optional semantic engine
 * only improves ranking when it is enabled and healthy. No cloud call is made from this use case.
 */
class SemanticSearchUseCase(
    private val semanticPlugin: ISemanticSearchEngine,
    private val searchRepository: SearchRepository,
    @Suppress("unused") private val fileManagerRepository: FileManagerRepository,
    private val isEmbeddingConsentGranted: () -> Boolean = { false }
) {
    fun isPluginReady(): Boolean = runCatching { semanticPlugin.isModelReady() }.getOrDefault(false)

    suspend fun downloadPluginModel(onProgress: (Float) -> Unit = {}): Boolean =
        semanticPlugin.downloadModel(onProgress)

    suspend fun searchSemantically(
        query: String,
        options: SemanticSearchOptions = SemanticSearchOptions()
    ): List<SemanticSearchResult> {
        val cleanQuery = query.trim().take(500)
        if (cleanQuery.isBlank()) return emptyList()

        // Local keyword/metadata results are the reliability baseline and remain available offline.
        val lexical = runCatching {
            searchRepository.searchFiles(cleanQuery, SearchFilter()).first()
        }.getOrDefault(emptyList())

        val lexicalScores = lexical.associate { result ->
            val score = when (result.matchType) {
                SearchMatchType.FILENAME -> 0.95f
                SearchMatchType.FTS -> 0.85f
                SearchMatchType.TAG -> 0.72f
                SearchMatchType.METADATA -> 0.55f
                SearchMatchType.SEMANTIC -> 0.50f
            }
            result.fileItem.path to (score to result)
        }

        val semanticScores = try {
            if (!isEmbeddingConsentGranted() || !semanticPlugin.isModelReady()) emptyList()
            else {
                val recent = searchRepository.getRecentIndexedFiles(CANDIDATE_LIMIT)
                if (recent.isEmpty()) emptyList()
                else {
                    val candidates = recent.map { file ->
                        SemanticCandidate(file, buildString {
                            append(file.name)
                            if (file.tags.isNotEmpty()) append(" ").append(file.tags.joinToString(" "))
                        })
                    }
                    semanticPlugin.searchSimilar(cleanQuery, candidates, options)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }

        val semanticByPath = semanticScores.associateBy { it.fileItem.path }
        val allFiles = LinkedHashMap<String, FileItem>()
        lexical.forEach { allFiles[it.fileItem.path] = it.fileItem }
        semanticScores.forEach { allFiles[it.fileItem.path] = it.fileItem }

        // Normalize to a common 0..1 scale. Keyword-only results still rank when neural search fails.
        return allFiles.values.map { file ->
            val lexicalScore = lexicalScores[file.path]?.first ?: 0f
            val semanticScore = (semanticByPath[file.path]?.similarityScore ?: 0f).coerceIn(0f, 1f)
            val combined = if (semanticByPath.containsKey(file.path)) {
                0.55f * semanticScore + 0.30f * lexicalScore + 0.15f * metadataScore(file)
            } else {
                // If no vector exists, do not penalize the lexical score by pretending a vector is zero.
                0.80f * lexicalScore + 0.20f * metadataScore(file)
            }
            SemanticSearchResult(
                fileItem = file,
                similarityScore = combined.coerceIn(0f, 1f),
                matchedConcept = when {
                    semanticByPath.containsKey(file.path) -> semanticByPath[file.path]?.matchedConcept ?: "Hybrid semantic match"
                    lexicalScores[file.path]?.second?.matchedSnippet != null -> lexicalScores[file.path]?.second?.matchedSnippet
                    else -> "Keyword or metadata match"
                }
            )
        }.sortedWith(compareByDescending<SemanticSearchResult> { it.similarityScore }.thenBy { it.fileItem.name.lowercase() })
            .take(options.maxResults.coerceIn(1, 200))
    }

    private fun metadataScore(file: FileItem): Float =
        if (file.tags.isNotEmpty() || file.mimeType != null) 0.5f else 0.25f

    companion object {
        private const val CANDIDATE_LIMIT = 400
    }
}
