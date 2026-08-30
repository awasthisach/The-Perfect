package com.vvf.smartmanager.core.cloud.gdrive

import com.vvf.smartmanager.core.model.CloudAccount
import com.vvf.smartmanager.core.model.CloudProviderType
import com.vvf.smartmanager.core.model.FileItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class GoogleDriveServiceTest {

    private class FakeGoogleDriveService : GoogleDriveService {
        private var isAuth = false
        private var usedStorage = 4_500_000_000L
        private val totalStorage = 15_000_000_000L

        override suspend fun authenticate(): Result<Boolean> {
            isAuth = true
            return Result.success(true)
        }

        override suspend fun listDriveFiles(folderId: String): Result<List<FileItem>> {
            if (!isAuth) return Result.failure(IllegalStateException("Not authenticated"))
            return Result.success(
                listOf(
                    FileItem(
                        path = "gdrive://root/VVF_Backup_2026.bak",
                        name = "VVF_Backup_2026.bak",
                        sizeBytes = 14 * 1024 * 1024L,
                        lastModified = System.currentTimeMillis(),
                        isDirectory = false,
                        mimeType = "application/octet-stream"
                    ),
                    FileItem(
                        path = "gdrive://root/Invoice_2026.pdf",
                        name = "Invoice_2026.pdf",
                        sizeBytes = 1048576L,
                        lastModified = System.currentTimeMillis(),
                        isDirectory = false,
                        mimeType = "application/pdf"
                    )
                )
            )
        }

        override suspend fun uploadFile(localFile: FileItem, remoteFolderId: String): Result<String> {
            if (!isAuth) return Result.failure(IllegalStateException("Not authenticated"))
            usedStorage += localFile.sizeBytes
            return Result.success("gdrive_remote_${localFile.name}")
        }

        override suspend fun downloadFile(fileId: String, destinationPath: String): Result<Boolean> {
            if (!isAuth) return Result.failure(IllegalStateException("Not authenticated"))
            return Result.success(true)
        }

        override suspend fun getStorageQuota(): Result<Pair<Long, Long>> {
            return Result.success(Pair(usedStorage, totalStorage))
        }
    }

    @Test
    fun testAuthenticationAndQuotaFlow() = runBlocking {
        val gDriveService = FakeGoogleDriveService()

        val authResult = gDriveService.authenticate()
        assertTrue(authResult.isSuccess)
        assertTrue(authResult.getOrNull() == true)

        val quotaResult = gDriveService.getStorageQuota()
        assertTrue(quotaResult.isSuccess)
        val (used, total) = quotaResult.getOrNull()!!
        assertEquals(15_000_000_000L, total)
        assertTrue(used > 0L)
    }

    @Test
    fun testListAndUploadFiles() = runBlocking {
        val gDriveService = FakeGoogleDriveService()
        gDriveService.authenticate()

        val listResult = gDriveService.listDriveFiles("root")
        assertTrue(listResult.isSuccess)
        val files = listResult.getOrNull()!!
        assertEquals(2, files.size)
        assertEquals("VVF_Backup_2026.bak", files[0].name)

        val localFile = FileItem(
            path = "/storage/emulated/0/documents/test_upload.pdf",
            name = "test_upload.pdf",
            sizeBytes = 2048L,
            lastModified = System.currentTimeMillis(),
            isDirectory = false,
            mimeType = "application/pdf"
        )
        val uploadResult = gDriveService.uploadFile(localFile)
        assertTrue(uploadResult.isSuccess)
        assertEquals("gdrive_remote_test_upload.pdf", uploadResult.getOrNull())

        val downloadResult = gDriveService.downloadFile("remote_id", "/local/path")
        assertTrue(downloadResult.isSuccess)
    }
}
