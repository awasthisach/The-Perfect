package com.vvf.smartmanager.core.domain.restore.impl

import com.vvf.smartmanager.core.domain.restore.BackupVerifier
import com.vvf.smartmanager.core.domain.restore.ChecksumMismatchException
import com.vvf.smartmanager.core.domain.restore.DownloadedArtifact
import com.vvf.smartmanager.core.domain.restore.VerificationFailedException
import com.vvf.smartmanager.core.domain.restore.VerifiedArtifact
import java.io.File
import java.security.MessageDigest

/** Fail-closed integrity gate: a trusted SHA-256 must match the bytes being restored. */
class Sha256BackupVerifier : BackupVerifier {
    override suspend fun verify(
        artifact: DownloadedArtifact,
        expectedChecksum: String?
    ): Result<VerifiedArtifact> {
        if (!artifact.file.isFile || artifact.file.length() == 0L) {
            return Result.failure(VerificationFailedException("Artifact missing or empty"))
        }
        val expected = expectedChecksum?.trim()?.lowercase()
            ?: return Result.failure(VerificationFailedException("Trusted expected SHA-256 checksum is required"))
        if (!expected.matches(Regex("^[a-f0-9]{64}$"))) {
            return Result.failure(VerificationFailedException("Expected checksum must be a 64-character SHA-256 hex digest"))
        }

        // Recompute from current bytes; never trust a checksum cached by the downloader.
        val actual = sha256(artifact.file)
        val downloaded = artifact.checksumSha256?.trim()?.lowercase()
            ?: return Result.failure(VerificationFailedException("Downloaded artifact has no checksum"))
        if (!downloaded.matches(Regex("^[a-f0-9]{64}$")) || downloaded != actual) {
            return Result.failure(VerificationFailedException("Downloaded artifact changed after its checksum was recorded"))
        }
        if (expected != actual) {
            return Result.failure(ChecksumMismatchException(expected, actual))
        }
        return Result.success(
            VerifiedArtifact(
                file = artifact.file,
                backupInfo = artifact.backupInfo,
                checksumSha256 = actual
            )
        )
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
