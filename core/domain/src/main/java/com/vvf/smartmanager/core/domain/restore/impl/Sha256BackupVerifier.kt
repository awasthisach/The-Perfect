package com.vvf.smartmanager.core.domain.restore.impl

import com.vvf.smartmanager.core.domain.restore.BackupVerifier
import com.vvf.smartmanager.core.domain.restore.ChecksumMismatchException
import com.vvf.smartmanager.core.domain.restore.DownloadedArtifact
import com.vvf.smartmanager.core.domain.restore.VerificationFailedException
import com.vvf.smartmanager.core.domain.restore.VerifiedArtifact

/** Fail-closed integrity gate: expected checksum must match the downloaded artifact digest. */
class Sha256BackupVerifier : BackupVerifier {
    override suspend fun verify(
        artifact: DownloadedArtifact,
        expectedChecksum: String?
    ): Result<VerifiedArtifact> {
        if (!artifact.file.isFile || artifact.file.length() == 0L) {
            return Result.failure(VerificationFailedException("Artifact missing or empty"))
        }
        val actual = artifact.checksumSha256
            ?: return Result.failure(VerificationFailedException("Downloaded artifact has no checksum"))
        if (expectedChecksum != null && expectedChecksum.isNotBlank() &&
            !expectedChecksum.equals(actual, ignoreCase = true)
        ) {
            return Result.failure(ChecksumMismatchException(expectedChecksum, actual))
        }
        return Result.success(
            VerifiedArtifact(
                file = artifact.file,
                backupInfo = artifact.backupInfo,
                checksumSha256 = actual
            )
        )
    }
}
