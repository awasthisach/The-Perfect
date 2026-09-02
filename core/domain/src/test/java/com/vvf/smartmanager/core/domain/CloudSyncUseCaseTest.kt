package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.domain.restore.AppliedRestore
import com.vvf.smartmanager.core.domain.restore.BackupDecryptor
import com.vvf.smartmanager.core.domain.restore.BackupDownloader
import com.vvf.smartmanager.core.domain.restore.BackupVerifier
import com.vvf.smartmanager.core.domain.restore.DecryptedBackup
import com.vvf.smartmanager.core.domain.restore.DownloadedArtifact
import com.vvf.smartmanager.core.domain.restore.FailClosedRestorePipeline
import com.vvf.smartmanager.core.domain.restore.RestoreApplier
import com.vvf.smartmanager.core.domain.restore.RestoreSnapshot
import com.vvf.smartmanager.core.domain.restore.VerifiedArtifact
import com.vvf.smartmanager.core.model.CloudBackupInfo
import com.vvf.smartmanager.core.model.CloudProviderType
import com.vvf.smartmanager.core.model.CloudSyncStatus
import com.vvf.smartmanager.core.model.FileItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class CloudSyncUseCaseTest {
    @get:Rule
    val temp = TemporaryFolder()

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
    fun backupAndRestoreFailClosedUntilPipelinesConfigured() = runBlocking {
        val useCase = CloudSyncUseCase(FakeDriveService())

        val backup = useCase.createCloudBackup(CloudProviderType.GOOGLE_DRIVE)
        val restore = useCase.restoreCloudBackup(
            CloudBackupInfo("id", 1L, "backup.vvfbak", 1L)
        )

        assertTrue(backup.isFailure)
        assertTrue(restore.isFailure)
        assertEquals(CloudSyncStatus.ERROR, useCase.syncState.value)
    }

    @Test
    fun fileSyncUsesDriveUploadResult() = runBlocking {
        val useCase = CloudSyncUseCase(FakeDriveService(Result.success("remote-123")))

        val result = useCase.syncFileToCloud(sampleFile)

        assertTrue(result.isSuccess)
        assertEquals("remote-123", result.getOrThrow().remotePath)
        assertEquals(CloudSyncStatus.SUCCESS, useCase.syncState.value)
    }

    @Test
    fun blankRemoteIdIsRejectedAsNonDurable() = runBlocking {
        val useCase = CloudSyncUseCase(FakeDriveService(Result.success("   ")))

        val result = useCase.syncFileToCloud(sampleFile)

        assertFalse(result.isSuccess)
        assertEquals(CloudSyncStatus.ERROR, useCase.syncState.value)
        assertTrue(
            result.exceptionOrNull()?.message?.contains("blank remote identifier") == true ||
                result.exceptionOrNull() is IllegalArgumentException
        )
    }

    @Test
    fun failedDriveUploadIsReportedAndQueuedAsError() = runBlocking {
        val useCase = CloudSyncUseCase(FakeDriveService(Result.failure(IllegalStateException("unauthorized"))))

        val result = useCase.syncFileToCloud(sampleFile)

        assertFalse(result.isSuccess)
        assertEquals(CloudSyncStatus.ERROR, useCase.syncState.value)
        assertEquals(CloudSyncStatus.ERROR, useCase.syncQueue.value.first().status)
        assertEquals("unauthorized", useCase.syncQueue.value.first().errorMessage)
    }

    @Test
    fun restoreUsesInjectedFailClosedPipeline() = runBlocking {
        val info = CloudBackupInfo("remote-1", 1L, "backup.vvfbak", 10L, checksumSha256 = "abc")
        val pipeline = FailClosedRestorePipeline(
            workingDir = temp.newFolder("restore-work"),
            downloader = object : BackupDownloader {
                override suspend fun download(remoteBackupId: String) = Result.success(
                    DownloadedArtifact(temp.newFile("art"), info, "abc")
                )
            },
            verifier = object : BackupVerifier {
                override suspend fun verify(artifact: DownloadedArtifact, expectedChecksum: String?) =
                    Result.success(VerifiedArtifact(artifact.file, info, "abc"))
            },
            decryptor = object : BackupDecryptor {
                override suspend fun decrypt(verifiedArtifact: VerifiedArtifact, stagingDir: File) =
                    Result.success(
                        DecryptedBackup(stagingDir, temp.newFile("db"), temp.newFolder("vault"), info, 1L)
                    )
            },
            applier = object : RestoreApplier {
                override suspend fun prepare() = Result.success(
                    RestoreSnapshot("s1", 1L, temp.newFile("sdb"), temp.newFolder("sv"))
                )
                override suspend fun apply(decryptedBackup: DecryptedBackup) =
                    Result.success(AppliedRestore(true, 2, 1, "restored"))
                override suspend fun rollback(snapshot: RestoreSnapshot) = Result.success(Unit)
            }
        )
        val useCase = CloudSyncUseCase(FakeDriveService(), restorePipeline = pipeline)

        val result = useCase.restoreCloudBackup(info)

        assertTrue(result.isSuccess)
        assertEquals(CloudSyncStatus.SUCCESS, useCase.syncState.value)
    }
}
