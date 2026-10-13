package com.vvf.smartmanager.core.domain.restore.impl

import com.vvf.smartmanager.core.domain.restore.BackupVerifier
import com.vvf.smartmanager.core.domain.restore.ChecksumMismatchException
import com.vvf.smartmanager.core.domain.restore.DownloadedArtifact
import com.vvf.smartmanager.core.domain.restore.VerificationFailedException
import com.vvf.smartmanager.core.domain.restore.VerifiedArtifact
import java.security.MessageDigest

/** Fail-closed integrity gate: expected checksum must match the downloaded artifact digest. */
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

        // Recompute from the current bytes; never trust a digest cached by the downloader.
        val actual = sha256(artifact.file)
        val downloadedDigest = artifact.checksumSha256?.trim()?.lowercase()
            ?: return Result.failure(VerificationFailedException("Downloaded artifact has no checksum"))
        if (!downloadedDigest.matches(Regex("^[a-f0-9]{64}$")) || !downloadedDigest.equals(actual, ignoreCase = true)) {
            return Result.failure(VerificationFailedException("Downloaded artifact changed after its checksum was recorded"))
        }
        if (!expected.equals(actual, ignoreCase = true)) {
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

    private fun sha256(file: java.io.File): String {
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
