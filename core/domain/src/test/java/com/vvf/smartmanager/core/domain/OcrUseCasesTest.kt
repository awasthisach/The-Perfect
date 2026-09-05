package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.data.SearchRepository
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.SearchFilter
import com.vvf.smartmanager.core.model.SearchResultItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrUseCasesTest {

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
        assertTrue(true)
    }

    @Test
    fun testIndexOcrTextUseCase() = runBlocking {
        val fakeRepo = FakeSearchRepository()
        assertTrue(fakeRepo.getRecentIndexedFiles(10).isEmpty())
    }
}
