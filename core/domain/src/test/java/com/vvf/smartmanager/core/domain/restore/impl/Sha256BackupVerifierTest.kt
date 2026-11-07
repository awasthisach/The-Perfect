package com.vvf.smartmanager.core.domain.restore.impl

import com.vvf.smartmanager.core.domain.restore.ChecksumMismatchException
import com.vvf.smartmanager.core.domain.restore.DownloadedArtifact
import com.vvf.smartmanager.core.domain.restore.VerificationFailedException
import com.vvf.smartmanager.core.model.CloudBackupInfo
import java.io.File
import java.security.MessageDigest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class Sha256BackupVerifierTest {
    @get:Rule
    val temp = TemporaryFolder()

    private fun digest(text: String): String = MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray()).joinToString("") { (it.toInt() and 0xff).toString(16).padStart(2, '0') }

    private fun artifact(file: File, cached: String?) = DownloadedArtifact(
        file, CloudBackupInfo("id", 1L, file.name, file.length(), checksumSha256 = cached), cached
    )

    @Test
    fun mismatchFailsClosed() = runBlocking {
        val file = temp.newFile("a.vvfbak").also { it.writeText("payload") }
        val result = Sha256BackupVerifier().verify(artifact(file, digest("payload")), digest("different"))
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ChecksumMismatchException)
    }

    @Test
    fun matchingChecksumPasses() = runBlocking {
        val file = temp.newFile("b.vvfbak").also { it.writeText("payload") }
        val expected = digest("payload")
        val result = Sha256BackupVerifier().verify(artifact(file, expected), expected.uppercase())
        assertTrue(result.isSuccess)
        assertEquals(expected, result.getOrThrow().checksumSha256)
    }

    @Test
    fun missingExpectedChecksumFailsClosed() = runBlocking {
        val file = temp.newFile("c.vvfbak").also { it.writeText("payload") }
        val result = Sha256BackupVerifier().verify(artifact(file, digest("payload")), null)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is VerificationFailedException)
    }

    @Test
    fun modifiedBytesCannotPassUsingCachedDigest() = runBlocking {
        val file = temp.newFile("tampered.vvfbak").also { it.writeText("original") }
        val expected = digest("original")
        val downloaded = artifact(file, expected)
        file.writeText("modified after download")
        val result = Sha256BackupVerifier().verify(downloaded, expected)
        assertTrue(result.isFailure)
        assertTrue(result.exceptionOrNull() is ChecksumMismatchException)
    }

    @Test
    fun forgedCachedDigestCannotReplaceActualByteVerification() = runBlocking {
        val file = temp.newFile("forged.vvfbak").also { it.writeText("untrusted bytes") }
        val expected = digest("trusted bytes")
        assertTrue(Sha256BackupVerifier().verify(artifact(file, expected), expected).isFailure)
    }

    @Test
    fun malformedExpectedDigestFailsClosed() = runBlocking {
        val file = temp.newFile("malformed.vvfbak").also { it.writeText("payload") }
        val result = Sha256BackupVerifier().verify(artifact(file, "abc"), "abc")
        assertTrue(result.exceptionOrNull() is VerificationFailedException)
    }
}
