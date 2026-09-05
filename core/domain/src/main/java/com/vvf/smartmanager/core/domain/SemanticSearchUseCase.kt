package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.data.FileManagerRepository
import com.vvf.smartmanager.core.data.SearchRepository
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.SemanticCandidate
import com.vvf.smartmanager.core.model.SemanticSearchOptions
import com.vvf.smartmanager.core.model.SemanticSearchResult
import com.vvf.smartmanager.core.plugin.spi.ISemanticSearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * On-device semantic search over a **bounded** candidate set from the Room index.
 * Never walks the entire storage tree per keystroke (primary lag/ANR cause).
 */
class SemanticSearchUseCase(
    private val semanticPlugin: ISemanticSearchEngine,
    private val searchRepository: SearchRepository,
    @Suppress("unused") private val fileManagerRepository: FileManagerRepository
) {
    fun isPluginReady(): Boolean = semanticPlugin.isModelReady()

    suspend fun downloadPluginModel(onProgress: (Float) -> Unit = {}): Boolean {
        return semanticPlugin.downloadModel(onProgress)
    }

    suspend fun searchSemantically(
        query: String,
        options: SemanticSearchOptions = SemanticSearchOptions()
    ): List<SemanticSearchResult> = withContext(Dispatchers.Default) {
        val trimmed = query.trim().take(MAX_QUERY_CHARS)
        if (trimmed.length < MIN_QUERY_CHARS || !semanticPlugin.isModelReady()) {
            return@withContext emptyList()
        }

        val candidates = withTimeoutOrNull(CANDIDATE_TIMEOUT_MS) {
            loadBoundedCandidates()
        } ?: emptyList()

        if (candidates.isEmpty()) return@withContext emptyList()

        withTimeoutOrNull(SEARCH_TIMEOUT_MS) {
            semanticPlugin.searchSimilar(
                query = trimmed,
                candidates = candidates,
                options = options.copy(maxResults = options.maxResults.coerceAtMost(20))
            )
        } ?: emptyList()
    }

    private suspend fun loadBoundedCandidates(): List<SemanticCandidate> {
        val files: List<FileItem> = try {
            searchRepository.getRecentIndexedFiles(CANDIDATE_LIMIT)
        } catch (_: Throwable) {
            emptyList()
        }
        return files.map { file ->
            SemanticCandidate(
                fileItem = file,
                textContent = buildString {
                    append(file.name)
                    if (file.tags.isNotEmpty()) {
                        append(' ')
                        append(file.tags.joinToString(" "))
                    }
                }
            )
        }
    }

    companion object {
        private const val MAX_QUERY_CHARS = 500
        private const val MIN_QUERY_CHARS = 2
        private const val CANDIDATE_LIMIT = 400
        private const val CANDIDATE_TIMEOUT_MS = 2_000L
        private const val SEARCH_TIMEOUT_MS = 4_000L
    }
}
