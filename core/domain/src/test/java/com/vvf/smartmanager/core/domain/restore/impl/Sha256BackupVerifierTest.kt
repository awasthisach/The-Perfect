package com.vvf.smartmanager.core.domain.restore.impl

import com.vvf.smartmanager.core.domain.restore.ChecksumMismatchException
import com.vvf.smartmanager.core.domain.restore.DownloadedArtifact
import com.vvf.smartmanager.core.model.CloudBackupInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Sha256BackupVerifierTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun mismatchFailsClosed() = runBlocking {
        val file = temp.newFile("a.vvfbak").also { it.writeText("payload") }
        val artifact = DownloadedArtifact(
            file = file,
            backupInfo = CloudBackupInfo("id", 1L, "a.vvfbak", file.length(), checksumSha256 = "dead"),
            checksumSha256 = "beef"
        )
        val result = Sha256BackupVerifier().verify(artifact, "dead")
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ChecksumMismatchException)
    }

    @Test
    fun matchingChecksumPasses() = runBlocking {
        val file = temp.newFile("b.vvfbak").also { it.writeText("payload") }
        val artifact = DownloadedArtifact(
            file = file,
            backupInfo = CloudBackupInfo("id", 1L, "b.vvfbak", file.length(), checksumSha256 = "abc"),
            checksumSha256 = "abc"
        )
        val result = Sha256BackupVerifier().verify(artifact, "abc")
        assertTrue(result.isSuccess)
    }
}
