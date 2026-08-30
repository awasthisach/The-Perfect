package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.data.FileManagerRepository
import com.vvf.smartmanager.core.data.SearchRepository
import com.vvf.smartmanager.core.model.AiSuggestedTag
import com.vvf.smartmanager.core.model.CleanerScanResult
import com.vvf.smartmanager.core.model.DuplicateFileGroup
import com.vvf.smartmanager.core.model.DuplicateLevel
import com.vvf.smartmanager.core.model.FileCategory
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.FileOperationProgress
import com.vvf.smartmanager.core.model.FileSortOption
import com.vvf.smartmanager.core.model.NearDuplicateCluster
import com.vvf.smartmanager.core.model.SearchFilter
import com.vvf.smartmanager.core.model.SearchResultItem
import com.vvf.smartmanager.core.model.SemanticCandidate
import com.vvf.smartmanager.core.model.SemanticSearchOptions
import com.vvf.smartmanager.core.model.SemanticSearchResult
import com.vvf.smartmanager.core.model.StorageBreakdown
import com.vvf.smartmanager.core.plugin.spi.ISemanticSearchEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiIntelligenceUseCaseTest {

    private class FakeSemanticEngine : ISemanticSearchEngine {
        var isReady = true
        override fun isModelReady(): Boolean = isReady
        override suspend fun downloadModel(progressCallback: (Float) -> Unit): Boolean {
            isReady = true
            progressCallback(1.0f)
            return true
        }

        override suspend fun generateEmbedding(text: String): FloatArray = FloatArray(128) { 0.5f }

        override suspend fun searchSimilar(
            query: String,
            candidates: List<SemanticCandidate>,
            options: SemanticSearchOptions
        ): List<SemanticSearchResult> = emptyList()

        override fun computeCosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float = 0.85f

        override suspend fun findNearDuplicates(
            candidates: List<SemanticCandidate>,
            similarityThreshold: Float
        ): List<NearDuplicateCluster> {
            if (candidates.size < 2) return emptyList()
            val base = candidates[0].fileItem
            val similar = candidates[1].fileItem
            return listOf(
                NearDuplicateCluster(
                    id = "cluster_1",
                    baseFile = base,
                    similarFiles = listOf(similar),
                    averageSimilarity = similarityThreshold.coerceAtLeast(0.85f)
                )
            )
        }

        override suspend fun suggestTags(candidate: SemanticCandidate): List<AiSuggestedTag> {
            return listOf(
                AiSuggestedTag("finance", 0.92f, "Financial invoice detected"),
                AiSuggestedTag("tax", 0.88f, "Taxation keyword found")
            )
        }
    }

    private class FakeFileManagerRepo : FileManagerRepository {
        val sampleFiles = listOf(
            FileItem(
                path = "/storage/emulated/0/DCIM/IMG_001.jpg",
                name = "IMG_001.jpg",
                sizeBytes = 4096000L,
                lastModified = 1000L,
                isDirectory = false,
                mimeType = "image/jpeg"
            ),
            FileItem(
                path = "/storage/emulated/0/DCIM/IMG_001_edit.jpg",
                name = "IMG_001_edit.jpg",
                sizeBytes = 4100000L,
                lastModified = 2000L,
                isDirectory = false,
                mimeType = "image/jpeg"
            )
        )

        override fun getCategorizedFiles(category: FileCategory, sortOption: FileSortOption): Flow<List<FileItem>> = flowOf(sampleFiles)
        override fun getFiles(directoryPath: String, sortOption: FileSortOption, showHidden: Boolean): Flow<List<FileItem>> = flowOf(sampleFiles)
        override fun getStorageBreakdown(): Flow<StorageBreakdown> = flowOf()
        override fun getDefaultStoragePath(): String = "/storage/emulated/0"
        override suspend fun createDirectory(parentPath: String, directoryName: String): Result<FileItem> = Result.success(FileItem("", "", 0, 0, true))
        override suspend fun createFile(parentPath: String, fileName: String, content: ByteArray): Result<FileItem> = Result.success(FileItem("", "", 0, 0, false))
        override suspend fun deleteFile(path: String, permanent: Boolean): Result<Boolean> = Result.success(true)
        override suspend fun deleteFiles(paths: List<String>, permanent: Boolean): Result<Int> = Result.success(paths.size)
        override suspend fun restoreFromTrash(paths: List<String>): Result<Int> = Result.success(paths.size)
        override suspend fun emptyTrash(): Result<Boolean> = Result.success(true)
        override fun getTrashFiles(): Flow<List<FileItem>> = flowOf()
        override suspend fun renameFile(oldPath: String, newName: String): Result<FileItem> = Result.success(FileItem("", "", 0, 0, false))
        override suspend fun copyFiles(sourcePaths: List<String>, destinationDirectory: String, onProgress: ((FileOperationProgress) -> Unit)?): Result<Int> = Result.success(sourcePaths.size)
        override suspend fun moveFiles(sourcePaths: List<String>, destinationDirectory: String, onProgress: ((FileOperationProgress) -> Unit)?): Result<Int> = Result.success(sourcePaths.size)
        override suspend fun toggleFavorite(path: String, isFavorite: Boolean): Result<Boolean> = Result.success(true)
        override fun scanDuplicates(level: DuplicateLevel): Flow<List<DuplicateFileGroup>> = flowOf()
        override fun scanJunk(): Flow<CleanerScanResult> = flowOf()
        override suspend fun cleanJunkItems(selectedDuplicatePaths: List<String>, selectedJunkPaths: List<String>): Result<Long> = Result.success(0L)
    }

    private class FakeSearchRepo : SearchRepository {
        val assignedTags = mutableMapOf<String, MutableList<String>>()

        override fun searchFiles(query: String, filter: SearchFilter): Flow<List<SearchResultItem>> = flowOf()
        override fun getSearchHistory(): Flow<List<String>> = flowOf()
        override suspend fun saveSearchQuery(query: String) {}
        override suspend fun deleteSearchHistoryItem(query: String) {}
        override suspend fun clearSearchHistory() {}
        override fun getAvailableTags(): Flow<List<String>> = flowOf()
        override suspend fun addTagToFile(path: String, tag: String): Result<Boolean> {
            assignedTags.getOrPut(path) { mutableListOf() }.add(tag)
            return Result.success(true)
        }
        override suspend fun removeTagFromFile(path: String, tag: String): Result<Boolean> = Result.success(true)
        override fun getTotalIndexedCount(): Flow<Int> = flowOf(2)
        override suspend fun rebuildFtsIndex() {}
    }

    @Test
    fun testLevel3NearDuplicateScanWithSliderThreshold() = runBlocking {
        val engine = FakeSemanticEngine()
        val fileRepo = FakeFileManagerRepo()
        val searchRepo = FakeSearchRepo()
        val useCase = AiIntelligenceUseCase(engine, fileRepo, searchRepo)

        assertTrue(useCase.isAiModelReady())

        // Test with 80% threshold
        val groups = useCase.scanNearDuplicates(0.80f).first()
        assertEquals(1, groups.size)
        val group = groups[0]
        assertEquals(DuplicateLevel.LEVEL_3_SIMILARITY, group.level)
        assertEquals(2, group.files.size)
        // Check default selection keeps oldest (/DCIM/IMG_001.jpg) and selects /DCIM/IMG_001_edit.jpg
        assertTrue(group.selectedPaths.contains("/storage/emulated/0/DCIM/IMG_001_edit.jpg"))
    }

    @Test
    fun testSuggestTagsAndApply() = runBlocking {
        val engine = FakeSemanticEngine()
        val fileRepo = FakeFileManagerRepo()
        val searchRepo = FakeSearchRepo()
        val useCase = AiIntelligenceUseCase(engine, fileRepo, searchRepo)

        val targetFile = fileRepo.sampleFiles[0]
        val suggestedTags = useCase.suggestTags(targetFile)
        assertEquals(2, suggestedTags.size)
        assertEquals("finance", suggestedTags[0].tagName)

        val applyResult = useCase.applySuggestedTag(targetFile, "finance")
        assertTrue(applyResult.isSuccess)
        assertTrue(searchRepo.assignedTags[targetFile.path]?.contains("finance") == true)
    }
}
