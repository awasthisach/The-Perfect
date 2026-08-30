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
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticSearchUseCaseTest {

    private class FakeSemanticEngine : ISemanticSearchEngine {
        var isReady = true
        override fun isModelReady(): Boolean = isReady
        override suspend fun downloadModel(progressCallback: (Float) -> Unit): Boolean {
            isReady = true
            progressCallback(1.0f)
            return true
        }

        override suspend fun generateEmbedding(text: String): FloatArray {
            return FloatArray(128) { 0.5f }
        }

        override suspend fun searchSimilar(
            query: String,
            candidates: List<SemanticCandidate>,
            options: SemanticSearchOptions
        ): List<SemanticSearchResult> {
            return candidates.map {
                SemanticSearchResult(it.fileItem, 0.88f, "Semantic match for $query")
            }
        }

        override fun computeCosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float = 0.88f

        override suspend fun findNearDuplicates(
            candidates: List<SemanticCandidate>,
            similarityThreshold: Float
        ): List<NearDuplicateCluster> = emptyList()

        override suspend fun suggestTags(candidate: SemanticCandidate): List<AiSuggestedTag> = emptyList()
    }

    private class FakeFileManagerRepo : FileManagerRepository {
        val sampleFiles = listOf(
            FileItem(
                path = "/storage/emulated/0/docs/tax_invoice.pdf",
                name = "tax_invoice.pdf",
                sizeBytes = 1024L,
                lastModified = 0L,
                isDirectory = false,
                mimeType = "application/pdf",
                tags = listOf("finance", "gst")
            ),
            FileItem(
                path = "/storage/emulated/0/docs/doctor_report.pdf",
                name = "doctor_report.pdf",
                sizeBytes = 2048L,
                lastModified = 0L,
                isDirectory = false,
                mimeType = "application/pdf",
                tags = listOf("medical", "health")
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
        override fun searchFiles(query: String, filter: SearchFilter): Flow<List<SearchResultItem>> = flowOf()
        override fun getSearchHistory(): Flow<List<String>> = flowOf()
        override suspend fun saveSearchQuery(query: String) {}
        override suspend fun deleteSearchHistoryItem(query: String) {}
        override suspend fun clearSearchHistory() {}
        override fun getAvailableTags(): Flow<List<String>> = flowOf()
        override suspend fun addTagToFile(path: String, tag: String): Result<Boolean> = Result.success(true)
        override suspend fun removeTagFromFile(path: String, tag: String): Result<Boolean> = Result.success(true)
        override fun getTotalIndexedCount(): Flow<Int> = flowOf(2)
        override suspend fun rebuildFtsIndex() {}
    }

    @Test
    fun testSemanticSearchFlow() = runBlocking {
        val engine = FakeSemanticEngine()
        val fileRepo = FakeFileManagerRepo()
        val searchRepo = FakeSearchRepo()
        val useCase = SemanticSearchUseCase(engine, searchRepo, fileRepo)

        assertTrue(useCase.isPluginReady())

        val results = useCase.searchSemantically("taxes and billing")
        assertEquals(2, results.size)
        assertEquals("/storage/emulated/0/docs/tax_invoice.pdf", results[0].fileItem.path)
        assertEquals(0.88f, results[0].similarityScore, 0.001f)
    }

    @Test
    fun testSemanticSearchWhenEngineNotReady() = runBlocking {
        val engine = FakeSemanticEngine().apply { isReady = false }
        val fileRepo = FakeFileManagerRepo()
        val searchRepo = FakeSearchRepo()
        val useCase = SemanticSearchUseCase(engine, searchRepo, fileRepo)

        assertFalse(useCase.isPluginReady())
        val results = useCase.searchSemantically("any query")
        assertTrue(results.isEmpty())
    }
}
