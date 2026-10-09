package com.vvf.smartmanager.core.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vvf.smartmanager.core.cloud.gdrive.DriveChangePage
import com.vvf.smartmanager.core.cloud.gdrive.DriveFileListing
import com.vvf.smartmanager.core.cloud.gdrive.DriveMetadataPage
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.database.VVFDatabase
import com.vvf.smartmanager.core.database.model.DriveIndexFileEntity
import com.vvf.smartmanager.core.model.FileItem
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class OfflinePinManagerTest {
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

    private class FakeDriveService : GoogleDriveService {
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
        override suspend fun downloadFile(fileId: String, destinationPath: String): Result<Boolean> {
            File(destinationPath).apply {
                parentFile?.mkdirs()
                writeBytes(byteArrayOf(1, 2, 3, 4))
            }
            return Result.success(true)
        }
        override suspend fun getStorageQuota(): Result<Pair<Long, Long>> = Result.success(0L to 0L)
    }

    @Test
    fun pinningEvictsOldestWhenEightyFileLimitWouldBeExceeded() = runBlocking {
        val dao = database.driveIndexDao()
        val pinnedDir = File(context.filesDir, "pinned").apply { mkdirs() }
        val existingIds = (1..OfflinePinManager.MAX_PINNED_FILES).map { "old" + it.toString().padStart(30, '0') }
        existingIds.forEachIndexed { index, id ->
            val path = File(pinnedDir, "$id.bin").apply { writeBytes(byteArrayOf(1)) }
            dao.upsertFile(
                DriveIndexFileEntity(
                    driveFileId = id,
                    name = "old-$index.txt",
                    mimeType = "text/plain",
                    sizeBytes = 1L,
                    modifiedTimeMs = 100L,
                    pinnedPath = path.absolutePath,
                    pinnedAtMs = (index + 1).toLong(),
                    pinnedModifiedTimeMs = 100L,
                    indexStatus = "TEXT_INDEXED"
                )
            )
        }
        val targetId = "new" + "9".repeat(30)
        dao.upsertFile(
            DriveIndexFileEntity(
                driveFileId = targetId,
                name = "new.txt",
                mimeType = "text/plain",
                sizeBytes = 4L,
                modifiedTimeMs = 200L
            )
        )

        val manager = OfflinePinManager(context, FakeDriveService(), dao)
        val pinned = manager.pin(targetId).getOrThrow()

        assertEquals(targetId, pinned.driveFileId)
        assertNotNull(pinned.contentSha256)
        assertEquals(OfflinePinManager.MAX_PINNED_FILES, dao.getPinnedFilesOldestFirst().size)
        assertFalse(File(pinnedDir, "${existingIds.first()}.bin").exists())
        assertTrue(dao.getByDriveId(existingIds.first())?.pinnedPath == null)
    }
}
