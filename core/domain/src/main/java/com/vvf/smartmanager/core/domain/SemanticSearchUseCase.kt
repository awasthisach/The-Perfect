package com.vvf.smartmanager.core.domain

import kotlinx.coroutines.CancellationException

import com.vvf.smartmanager.core.data.FileManagerRepository
import com.vvf.smartmanager.core.data.SearchRepository
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.SemanticCandidate
import com.vvf.smartmanager.core.model.SemanticSearchOptions
import com.vvf.smartmanager.core.model.SemanticSearchResult
import com.vvf.smartmanager.core.plugin.spi.ISemanticSearchEngine

/**
 * Runs deterministic on-device hashed text-similarity ranking over indexed file names and tags.
 * This is a heuristic projection, not a learned embedding model. Bounded candidates prevent lag/OOM.
 */
class SemanticSearchUseCase(
    private val semanticPlugin: ISemanticSearchEngine,
    private val searchRepository: SearchRepository,
    private val fileManagerRepository: FileManagerRepository
) {
    fun isPluginReady(): Boolean = semanticPlugin.isModelReady()

    /** Legacy compatibility hook; initializes the built-in engine and does not download a model. */
    suspend fun downloadPluginModel(onProgress: (Float) -> Unit = {}): Boolean {
        return semanticPlugin.downloadModel(onProgress)
    }

    suspend fun searchSemantically(
        query: String,
        options: SemanticSearchOptions = SemanticSearchOptions()
    ): List<SemanticSearchResult> {
        if (query.isBlank() || !semanticPlugin.isModelReady()) {
            return emptyList()
        }

        // Bounded candidates: loading ALL files every keystroke caused lag/OOM.
        val recentFiles: List<FileItem> = try {
            searchRepository.getRecentIndexedFiles(CANDIDATE_LIMIT)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (_: Exception) {
            emptyList()
        }

        if (recentFiles.isEmpty()) return emptyList()

        val candidates = recentFiles.map { file ->
            val textContent = buildString {
                append(file.name)
                if (file.tags.isNotEmpty()) {
                    append(" ")
                    append(file.tags.joinToString(" "))
                }
            }
            SemanticCandidate(
                fileItem = file,
                textContent = textContent
            )
        }

        return semanticPlugin.searchSimilar(
            query = query.take(500),
            candidates = candidates,
            options = options
        )
    }

    companion object {
        private const val CANDIDATE_LIMIT = 400
    }
}
