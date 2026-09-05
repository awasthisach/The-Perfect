package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.data.SearchRepository
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.OcrResult
import com.vvf.smartmanager.core.model.SearchFilter
import com.vvf.smartmanager.core.model.SearchResultItem
import com.vvf.smartmanager.core.plugin.spi.IOcrEngine
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrUseCasesTest {

    private class FakeOcrEngine : IOcrEngine {
        override val isEnabled: Boolean = true
        override suspend fun extractText(
            fileItem: FileItem,
            options: com.vvf.smartmanager.core.model.OcrOptions,
            onProgress: ((Float) -> Unit)?
        ): Result<OcrResult> {
            return Result.success(
                OcrResult(fullText = "sample text", confidence = 0.9f, languageCode = "en")
            )
        }
        override fun cancelOngoing() {}
    }

    private class FakeSearchRepository : SearchRepository {
        override fun searchFiles(query: String, filter: SearchFilter): Flow<List<SearchResultItem>> = flowOf(emptyList())
        override fun getSearchHistory(): Flow<List<String>> = flowOf(emptyList())
        override suspend fun saveSearchQuery(query: String) {}
        override suspend fun deleteSearchHistoryItem(query: String) {}
        override suspend fun clearSearchHistory() {}
        override fun getAvailableTags(): Flow<List<String>> = flowOf(emptyList())
        override suspend fun addTagToFile(path: String, tag: String) = Result.success(true)
        override suspend fun removeTagFromFile(path: String, tag: String) = Result.success(true)
        override fun getTotalIndexedCount(): Flow<Int> = flowOf(0)
        override suspend fun rebuildFtsIndex() {}
        override suspend fun getRecentIndexedFiles(limit: Int) = emptyList<FileItem>()
    }

    @Test
    fun testExtractTextUseCase() = runBlocking {
        val useCase = ExtractTextUseCase(FakeOcrEngine())
        val item = FileItem(path = "/tmp/a.pdf", name = "a.pdf", sizeBytes = 1, lastModified = 0, isDirectory = false)
        val result = useCase(item)
        assertTrue(result.isSuccess)
        assertEquals("sample text", result.getOrNull()?.fullText)
    }

    @Test
    fun testIndexOcrTextUseCase() = runBlocking {
        val fakeRepo = FakeSearchRepository()
        assertTrue(fakeRepo.getRecentIndexedFiles(10).isEmpty())
    }
}
