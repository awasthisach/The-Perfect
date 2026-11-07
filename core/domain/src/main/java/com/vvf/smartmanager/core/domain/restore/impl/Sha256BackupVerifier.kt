package com.vvf.smartmanager.core.domain.restore.impl

import com.vvf.smartmanager.core.domain.restore.BackupVerifier
import com.vvf.smartmanager.core.domain.restore.ChecksumMismatchException
import com.vvf.smartmanager.core.domain.restore.DownloadedArtifact
import com.vvf.smartmanager.core.domain.restore.VerificationFailedException
import com.vvf.smartmanager.core.domain.restore.VerifiedArtifact
import java.security.MessageDigest
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

/** Verify actual private artifact bytes against a separately trusted expected SHA-256. */
class Sha256BackupVerifier : BackupVerifier {
    override suspend fun verify(
        artifact: DownloadedArtifact,
        expectedChecksum: String?
    ): Result<VerifiedArtifact> = withContext(Dispatchers.IO) {
        if (expectedChecksum.isNullOrBlank() || !expectedChecksum.matches(Regex("[0-9a-fA-F]{64}"))) {
            return@withContext Result.failure(VerificationFailedException("Trusted expected SHA-256 is required"))
        }
        if (!artifact.file.isFile || artifact.file.length() == 0L) {
            return@withContext Result.failure(VerificationFailedException("Artifact missing or empty"))
        }
        try {
            val digest = MessageDigest.getInstance("SHA-256")
            val context = currentCoroutineContext()
            artifact.file.inputStream().use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    context.ensureActive()
                    val count = input.read(buffer)
                    if (count < 0) break
                    digest.update(buffer, 0, count)
                }
            }
            val actual = digest.digest().joinToString("") {
                (it.toInt() and 0xff).toString(16).padStart(2, '0')
            }
            // Cached download metadata is not proof that the bytes remain unchanged.
            if (!expectedChecksum.equals(actual, ignoreCase = true)) {
                Result.failure(ChecksumMismatchException(expectedChecksum, actual))
            } else {
                Result.success(VerifiedArtifact(artifact.file, artifact.backupInfo, actual))
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(VerificationFailedException("Unable to read artifact for checksum verification"))
        }
    }
}
