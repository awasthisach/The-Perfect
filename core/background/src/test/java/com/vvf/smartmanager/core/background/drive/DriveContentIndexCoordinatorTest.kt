package com.vvf.smartmanager.core.background.drive

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vvf.smartmanager.core.cloud.gdrive.DriveChangePage
import com.vvf.smartmanager.core.cloud.gdrive.DriveFileListing
import com.vvf.smartmanager.core.cloud.gdrive.DriveMetadataPage
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.database.VVFDatabase
import com.vvf.smartmanager.core.database.model.DriveIndexFileEntity
import com.vvf.smartmanager.core.domain.DriveTextExtractor
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.OcrOptions
import com.vvf.smartmanager.core.model.OcrProgress
import com.vvf.smartmanager.core.model.OcrResult
import com.vvf.smartmanager.core.plugin.spi.IOcrEngine
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DriveContentIndexCoordinatorTest {
    private lateinit var context: Context
    private lateinit var database: VVFDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = VVFDatabase.buildInMemoryDatabase(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private class FakeDriveService(private val content: String) : GoogleDriveService {
        var downloads = 0
        override fun setAccessToken(token: String?) = Unit
        override suspend fun authenticate(): Result<Boolean> = Result.success(true)
        override suspend fun listDriveFiles(folderId: String): Result<List<FileItem>> = Result.success(emptyList())
        override suspend fun listAllDriveFiles(): Result<DriveFileListing> =
            Result.success(DriveFileListing(emptyList(), false, null))
        override suspend fun listDriveMetadataPage(pageToken: String?, pageSize: Int): Result<DriveMetadataPage> =
            Result.success(DriveMetadataPage(emptyList(), null))
        override suspend fun getStartPageToken(): Result<String> = Result.success("start")
        override suspend fun listChanges(pageToken: String): Result<DriveChangePage> =
            Result.success(DriveChangePage(emptyList(), null, "start"))
        override suspend fun createFolder(name: String, parentFolderId: String): Result<String> = Result.success("folder")
        override suspend fun moveFile(fileId: String, targetFolderId: String): Result<Boolean> = Result.success(true)
        override suspend fun starFile(fileId: String, starred: Boolean): Result<Boolean> = Result.success(true)
        override suspend fun uploadFile(localFile: FileItem, remoteFolderId: String): Result<String> = Result.success("file")
        override suspend fun downloadFile(fileId: String, destinationPath: String): Result<Boolean> = Result.success(true)
        override suspend fun downloadForIndexing(fileId: String, mimeType: String, destinationPath: String): Result<String> {
            downloads++
            File(destinationPath).writeText(content)
            return Result.success("text/plain")
        }
        override suspend fun getStorageQuota(): Result<Pair<Long, Long>> = Result.success(0L to 0L)
    }

    private class FakeOcrEngine : IOcrEngine {
        override val pluginId = "test-ocr"
        override val displayName = "Test OCR"
        override val version = "1"
        override val isEnabled = true
        override suspend fun extractText(
            fileItem: FileItem,
            options: OcrOptions,
            onProgress: ((OcrProgress) -> Unit)?
        ): Result<OcrResult> = Result.success(OcrResult("image OCR text"))
        override suspend fun isModelDownloaded(): Boolean = true
        override suspend fun downloadModel(progressCallback: (Float) -> Unit): Boolean = true
        override fun cancelOngoing() = Unit
    }

    private fun record(mimeType: String = "text/plain") = DriveIndexFileEntity(
        driveFileId = "1AbCdEfGhIjKlMnOpQrStUvWxYz_12345",
        name = if (mimeType.startsWith("image/")) "scan.png" else "notes.txt",
        mimeType = mimeType,
        sizeBytes = 32L,
        modifiedTimeMs = 100L
    )

    @Test
    fun extractsLocalTextAndHashesOnlyDownloadedBytes() = runBlocking {
        val dao = database.driveIndexDao()
        val record = record()
        dao.upsertFile(record)
        val service = FakeDriveService("local extracted content")
        val coordinator = DriveContentIndexCoordinator(
            context,
            service,
            dao,
            DriveTextExtractor(context),
            FakeOcrEngine(),
            fullContentConsentGranted = { false },
            autoOcrEnabled = { false }
        )

        assertFalse(coordinator.runBatch().getOrThrow())
        val indexed = dao.getByDriveId(record.driveFileId)
        assertEquals("local extracted content", indexed?.extractedText)
        assertEquals("NATIVE_TEXT", indexed?.extractionSource)
        assertEquals("TEXT_INDEXED", indexed?.indexStatus)
        assertNotNull(indexed?.contentSha256)
        assertEquals(1, service.downloads)
    }

    @Test
    fun imageOcrIsSkippedWithoutFullContentConsent() = runBlocking {
        val dao = database.driveIndexDao()
        val record = record("image/png")
        dao.upsertFile(record)
        val service = FakeDriveService("image bytes")
        val coordinator = DriveContentIndexCoordinator(
            context,
            service,
            dao,
            DriveTextExtractor(context),
            FakeOcrEngine(),
            fullContentConsentGranted = { false },
            autoOcrEnabled = { false }
        )

        assertFalse(coordinator.runBatch().getOrThrow())
        val indexed = dao.getByDriveId(record.driveFileId)
        assertEquals("OCR_CONSENT_REQUIRED", indexed?.indexStatus)
        assertEquals("", indexed?.extractedText)
        assertNull(indexed?.contentSha256)
        assertEquals(0, service.downloads)
    }
}
