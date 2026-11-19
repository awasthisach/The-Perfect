package com.vvf.smartmanager.core.database

import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class OcrSearchDaoTest {

    @Test
    fun fullOcrTextIsSearchableThroughFtsAndLikeFallback() = runBlocking {
        val database = VVFDatabase.buildInMemoryDatabase(RuntimeEnvironment.getApplication())
        try {
            val path = "/documents/scan.pdf"
            database.fileDao().insertOrUpdate(
                FileMetadataEntity(
                    path = path,
                    name = "scan.pdf",
                    parentPath = "/documents",
                    sizeBytes = 128L,
                    mimeType = "application/pdf",
                    isDirectory = false,
                    modifiedDate = 1L
                )
            )
            val fullText = "Patient discharge summary contains rarewordxylophone and prescription details."
            database.fileDao().updateOcrTextByPath(path, fullText)

            val ftsMatches = database.searchFtsDao().searchFilesFts("rarewordxylophone*").first()
            val likeMatches = database.searchFtsDao().searchFilesFallback("xylophone").first()

            assertEquals(listOf(path), ftsMatches.map { it.path })
            assertEquals(listOf(path), likeMatches.map { it.path })
            assertTrue(database.fileDao().getByPath(path)?.ocrText == fullText)
        } finally {
            database.close()
        }
    }

    @Test
    fun hindiOcrTextIsSearchableThroughFtsAndLikeFallback() = runBlocking {
        val database = VVFDatabase.buildInMemoryDatabase(RuntimeEnvironment.getApplication())
        try {
            val path = "/documents/hindi-scan.pdf"
            database.fileDao().insertOrUpdate(
                FileMetadataEntity(
                    path = path,
                    name = "hindi-scan.pdf",
                    parentPath = "/documents",
                    sizeBytes = 512L,
                    mimeType = "application/pdf",
                    isDirectory = false,
                    modifiedDate = 3L
                )
            )
            val hindiText = "भारत की प्राचीन पांडुलिपि और देवनागरी लिपि का पाठ"
            database.fileDao().updateOcrTextByPath(path, hindiText)

            val ftsMatches = database.searchFtsDao().searchFilesFts("प्राचीन*").first()
            val likeMatches = database.searchFtsDao().searchFilesFallback("पांडुलिपि").first()

            assertEquals(listOf(path), ftsMatches.map { it.path })
            assertEquals(listOf(path), likeMatches.map { it.path })
            assertEquals(hindiText, database.fileDao().getByPath(path)?.ocrText)
        } finally {
            database.close()
        }
    }

    @Test
    fun updatingAndDeletingOcrTextKeepsFtsIndexInSync() = runBlocking {
        val database = VVFDatabase.buildInMemoryDatabase(RuntimeEnvironment.getApplication())
        try {
            val path = "/documents/revised-scan.pdf"
            database.fileDao().insertOrUpdate(
                FileMetadataEntity(
                    path = path,
                    name = "revised-scan.pdf",
                    parentPath = "/documents",
                    sizeBytes = 256L,
                    mimeType = "application/pdf",
                    isDirectory = false,
                    modifiedDate = 2L
                )
            )

            database.fileDao().updateOcrTextByPath(path, "oldmarkerquartz")
            assertEquals(
                listOf(path),
                database.searchFtsDao().searchFilesFts("oldmarkerquartz*").first().map { it.path }
            )

            database.fileDao().updateOcrTextByPath(path, "newmarkerjasper")
            assertTrue(database.searchFtsDao().searchFilesFts("oldmarkerquartz*").first().isEmpty())
            assertEquals(
                listOf(path),
                database.searchFtsDao().searchFilesFts("newmarkerjasper*").first().map { it.path }
            )

            database.fileDao().deleteByPath(path)
            assertTrue(database.searchFtsDao().searchFilesFts("newmarkerjasper*").first().isEmpty())
            assertTrue(database.searchFtsDao().searchFilesFallback("newmarkerjasper").first().isEmpty())
        } finally {
            database.close()
        }
    }
}
