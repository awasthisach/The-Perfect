package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.data.SearchRepository
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.SearchFilter
import com.vvf.smartmanager.core.model.SearchResultItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class OcrCleanupCancellationTest {
    private val file = FileItem(
        path = "/test/document.png", name = "document.png", sizeBytes = 1L,
        lastModified = 0L, isDirectory = false, mimeType = "image/png"
    )

    @Test
    fun failedRemovalResultIsNotReportedAsSuccess() = runBlocking {
        val failure = IllegalStateException("tag write failed")
        val repository = CleanupRepository { Result.failure(failure) }
        val result = OcrIndexingService(repository).removeOcrIndex(file)
        assertTrue(result.isFailure)
        assertSame(failure, result.exceptionOrNull())
    }

    @Test
    fun cancellationInsideAResultIsRethrown() = runBlocking {
        val repository = CleanupRepository { Result.failure(CancellationException("cancel cleanup")) }
        try {
            OcrIndexingService(repository).removeOcrIndex(file)
            fail("Cancellation must not be reported as a successful cleanup")
        } catch (cancelled: CancellationException) {
            assertEquals("cancel cleanup", cancelled.message)
        }
    }

    @Test
    fun thrownCancellationRemainsCancellation() = runBlocking {
        val repository = CleanupRepository { throw CancellationException("cancel cleanup") }
        try {
            OcrIndexingService(repository).removeOcrIndex(file)
            fail("Cancellation must propagate")
        } catch (cancelled: CancellationException) {
            assertEquals("cancel cleanup", cancelled.message)
        }
    }

    @Test
    fun thrownOrdinaryFailureRemainsAFailedResult() = runBlocking {
        val failure = IllegalStateException("database unavailable")
        val repository = CleanupRepository { throw failure }
        val result = OcrIndexingService(repository).removeOcrIndex(file)
        assertTrue(result.isFailure)
        assertSame(failure, result.exceptionOrNull())
    }

    @Test
    fun successfulCleanupStillSucceeds() = runBlocking {
        val repository = CleanupRepository { Result.success(true) }
        val result = OcrIndexingService(repository).removeOcrIndex(file)
        assertEquals(true, result.getOrThrow())
        assertEquals(file.path, repository.removedPath)
        assertEquals("ocr", repository.removedTag)
    }

    private class CleanupRepository(
        private val remove: suspend () -> Result<Boolean>
    ) : SearchRepository {
        var removedPath: String? = null
        var removedTag: String? = null
        override suspend fun removeTagFromFile(path: String, tag: String): Result<Boolean> {
            removedPath = path
            removedTag = tag
            return remove()
        }
        override fun searchFiles(query: String, filter: SearchFilter): Flow<List<SearchResultItem>> = flowOf(emptyList())
        override fun getSearchHistory(): Flow<List<String>> = flowOf(emptyList())
        override suspend fun saveSearchQuery(query: String) {}
        override suspend fun deleteSearchHistoryItem(query: String) {}
        override suspend fun clearSearchHistory() {}
        override fun getAvailableTags(): Flow<List<String>> = flowOf(emptyList())
        override suspend fun addTagToFile(path: String, tag: String): Result<Boolean> = error("Unexpected tag addition")
        override fun getTotalIndexedCount(): Flow<Int> = flowOf(0)
        override suspend fun rebuildFtsIndex() {}
        override suspend fun getRecentIndexedFiles(limit: Int): List<FileItem> = emptyList()
    }
}
