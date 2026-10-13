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

    @Test
    fun mismatchFailsClosed() = runBlocking {
        val file = temp.newFile("a.vvfbak").also { it.writeText("payload") }
        val actual = sha256(file)
        val artifact = DownloadedArtifact(
            file = file,
            backupInfo = CloudBackupInfo("id", 1L, "a.vvfbak", file.length(), checksumSha256 = actual),
            checksumSha256 = actual
        )
        val result = Sha256BackupVerifier().verify(artifact, "0".repeat(64))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ChecksumMismatchException)
    }

    @Test
    fun matchingChecksumPasses() = runBlocking {
        val file = temp.newFile("b.vvfbak").also { it.writeText("payload") }
        val actual = sha256(file)
        val artifact = DownloadedArtifact(
            file = file,
            backupInfo = CloudBackupInfo("id", 1L, "b.vvfbak", file.length(), checksumSha256 = actual),
            checksumSha256 = actual
        )
        val result = Sha256BackupVerifier().verify(artifact, actual)
        assertTrue(result.isSuccess)
    }

    @Test
    fun missingTrustedChecksumFailsClosed() = runBlocking {
        val file = temp.newFile("missing-checksum.vvfbak").also { it.writeText("payload") }
        val actual = sha256(file)
        val artifact = DownloadedArtifact(
            file = file,
            backupInfo = CloudBackupInfo("id", 1L, file.name, file.length(), checksumSha256 = actual),
            checksumSha256 = actual
        )
        val result = Sha256BackupVerifier().verify(artifact, null)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is VerificationFailedException)
    }

    @Test
    fun bytesChangedAfterDownloadFailsClosed() = runBlocking {
        val file = temp.newFile("mutated.vvfbak").also { it.writeText("payload") }
        val recorded = sha256(file)
        file.writeText("tampered")
        val artifact = DownloadedArtifact(
            file = file,
            backupInfo = CloudBackupInfo("id", 1L, file.name, file.length(), checksumSha256 = recorded),
            checksumSha256 = recorded
        )
        val result = Sha256BackupVerifier().verify(artifact, recorded)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is VerificationFailedException)
    }
}
