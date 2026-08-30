package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.data.FileManagerRepository
import com.vvf.smartmanager.core.data.SearchRepository
import com.vvf.smartmanager.core.model.AiIntelligenceSummary
import com.vvf.smartmanager.core.model.AiSuggestedTag
import com.vvf.smartmanager.core.model.DuplicateFileGroup
import com.vvf.smartmanager.core.model.DuplicateLevel
import com.vvf.smartmanager.core.model.FileCategory
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.NearDuplicateCluster
import com.vvf.smartmanager.core.model.SemanticCandidate
import com.vvf.smartmanager.core.plugin.spi.ISemanticSearchEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn

/**
 * Phase 10: Domain use case for On-Device AI Intelligence.
 * Provides smart near-duplicate clustering with dynamic similarity threshold (70% - 95%)
 * and intelligent semantic tag suggestion.
 */
class AiIntelligenceUseCase(
    private val semanticPlugin: ISemanticSearchEngine,
    private val fileManagerRepository: FileManagerRepository,
    private val searchRepository: SearchRepository
) {
    fun isAiModelReady(): Boolean = semanticPlugin.isModelReady()

    suspend fun downloadAiModel(onProgress: (Float) -> Unit = {}): Boolean {
        return semanticPlugin.downloadModel(onProgress)
    }

    /**
     * Scans indexed and storage files for Level 3 AI near-duplicates using vector cosine similarity.
     * @param similarityThreshold Range 0.70f to 0.95f (70% to 95%)
     */
    fun scanNearDuplicates(similarityThreshold: Float = 0.80f): Flow<List<DuplicateFileGroup>> = flow {
        if (!semanticPlugin.isModelReady()) {
            emit(emptyList())
            return@flow
        }

        val clampedThreshold = similarityThreshold.coerceIn(0.70f, 0.95f)

        val allFiles = try {
            fileManagerRepository.getCategorizedFiles(FileCategory.ALL).first()
        } catch (e: Exception) {
            emptyList()
        }

        if (allFiles.size < 2) {
            emit(emptyList())
            return@flow
        }

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

        val clusters: List<NearDuplicateCluster> = semanticPlugin.findNearDuplicates(candidates, clampedThreshold)

        val duplicateGroups = clusters.map { cluster ->
            val matchPct = (cluster.averageSimilarity * 100).toInt()
            val allFilesSorted = cluster.allFiles.sortedBy { it.lastModified }
            val defaultSelected = allFilesSorted.drop(1).map { it.path }.toSet()

            DuplicateFileGroup(
                id = cluster.id,
                matchKey = "AI Similarity: $matchPct% Match",
                level = DuplicateLevel.LEVEL_3_SIMILARITY,
                sizePerFile = cluster.baseFile.sizeBytes,
                files = allFilesSorted,
                selectedPaths = defaultSelected
            )
        }

        emit(duplicateGroups)
    }.flowOn(Dispatchers.Default)

    /**
     * Suggests conceptual tags for an individual file item.
     */
    suspend fun suggestTags(fileItem: FileItem): List<AiSuggestedTag> {
        val textContent = buildString {
            append(fileItem.name)
            if (fileItem.tags.isNotEmpty()) {
                append(" ")
                append(fileItem.tags.joinToString(" "))
            }
        }
        val candidate = SemanticCandidate(fileItem = fileItem, textContent = textContent)
        return semanticPlugin.suggestTags(candidate)
    }

    /**
     * Applies an AI suggested tag to a file.
     */
    suspend fun applySuggestedTag(fileItem: FileItem, tagName: String): Result<Boolean> {
        return searchRepository.addTagToFile(fileItem.path, tagName)
    }

    /**
     * Generates a high-level summary of AI intelligence scan metrics.
     */
    fun getIntelligenceSummary(similarityThreshold: Float = 0.80f): Flow<AiIntelligenceSummary> = flow {
        val clampedThreshold = similarityThreshold.coerceIn(0.70f, 0.95f)
        val allFiles = try {
            fileManagerRepository.getCategorizedFiles(FileCategory.ALL).first()
        } catch (e: Exception) {
            emptyList()
        }

        val candidates = allFiles.map {
            SemanticCandidate(it, it.name + " " + it.tags.joinToString(" "))
        }

        val clusters = semanticPlugin.findNearDuplicates(candidates, clampedThreshold)
        val tagCount = candidates.sumOf { semanticPlugin.suggestTags(it).size }

        emit(
            AiIntelligenceSummary(
                totalScanned = allFiles.size,
                nearDuplicateClustersCount = clusters.size,
                suggestedTagsCount = tagCount,
                currentThresholdPct = (clampedThreshold * 100).toInt()
            )
        )
    }.flowOn(Dispatchers.Default)
}
