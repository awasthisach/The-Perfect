package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.database.dao.CloudSyncDao
import com.vvf.smartmanager.core.database.model.CloudSyncEntity
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
import java.io.File
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CloudSyncUseCaseTest {

    @get:Rule
    val temp = TemporaryFolder()

    private class FakeDriveService(
        private val uploadResult: Result<String> = Result.success("drive-file-id"),
        private val checksumResult: Result<String> = Result.failure(UnsupportedOperationException("no checksum"))
    ) : GoogleDriveService {
        override suspend fun authenticate(): Result<Boolean> = Result.success(true)
        override suspend fun listDriveFiles(folderId: String): Result<List<FileItem>> = Result.success(emptyList())
        override suspend fun uploadFile(fileItem: FileItem, folderName: String): Result<String> = uploadResult
        override suspend fun downloadFile(fileId: String, destinationPath: String): Result<Boolean> = Result.success(true)
        override suspend fun getStorageQuota(): Result<Pair<Long, Long>> = Result.success(0L to 1L)
        override suspend fun listDriveBackups(): Result<List<CloudBackupInfo>> = Result.success(emptyList())
        override suspend fun getFileSha256(fileId: String): Result<String> = checksumResult
        override fun setAccessToken(token: String?) {}
    }

    private class FakeCloudSyncDao : CloudSyncDao {
        private val records = mutableListOf<CloudSyncEntity>()
        override suspend fun getRecord(localPath: String, provider: String): CloudSyncEntity? =
            records.find { it.localPath == localPath && it.provider == provider }
        override suspend fun getRecordByRemoteId(remoteFileId: String, provider: String): CloudSyncEntity? =
            records.find { it.remoteFileId == remoteFileId && it.provider == provider }
        override suspend fun insertOrUpdate(entity: CloudSyncEntity): Long {
            val existing = records.indexOfFirst { it.localPath == entity.localPath && it.provider == entity.provider }
            if (existing >= 0) {
                records[existing] = entity.copy(id = records[existing].id)
                return records[existing].id
            }
            val id = (records.maxOfOrNull { it.id } ?: 0L) + 1L
            records.add(entity.copy(id = id))
            return id
        }
        override fun getRecordsByProvider(provider: String): Flow<List<CloudSyncEntity>> =
            flowOf(records.filter { it.provider == provider }.sortedByDescending { it.lastSyncedAt })

        override suspend fun getRecordsByStatus(status: String): List<CloudSyncEntity> =
            records.filter { it.status == status }

        override suspend fun deleteByLocalPath(localPath: String) {
            records.removeAll { it.localPath == localPath }
        }
    }

    private val sampleFile = FileItem(
        path = "/tmp/sample.pdf",
        name = "sample.pdf",
        sizeBytes = 12L,
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
        val useCase = CloudSyncUseCase(
            FakeDriveService(Result.success("remote-123")),
            cloudSyncDao = FakeCloudSyncDao()
        )
        val result = useCase.syncFileToCloud(sampleFile)
        assertTrue(result.isSuccess)
        assertEquals("remote-123", result.getOrThrow().remotePath)
        assertEquals(CloudSyncStatus.SUCCESS, useCase.syncState.value)
    }

    @Test
    fun restoreUsesInjectedFailClosedPipeline() = runBlocking {
        val trustedChecksum = "a".repeat(64)
        val info = CloudBackupInfo("remote-1", 1L, "backup.vvfbak", 10L, checksumSha256 = trustedChecksum)
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
        val useCase = CloudSyncUseCase(
            FakeDriveService(checksumResult = Result.success(trustedChecksum)),
            restorePipeline = pipeline
        )
        val result = useCase.restoreCloudBackup(info)
        assertTrue(result.isSuccess)
        val restoreResult = result.getOrThrow()
        assertTrue(restoreResult.success)
        assertFalse(restoreResult.snapshotToken.isNullOrBlank())
        assertEquals(CloudSyncStatus.SUCCESS, useCase.syncState.value)
    }
}
