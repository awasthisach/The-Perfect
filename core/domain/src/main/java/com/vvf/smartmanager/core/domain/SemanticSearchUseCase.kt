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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Scores embedding-backed matches using local lexical/metadata signals.
 * The search UI obtains local FTS results independently, so they remain visible when neural
 * ranking is disabled or the remote provider fails. No cloud call is made outside the opted-in engine.
 */
class SemanticSearchUseCase(
    private val semanticPlugin: ISemanticSearchEngine,
    private val searchRepository: SearchRepository,
    @Suppress("unused") private val fileManagerRepository: FileManagerRepository,
    private val isEmbeddingConsentGranted: () -> Boolean = { false }
) {
    @Volatile private var lastSemanticSearchUnavailable = false

    /** True when the latest opted-in neural attempt failed at the provider/transport layer. */
    fun wasLastSemanticSearchUnavailable(): Boolean = lastSemanticSearchUnavailable

    fun isPluginReady(): Boolean = runCatching { semanticPlugin.isModelReady() }.getOrDefault(false)

    suspend fun downloadPluginModel(onProgress: (Float) -> Unit = {}): Boolean =
        semanticPlugin.downloadModel(onProgress)

    suspend fun searchSemantically(
        query: String,
        options: SemanticSearchOptions = SemanticSearchOptions()
    ): List<SemanticSearchResult> {
        lastSemanticSearchUnavailable = false
        val cleanQuery = query.trim().take(500)
        if (cleanQuery.isBlank()) return emptyList()

        // Local keyword/metadata results are the reliability baseline and remain available offline.
        val lexical = try {
            searchRepository.searchFiles(cleanQuery, SearchFilter()).first()
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            emptyList()
        }

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
            if (!isEmbeddingConsentGranted()) emptyList()
            else if (!semanticPlugin.isModelReady()) {
                lastSemanticSearchUnavailable = true
                emptyList()
            } else {
                val recent = searchRepository.getRecentDriveIndexedFiles(CANDIDATE_LIMIT)
                if (recent.isEmpty()) emptyList()
                else {
                    val candidates = recent.map { file ->
                        SemanticCandidate(file, buildString {
                            append(file.name)
                            if (file.tags.isNotEmpty()) append(" ").append(file.tags.joinToString(" "))
                            // Include extracted document text; the consent gate above ensures this
                            // is only handed to a semantic provider after the user opts in.
                            if (file.indexedText.isNotBlank()) append(" ").append(file.indexedText.take(MAX_CANDIDATE_TEXT_CHARS))
                        })
                    }
                    semanticPlugin.searchSimilar(cleanQuery, candidates, options)
                }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            lastSemanticSearchUnavailable = true
            emptyList()
        }

        // Return only genuine embedding-backed hits. The ViewModel merges these with the
        // independent local FTS result stream, avoiding false "semantic" labels on keyword-only rows.
        return semanticScores.map { hit ->
            val file = hit.fileItem
            val lexicalScore = lexicalScores[file.path]?.first ?: 0f
            val semanticScore = hit.similarityScore.coerceIn(0f, 1f)
            val combined = 0.55f * semanticScore + 0.30f * lexicalScore + 0.15f * metadataScore(file)
            hit.copy(
                similarityScore = combined.coerceIn(0f, 1f),
                matchedConcept = hit.matchedConcept ?: "Neural embedding match"
            )
        }.sortedWith(
            compareByDescending<SemanticSearchResult> { it.similarityScore }
                .thenBy { it.fileItem.name.lowercase() }
        ).take(options.maxResults.coerceIn(1, 200))
    }

    private fun metadataScore(file: FileItem): Float =
        if (file.tags.isNotEmpty() || file.mimeType != null) 0.5f else 0.25f

    companion object {
        private const val CANDIDATE_LIMIT = 2_000
        private const val MAX_CANDIDATE_TEXT_CHARS = 4_000
    }
}
