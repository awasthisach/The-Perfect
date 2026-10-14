package com.vvf.smartmanager.core.domain.restore.impl

import com.vvf.smartmanager.core.domain.restore.ChecksumMismatchException
import com.vvf.smartmanager.core.domain.restore.DownloadedArtifact
import com.vvf.smartmanager.core.domain.restore.VerificationFailedException
import com.vvf.smartmanager.core.model.CloudBackupInfo
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.MessageDigest

class Sha256BackupVerifierTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun mismatchWithValidSha256FailsClosed() = runBlocking {
        val file = temp.newFile("a.vvfbak").also { it.writeText("payload") }
        val actual = sha256(file)
        val expected = if (actual.startsWith("0")) "1" + actual.substring(1) else "0" + actual.substring(1)
        val artifact = DownloadedArtifact(
            file = file,
            backupInfo = CloudBackupInfo("id", 1L, "a.vvfbak", file.length(), checksumSha256 = actual),
            checksumSha256 = actual
        )
        val result = Sha256BackupVerifier().verify(artifact, expected)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ChecksumMismatchException)
    }

    @Test
    fun matchingChecksumPassesAfterRehashingCurrentBytes() = runBlocking {
        val file = temp.newFile("b.vvfbak").also { it.writeText("payload") }
        val actual = sha256(file)
        val artifact = DownloadedArtifact(
            file = file,
            backupInfo = CloudBackupInfo("id", 1L, "b.vvfbak", file.length(), checksumSha256 = actual),
            checksumSha256 = actual
        )
        val result = Sha256BackupVerifier().verify(artifact, actual)
        assertTrue(result.isSuccess)
        assertTrue(result.getOrThrow().checksumSha256 == actual)
    }

    @Test
    fun missingExpectedChecksumFailsClosed() = runBlocking {
        val file = temp.newFile("c.vvfbak").also { it.writeText("payload") }
        val actual = sha256(file)
        val artifact = DownloadedArtifact(
            file = file,
            backupInfo = CloudBackupInfo("id", 1L, "c.vvfbak", file.length(), checksumSha256 = actual),
            checksumSha256 = actual
        )
        val result = Sha256BackupVerifier().verify(artifact, null)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is VerificationFailedException)
    }

    @Test
    fun changedBytesAfterDownloadFailClosed() = runBlocking {
        val file = temp.newFile("d.vvfbak").also { it.writeText("payload") }
        val recorded = sha256(file)
        file.writeText("tampered")
        val artifact = DownloadedArtifact(
            file = file,
            backupInfo = CloudBackupInfo("id", 1L, "d.vvfbak", file.length(), checksumSha256 = recorded),
            checksumSha256 = recorded
        )
        val result = Sha256BackupVerifier().verify(artifact, recorded)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is VerificationFailedException)
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
