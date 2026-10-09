package com.vvf.smartmanager.core.background.drive

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vvf.smartmanager.core.cloud.gdrive.DriveChangePage
import com.vvf.smartmanager.core.cloud.gdrive.DriveFileListing
import com.vvf.smartmanager.core.cloud.gdrive.DriveMetadataPage
import com.vvf.smartmanager.core.cloud.gdrive.DriveMetadataRecord
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.database.VVFDatabase
import com.vvf.smartmanager.core.model.FileItem
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DriveMetadataSyncCoordinatorTest {
    private lateinit var database: VVFDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = VVFDatabase.buildInMemoryDatabase(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private class FakeDriveService : GoogleDriveService {
        val requestedPageTokens = mutableListOf<String?>()
        private var pageIndex = 0
        val fullPages = listOf(
            DriveMetadataPage(
                files = listOf(
                    DriveMetadataRecord(
                        fileId = "1AbCdEfGhIjKlMnOpQrStUvWxYz_12345",
                        name = "contract.pdf",
                        mimeType = "application/pdf",
                        sizeBytes = 1024L,
                        modifiedTimeMs = 100L,
                        parentIds = emptyList(),
                        webViewLink = null,
                        starred = false,
                        trashed = false
                    )
                ),
                nextPageToken = "page-2"
            ),
            DriveMetadataPage(
                files = listOf(
                    DriveMetadataRecord(
                        fileId = "2AbCdEfGhIjKlMnOpQrStUvWxYz_12345",
                        name = "budget.xlsx",
                        mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        sizeBytes = 2048L,
                        modifiedTimeMs = 200L,
                        parentIds = listOf("3AbCdEfGhIjKlMnOpQrStUvWxYz_12345"),
                        webViewLink = "https://drive.google.com/file/d/example/view",
                        starred = true,
                        trashed = false
                    )
                ),
                nextPageToken = null
            )
        )

        override fun setAccessToken(token: String?) = Unit
        override suspend fun authenticate(): Result<Boolean> = Result.success(true)
        override suspend fun listDriveFiles(folderId: String): Result<List<FileItem>> = Result.success(emptyList())
        override suspend fun listAllDriveFiles(): Result<DriveFileListing> =
            Result.success(DriveFileListing(emptyList(), false, null))
        override suspend fun listDriveMetadataPage(pageToken: String?, pageSize: Int): Result<DriveMetadataPage> {
            requestedPageTokens += pageToken
            val page = fullPages.getOrNull(pageIndex++) ?: return Result.failure(IllegalStateException("No fake page"))
            return Result.success(page)
        }
        override suspend fun getStartPageToken(): Result<String> = Result.success("start-1")
        override suspend fun listChanges(pageToken: String): Result<DriveChangePage> =
            Result.success(DriveChangePage(emptyList(), null, "start-2"))
        override suspend fun createFolder(name: String, parentFolderId: String): Result<String> = Result.success("4AbCdEfGhIjKlMnOpQrStUvWxYz_12345")
        override suspend fun moveFile(fileId: String, targetFolderId: String): Result<Boolean> = Result.success(true)
        override suspend fun starFile(fileId: String, starred: Boolean): Result<Boolean> = Result.success(true)
        override suspend fun uploadFile(localFile: FileItem, remoteFolderId: String): Result<String> = Result.success("5AbCdEfGhIjKlMnOpQrStUvWxYz_12345")
        override suspend fun downloadFile(fileId: String, destinationPath: String): Result<Boolean> = Result.success(true)
        override suspend fun getStorageQuota(): Result<Pair<Long, Long>> = Result.success(0L to 0L)
    }

    @Test
    fun fullListResumesByPageAndCommitsChangesCursor() = runBlocking {
        val dao = database.driveIndexDao()
        val service = FakeDriveService()
        val coordinator = DriveMetadataSyncCoordinator(service, dao)

        assertTrue(coordinator.runOneBatch().getOrThrow()) // Capture start token and persist first-page cursor.
        assertTrue(coordinator.runOneBatch().getOrThrow()) // First 50-file page.
        assertTrue(coordinator.runOneBatch().getOrThrow()) // Second page; switch to Changes API.
        assertFalse(coordinator.runOneBatch().getOrThrow()) // Commit the new start token.

        assertEquals(listOf(null, "page-2"), service.requestedPageTokens)
        assertEquals(2, dao.getIndexedFileCount())
        assertEquals("start-2", dao.getSyncState()?.changeStartPageToken)
        assertFalse(dao.getSyncState()?.indexingInProgress ?: true)
        assertEquals("budget.xlsx", dao.getByDriveId("2AbCdEfGhIjKlMnOpQrStUvWxYz_12345")?.name)
    }
}
