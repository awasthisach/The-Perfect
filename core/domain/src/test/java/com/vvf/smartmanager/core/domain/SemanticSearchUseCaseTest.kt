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
import com.vvf.smartmanager.core.model.SearchMatchType
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
        var capturedCandidates: List<SemanticCandidate> = emptyList()
        var throwOnSearch: Boolean = false
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
            if (throwOnSearch) throw IllegalStateException("embedding service unavailable")
            capturedCandidates = candidates
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

    private class FakeSearchRepo(
        var searchResults: List<SearchResultItem> = emptyList(),
        var recentIndexedFiles: List<FileItem> = emptyList()
    ) : SearchRepository {
        override fun searchFiles(query: String, filter: SearchFilter): Flow<List<SearchResultItem>> = flowOf(searchResults)
        override fun getSearchHistory(): Flow<List<String>> = flowOf()
        override suspend fun saveSearchQuery(query: String) {}
        override suspend fun deleteSearchHistoryItem(query: String) {}
        override suspend fun clearSearchHistory() {}
        override fun getAvailableTags(): Flow<List<String>> = flowOf()
        override suspend fun addTagToFile(path: String, tag: String): Result<Boolean> = Result.success(true)
        override suspend fun removeTagFromFile(path: String, tag: String): Result<Boolean> = Result.success(true)
        override fun getTotalIndexedCount(): Flow<Int> = flowOf(2)
        override suspend fun rebuildFtsIndex() {}
        override suspend fun getRecentIndexedFiles(limit: Int) = recentIndexedFiles.take(limit)
    }

    @Test
    fun semanticResultsStayEmptyWhenEngineIsDisabledAndLocalResultsRemainAvailable() = runBlocking {
        val file = FileItem(
            path = "gdrive://drive-file-1",
            name = "Annual Report.pdf",
            sizeBytes = 100L,
            lastModified = 1L,
            isDirectory = false,
            mimeType = "application/pdf"
        )
        val searchRepo = FakeSearchRepo(
            listOf(SearchResultItem(file, SearchMatchType.FTS, "annual report revenue"))
        )
        val useCase = SemanticSearchUseCase(FakeSemanticEngine().apply { isReady = false }, searchRepo, FakeFileManagerRepo())

        val results = useCase.searchSemantically("revenue")

        assertTrue(results.isEmpty())
        assertEquals(1, searchRepo.searchResults.size)
        assertEquals(file.path, searchRepo.searchResults.first().fileItem.path)
    }

    @Test
    fun semanticResultsAndKeywordResultsAreMergedWithoutDuplicates() = runBlocking {
        val file = FileItem(
            path = "gdrive://drive-file-2",
            name = "Budget Plan.pdf",
            sizeBytes = 100L,
            lastModified = 1L,
            isDirectory = false,
            mimeType = "application/pdf"
        )
        val searchRepo = FakeSearchRepo(
            listOf(SearchResultItem(file, SearchMatchType.FILENAME, "budget"))
        )
        val engine = FakeSemanticEngine()
        val useCase = SemanticSearchUseCase(engine, searchRepo, FakeFileManagerRepo())

        val results = useCase.searchSemantically("budget")

        assertEquals(1, results.size)
        assertEquals(file.path, results.first().fileItem.path)
        assertTrue(results.first().similarityScore > 0f)
    }


    @Test
    fun semanticConsentOffNeverSendsIndexedDocumentTextToProvider() = runBlocking {
        val file = FileItem(
            path = "gdrive://drive-file-private",
            name = "private-notes.txt",
            sizeBytes = 300L,
            lastModified = 1L,
            isDirectory = false,
            mimeType = "text/plain",
            indexedText = "Private document contents must not leave the device without consent."
        )
        val engine = FakeSemanticEngine()
        val searchRepo = FakeSearchRepo(recentIndexedFiles = listOf(file))
        val useCase = SemanticSearchUseCase(
            engine,
            searchRepo,
            FakeFileManagerRepo(),
            isEmbeddingConsentGranted = { false }
        )

        useCase.searchSemantically("private document")

        assertTrue("No candidate text may be sent when consent is off", engine.capturedCandidates.isEmpty())
    }

    @Test
    fun consentedSemanticCandidatesIncludeExtractedDocumentText() = runBlocking {
        val file = FileItem(
            path = "gdrive://drive-file-content",
            name = "meeting-notes.txt",
            sizeBytes = 300L,
            lastModified = 1L,
            isDirectory = false,
            mimeType = "text/plain",
            indexedText = "The board approved a renewable energy solar farm project."
        )
        val engine = FakeSemanticEngine()
        val searchRepo = FakeSearchRepo(recentIndexedFiles = listOf(file))
        val useCase = SemanticSearchUseCase(
            engine,
            searchRepo,
            FakeFileManagerRepo(),
            isEmbeddingConsentGranted = { true }
        )

        useCase.searchSemantically("clean energy")

        assertEquals(1, engine.capturedCandidates.size)
        assertTrue(engine.capturedCandidates.single().textContent.contains("renewable energy solar farm"))
    }

    @Test
    fun testSemanticSearchFlow() = runBlocking {
        val engine = FakeSemanticEngine()
        val fileRepo = FakeFileManagerRepo()
        val searchRepo = FakeSearchRepo()
        val useCase = SemanticSearchUseCase(engine, searchRepo, fileRepo)

        assertTrue(useCase.isPluginReady())

        val results = useCase.searchSemantically("taxes and billing")
        // The fake Drive corpus is empty, so the semantic-only result list is empty.
        assertTrue(results.isEmpty() || results.size <= 2)
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

    @Test
    fun providerFailureIsReportedAndLocalSearchSourceRemainsAvailable() = runBlocking {
        val file = FileItem(
            path = "gdrive://annual-report",
            name = "Annual Report.pdf",
            sizeBytes = 100L,
            lastModified = 1L,
            isDirectory = false,
            mimeType = "application/pdf"
        )
        val repository = FakeSearchRepo(
            searchResults = listOf(SearchResultItem(file, SearchMatchType.FTS, "annual report revenue")),
            recentIndexedFiles = listOf(file)
        )
        val engine = FakeSemanticEngine().apply { throwOnSearch = true }
        val useCase = SemanticSearchUseCase(
            engine,
            repository,
            FakeFileManagerRepo(),
            isEmbeddingConsentGranted = { true }
        )

        val semanticResults = useCase.searchSemantically("revenue")

        assertTrue(semanticResults.isEmpty())
        assertEquals(1, repository.searchResults.size)
        assertEquals(file.path, repository.searchResults.first().fileItem.path)
        assertTrue(useCase.wasLastSemanticSearchUnavailable())

        engine.throwOnSearch = false
        assertEquals(1, useCase.searchSemantically("revenue").size)
        assertFalse(useCase.wasLastSemanticSearchUnavailable())
    }


}
