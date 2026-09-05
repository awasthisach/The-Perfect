package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.data.FileManagerRepository
import com.vvf.smartmanager.core.data.SearchRepository
import com.vvf.smartmanager.core.model.FileCategory
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.FileSortOption
import com.vvf.smartmanager.core.model.SearchFilter
import com.vvf.smartmanager.core.model.SearchResultItem
import com.vvf.smartmanager.core.plugin.spi.ISemanticSearchEngine
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Test

class AiIntelligenceUseCaseTest {

    private class FakeSemantic : ISemanticSearchEngine {
        override fun isModelReady(): Boolean = true
        override suspend fun downloadModel(onProgress: (Float) -> Unit): Boolean = true
        override suspend fun searchSimilar(
            query: String,
            candidates: List<com.vvf.smartmanager.core.model.SemanticCandidate>,
            options: com.vvf.smartmanager.core.model.SemanticSearchOptions
        ) = emptyList<com.vvf.smartmanager.core.model.SemanticSearchResult>()
        override suspend fun suggestTags(fileItem: FileItem) = emptyList<com.vvf.smartmanager.core.model.AiSuggestedTag>()
    }

    private class FakeFileManager : FileManagerRepository {
        override fun getFiles(directoryPath: String, sortOption: FileSortOption, showHidden: Boolean) = flowOf(emptyList<FileItem>())
        override fun getCategorizedFiles(category: FileCategory, sortOption: FileSortOption) = flowOf(emptyList<FileItem>())
        override fun getStorageBreakdown() = flowOf(com.vvf.smartmanager.core.model.StorageBreakdown(0,0,0))
        override fun getDefaultStoragePath() = "/tmp"
        override suspend fun createDirectory(parentPath: String, directoryName: String) = Result.failure<FileItem>(UnsupportedOperationException())
        override suspend fun createFile(parentPath: String, fileName: String, content: ByteArray) = Result.failure<FileItem>(UnsupportedOperationException())
        override suspend fun deleteFile(path: String, permanent: Boolean) = Result.success(true)
        override suspend fun deleteFiles(paths: List<String>, permanent: Boolean) = Result.success(0)
        override suspend fun restoreFromTrash(paths: List<String>) = Result.success(0)
        override suspend fun emptyTrash() = Result.success(true)
        override fun getTrashFiles() = flowOf(emptyList<FileItem>())
        override suspend fun renameFile(oldPath: String, newName: String) = Result.failure<FileItem>(UnsupportedOperationException())
        override suspend fun copyFiles(sourcePaths: List<String>, destinationDirectory: String, onProgress: ((com.vvf.smartmanager.core.model.FileOperationProgress) -> Unit)?) = Result.success(0)
        override suspend fun moveFiles(sourcePaths: List<String>, destinationDirectory: String, onProgress: ((com.vvf.smartmanager.core.model.FileOperationProgress) -> Unit)?) = Result.success(0)
        override suspend fun toggleFavorite(path: String, isFavorite: Boolean) = Result.success(true)
        override fun scanDuplicates(level: com.vvf.smartmanager.core.model.DuplicateLevel) = flowOf(emptyList<com.vvf.smartmanager.core.model.DuplicateFileGroup>())
        override fun scanJunk() = flowOf(com.vvf.smartmanager.core.model.CleanerScanResult())
        override suspend fun cleanJunkItems(selectedDuplicatePaths: List<String>, selectedJunkPaths: List<String>) = Result.success(0L)
    }

    private class FakeSearchRepo : SearchRepository {
        override fun searchFiles(query: String, filter: SearchFilter) = flowOf(emptyList<SearchResultItem>())
        override fun getSearchHistory() = flowOf(emptyList<String>())
        override suspend fun saveSearchQuery(query: String) {}
        override suspend fun deleteSearchHistoryItem(query: String) {}
        override suspend fun clearSearchHistory() {}
        override fun getAvailableTags() = flowOf(emptyList<String>())
        override suspend fun addTagToFile(path: String, tag: String) = Result.success(true)
        override suspend fun removeTagFromFile(path: String, tag: String) = Result.success(true)
        override fun getTotalIndexedCount() = flowOf(0)
        override suspend fun rebuildFtsIndex() {}
        override suspend fun getRecentIndexedFiles(limit: Int) = emptyList<FileItem>()
    }

    @Test
    fun constructs() = runBlocking {
        val useCase = AiIntelligenceUseCase(FakeSemantic(), FakeFileManager(), FakeSearchRepo())
        assertNotNull(useCase)
    }
}
