package com.vvf.smartmanager.core.domain.restore.impl

import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.model.FileItem
import java.io.File
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class GoogleDriveBackupDownloaderTest {
    @get:Rule
    val temp = TemporaryFolder()

    private class FakeDriveService(
        private val download: suspend (String, String) -> Result<Boolean>
    ) : GoogleDriveService {
        override suspend fun authenticate(): Result<Boolean> = Result.success(true)
        override suspend fun listDriveFiles(folderId: String): Result<List<FileItem>> =
            Result.success(emptyList())
        override suspend fun uploadFile(localFile: FileItem, remoteFolderId: String): Result<String> =
            Result.failure(UnsupportedOperationException("unused"))
        override suspend fun downloadFile(fileId: String, destinationPath: String): Result<Boolean> =
            download(fileId, destinationPath)
        override suspend fun disconnect(): Result<Unit> = Result.success(Unit)
        override suspend fun getStorageQuota(): Result<Pair<Long, Long>> = Result.success(0L to 0L)
    }

    @Test
    fun rejectsPathTraversalBeforeCallingDrive() = runBlocking {
        var called = false
        val directory = temp.newFolder("downloads")
        val downloader = GoogleDriveBackupDownloader(
            FakeDriveService { _, _ ->
                called = true
                Result.success(true)
            },
            directory
        )

        val result = downloader.download("../outside")

        assertTrue(result.isFailure)
        assertFalse(called)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
        assertFalse(File(temp.root, "outside.vvfbak").exists())
    }

    @Test
    fun publishesOnlyACompleteDownloadedArtifact() = runBlocking {
        val directory = temp.newFolder("downloads-success")
        val downloader = GoogleDriveBackupDownloader(
            FakeDriveService { fileId, destinationPath ->
                assertEquals("safe-id_123", fileId)
                File(destinationPath).writeText("encrypted backup bytes")
                Result.success(true)
            },
            directory
        )

        val result = downloader.download("safe-id_123")

        assertTrue(result.isSuccess)
        val artifact = result.getOrThrow()
        assertEquals("encrypted backup bytes", artifact.file.readText())
        assertFalse(artifact.checksumSha256.isNullOrBlank())
        assertEquals(listOf(artifact.file), directory.listFiles().orEmpty().toList())
    }

    @Test
    fun failedDownloadRemovesPartialTemporaryArtifact() = runBlocking {
        val directory = temp.newFolder("downloads-failure")
        val downloader = GoogleDriveBackupDownloader(
            FakeDriveService { _, destinationPath ->
                File(destinationPath).writeText("partial")
                Result.failure(IOException("network interrupted"))
            },
            directory
        )

        val result = downloader.download("safe-id_123")

        assertTrue(result.isFailure)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun cancellationPropagatesAndRemovesPartialTemporaryArtifact() = runBlocking {
        val directory = temp.newFolder("downloads-cancel")
        val cancellation = CancellationException("download cancelled")
        val downloader = GoogleDriveBackupDownloader(
            FakeDriveService { _, destinationPath ->
                File(destinationPath).writeText("partial")
                throw cancellation
            },
            directory
        )

        val thrown = try {
            downloader.download("safe-id_123")
            null
        } catch (actual: CancellationException) {
            actual
        }

        // Coroutine stacktrace recovery may copy an exception, retaining its cause.
        assertTrue(
            "Cancellation must escape with the original cause retained",
            generateSequence<Throwable>(thrown) { it.cause }.take(8).any { it === cancellation }
        )
        assertEquals(cancellation.message, thrown?.message)
        assertTrue(directory.listFiles().orEmpty().isEmpty())
    }
}
