package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.data.FileManagerRepository
import com.vvf.smartmanager.core.data.SearchRepository
import com.vvf.smartmanager.core.model.FileCategory
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.SemanticCandidate
import com.vvf.smartmanager.core.model.SemanticSearchOptions
import com.vvf.smartmanager.core.model.SemanticSearchResult
import com.vvf.smartmanager.core.plugin.spi.ISemanticSearchEngine
import kotlinx.coroutines.flow.first

/**
 * UseCase to execute On-Device Semantic AI Search over indexed files and tags.
 * Preserves strict Core vs Plugin separation: supplements Core Search with conceptual relevance.
 */
class SemanticSearchUseCase(
    private val semanticPlugin: ISemanticSearchEngine,
    private val searchRepository: SearchRepository,
    private val fileManagerRepository: FileManagerRepository
) {
    fun isPluginReady(): Boolean = semanticPlugin.isModelReady()

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

        val allFiles: List<FileItem> = try {
            fileManagerRepository.getCategorizedFiles(FileCategory.ALL).first()
        } catch (e: Exception) {
            emptyList()
        }

        if (allFiles.isEmpty()) return emptyList()

        val candidates = allFiles.map { file ->
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
            query = query,
            candidates = candidates,
            options = options
        )
    }
}
