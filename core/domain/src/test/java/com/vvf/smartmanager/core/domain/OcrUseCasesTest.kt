package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.data.SearchRepository
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.OcrOptions
import com.vvf.smartmanager.core.model.OcrProgress
import com.vvf.smartmanager.core.model.OcrResult
import com.vvf.smartmanager.core.model.SearchFilter
import com.vvf.smartmanager.core.model.SearchResultItem
import com.vvf.smartmanager.core.plugin.spi.OcrPluginSPI
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrUseCasesTest {

    private class FakeOcrPlugin(override var isEnabled: Boolean = true) : OcrPluginSPI {
        override suspend fun extractText(
            fileItem: FileItem,
            options: OcrOptions,
            onProgress: ((OcrProgress) -> Unit)?
        ): Result<OcrResult> {
            return Result.success(
                OcrResult(
                    fullText = "INVOICE NUMBER #8849 Total Amount: $450.00 Thank you for your payment",
                    totalWords = 10,
                    totalLines = 1,
                    pageCount = 1,
                    sourceFilePath = fileItem.path
                )
            )
        }

        override suspend fun isModelDownloaded(): Boolean = true
        override suspend fun downloadModel(progressCallback: (Float) -> Unit): Boolean = true
        override fun cancelOngoing() {}
    }

    private class FakeSearchRepository : SearchRepository {
        val addedTags = mutableMapOf<String, MutableList<String>>()

        override fun searchFiles(query: String, filter: SearchFilter): Flow<List<SearchResultItem>> = emptyFlow()
        override fun getSearchHistory(): Flow<List<String>> = flowOf(emptyList())
        override suspend fun saveSearchQuery(query: String) {}
        override suspend fun deleteSearchHistoryItem(query: String) {}
        override suspend fun clearSearchHistory() {}
        override fun getAvailableTags(): Flow<List<String>> = flowOf(emptyList())
        override suspend fun addTagToFile(path: String, tag: String): Result<Boolean> {
            addedTags.getOrPut(path) { mutableListOf() }.add(tag)
            return Result.success(true)
        }
        override suspend fun removeTagFromFile(path: String, tag: String): Result<Boolean> = Result.success(true)
        override fun getTotalIndexedCount(): Flow<Int> = flowOf(10)
        override suspend fun rebuildFtsIndex() {}
        override suspend fun getRecentIndexedFiles(limit: Int) = emptyList<com.vvf.smartmanager.core.model.FileItem>()
    }

    @Test
    fun testExtractTextUseCase() = runBlocking {
        val plugin = FakeOcrPlugin()
        val useCase = ExtractTextUseCase(plugin)
        val testFile = FileItem(
            path = "/dummy/invoice.png",
            name = "invoice.png",
            sizeBytes = 5000L,
            lastModified = System.currentTimeMillis(),
            isDirectory = false,
            mimeType = "image/png"
        )

        val result = useCase(testFile)
        assertTrue(result.isSuccess)
        val ocrResult = result.getOrNull()!!
        assertTrue(ocrResult.fullText.contains("INVOICE"))
        assertEquals(10, ocrResult.totalWords)
    }

    @Test
    fun testIndexOcrTextUseCase() = runBlocking {
        val fakeRepo = FakeSearchRepository()
        val indexUseCase = IndexOcrTextUseCase(fakeRepo)
        val testFile = FileItem(
            path = "/dummy/receipt.jpg",
            name = "receipt.jpg",
            sizeBytes = 2000L,
            lastModified = System.currentTimeMillis(),
            isDirectory = false,
            mimeType = "image/jpeg"
        )

        val ocrResult = OcrResult(
            fullText = "Hospital Medical Prescription Pharmacy Medicines Doctor Advice Consultation",
            totalWords = 7,
            totalLines = 1
        )

        val result = indexUseCase(testFile, ocrResult)
        assertTrue(result.isSuccess)
    }
}
