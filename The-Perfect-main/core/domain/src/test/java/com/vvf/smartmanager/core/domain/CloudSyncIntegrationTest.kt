package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.domain.backup.ArchiveArtifact
import com.vvf.smartmanager.core.domain.backup.ArchiveService
import com.vvf.smartmanager.core.model.CloudBackupInfo
import com.vvf.smartmanager.core.model.CloudProviderType
import com.vvf.smartmanager.core.model.CloudSyncStatus
import com.vvf.smartmanager.core.model.FileItem
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.nio.file.Files
import java.util.concurrent.TimeUnit

class CloudSyncIntegrationTest {
    private lateinit var server: MockWebServer
    private lateinit var tempDir: File

    @Before
    fun setUp() {
        server = MockWebServer().also { it.start() }
        tempDir = Files.createTempDirectory("cloud-sync-integration").toFile()
    }

    @After
    fun tearDown() {
        server.shutdown()
        tempDir.deleteRecursively()
    }

    @Test
    fun backupUploadsFileItemToFakeProviderAndReturnsRemoteId() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("remote-http-id"))
        val drive = HttpFakeDriveService(server)
        val archive = mock<ArchiveService>()
        val artifactFile = File(tempDir, "backup.vvfbak").apply { writeText("encrypted-content") }
        whenever(archive.createArchive(includeVault = false, includeDatabase = true)).thenReturn(
            Result.success(artifact(artifactFile))
        )
        val useCase = CloudSyncUseCase(drive, archiveService = archive)

        val result = useCase.createCloudBackup(CloudProviderType.GOOGLE_DRIVE)
        val request = server.takeRequest(2, TimeUnit.SECONDS)

        assertTrue(result.isSuccess)
        assertEquals("remote-http-id", result.getOrThrow().backupId)
        assertEquals("/upload", request?.path)
        assertTrue(request?.body?.readUtf8()?.contains("encrypted-content") == true)
        assertEquals("VVF_Backups", drive.lastRemoteFolder)
        assertEquals(CloudSyncStatus.SUCCESS, useCase.syncState.value)
    }

    @Test
    fun fakeProviderRejectionBecomesFailureAndDoesNotReportSuccess() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(503).setBody("unavailable"))
        val drive = HttpFakeDriveService(server)
        val archive = mock<ArchiveService>()
        val artifactFile = File(tempDir, "backup.vvfbak").apply { writeText("encrypted-content") }
        whenever(archive.createArchive(includeVault = false, includeDatabase = true)).thenReturn(
            Result.success(artifact(artifactFile))
        )
        val useCase = CloudSyncUseCase(drive, archiveService = archive)

        val result = useCase.createCloudBackup(CloudProviderType.GOOGLE_DRIVE)

        assertTrue(result.isFailure)
        assertEquals("Upload rejected with HTTP 503", result.exceptionOrNull()?.message)
        assertEquals(CloudSyncStatus.ERROR, useCase.syncState.value)
    }

    @Test
    fun missingArchiveServiceFailsClosedWithoutProviderRequest() = runBlocking {
        val drive = HttpFakeDriveService(server)
        val useCase = CloudSyncUseCase(drive)

        val result = useCase.createCloudBackup(CloudProviderType.GOOGLE_DRIVE)

        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is UnsupportedOperationException)
        assertEquals(0, drive.uploadCalls)
        assertEquals(CloudSyncStatus.ERROR, useCase.syncState.value)
    }

    @Test
    fun archiveFailurePreventsProviderRequest() = runBlocking {
        val drive = HttpFakeDriveService(server)
        val archive = mock<ArchiveService>()
        whenever(archive.createArchive(includeVault = false, includeDatabase = true)).thenReturn(
            Result.failure(IllegalStateException("Snapshot failed"))
        )
        val useCase = CloudSyncUseCase(drive, archiveService = archive)

        val result = useCase.createCloudBackup(CloudProviderType.GOOGLE_DRIVE)

        assertTrue(result.isFailure)
        assertEquals("Snapshot failed", result.exceptionOrNull()?.message)
        assertEquals(0, drive.uploadCalls)
    }

    @Test
    fun fileItemUploadContractContainsRequiredFields() = runBlocking {
        server.enqueue(MockResponse().setResponseCode(200).setBody("remote-id"))
        val drive = HttpFakeDriveService(server)
        val archive = mock<ArchiveService>()
        val artifactFile = File(tempDir, "backup.vvfbak").apply { writeText("encrypted") }
        whenever(archive.createArchive(includeVault = false, includeDatabase = true)).thenReturn(
            Result.success(artifact(artifactFile))
        )
        val useCase = CloudSyncUseCase(drive, archiveService = archive)

        useCase.createCloudBackup(CloudProviderType.GOOGLE_DRIVE)

        val item = drive.lastUploadedItem
        assertEquals("backup.vvfbak", item?.name)
        assertEquals(artifactFile.length(), item?.sizeBytes)
        assertFalse(item?.isDirectory ?: true)
        assertEquals("application/octet-stream", item?.mimeType)
        assertTrue((item?.path ?: "").isNotEmpty())
    }

    private fun artifact(file: File) = ArchiveArtifact(
        file = file,
        backupInfo = CloudBackupInfo(
            backupId = "local-id",
            timestamp = System.currentTimeMillis(),
            backupName = "Test Backup",
            backupSizeBytes = file.length(),
            includesVault = false,
            includesDatabase = true,
            includesPreferences = false
        ),
        checksumSha256 = "checksum"
    )

    private class HttpFakeDriveService(
        private val server: MockWebServer
    ) : GoogleDriveService {
        private val client = OkHttpClient()
        var uploadCalls: Int = 0
            private set
        var lastRemoteFolder: String? = null
            private set
        var lastUploadedItem: FileItem? = null
            private set

        override suspend fun authenticate() = Result.success(true)
        override suspend fun listDriveFiles(folderId: String) = Result.success(emptyList<FileItem>())
        override suspend fun uploadFile(localFile: FileItem, remoteFolderId: String): Result<String> {
            uploadCalls++
            lastUploadedItem = localFile
            lastRemoteFolder = remoteFolderId
            val request = Request.Builder()
                .url(server.url("upload"))
                .post(localFile.asFile().asRequestBody("application/octet-stream".toMediaType()))
                .build()
            client.newCall(request).execute().use { response ->
                return if (response.isSuccessful) {
                    Result.success(response.body?.string().orEmpty())
                } else {
                    Result.failure(IllegalStateException("Upload rejected with HTTP ${response.code}"))
                }
            }
        }
        override suspend fun downloadFile(fileId: String, destinationPath: String) = Result.success(true)
        override suspend fun getStorageQuota() = Result.success(0L to 1L)

        private fun FileItem.asFile(): File = File(path)
    }
}
