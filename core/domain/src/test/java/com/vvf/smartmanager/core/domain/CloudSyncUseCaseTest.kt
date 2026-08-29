package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.model.CloudProviderType
import com.vvf.smartmanager.core.model.FileItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudSyncUseCaseTest {
    private class FakeDriveService(
        private val uploadResult: Result<String> = Result.success("drive-file-id")
    ) : GoogleDriveService {
        override suspend fun authenticate(): Result<Boolean> = Result.success(true)
        override suspend fun listDriveFiles(folderId: String): Result<List<FileItem>> = Result.success(emptyList())
        override suspend fun uploadFile(localFile: FileItem, remoteFolderId: String): Result<String> = uploadResult
        override suspend fun downloadFile(fileId: String, destinationPath: String): Result<Boolean> = Result.success(true)
        override suspend fun getStorageQuota(): Result<Pair<Long, Long>> = Result.success(0L to 1L)
    }

    private val sampleFile = FileItem(
        path = "/tmp/report.pdf",
        name = "report.pdf",
        sizeBytes = 10L,
        lastModified = 1L,
        isDirectory = false,
        mimeType = "application/pdf"
    )

    @Test
    fun backupAndRestoreFailClosedUntilArchivePipelineExists() = runBlocking {
        val useCase = CloudSyncUseCase(FakeDriveService())

        val backup = useCase.createCloudBackup(CloudProviderType.GOOGLE_DRIVE)
        val restore = useCase.restoreCloudBackup(
            com.vvf.smartmanager.core.model.CloudBackupInfo("id", 1L, "backup.vvfbak", 1L)
        )

        assertTrue(backup.isFailure)
        assertTrue(restore.isFailure)
        assertEquals(com.vvf.smartmanager.core.model.CloudSyncStatus.ERROR, useCase.syncState.value)
    }

    @Test
    fun fileSyncUsesDriveUploadResult() = runBlocking {
        val useCase = CloudSyncUseCase(FakeDriveService(Result.success("remote-123")))

        val result = useCase.syncFileToCloud(sampleFile)

        assertTrue(result.isSuccess)
        assertEquals("remote-123", result.getOrThrow().remotePath)
        assertEquals(com.vvf.smartmanager.core.model.CloudSyncStatus.SUCCESS, useCase.syncState.value)
    }

    @Test
    fun failedDriveUploadIsReportedAndQueuedAsError() = runBlocking {
        val useCase = CloudSyncUseCase(FakeDriveService(Result.failure(IllegalStateException("unauthorized"))))

        val result = useCase.syncFileToCloud(sampleFile)

        assertFalse(result.isSuccess)
        assertEquals(com.vvf.smartmanager.core.model.CloudSyncStatus.ERROR, useCase.syncState.value)
        assertEquals(com.vvf.smartmanager.core.model.CloudSyncStatus.ERROR, useCase.syncQueue.value.first().status)
        assertEquals("unauthorized", useCase.syncQueue.value.first().errorMessage)
    }
}
