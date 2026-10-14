package com.vvf.smartmanager.core.domain.restore.impl

import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.domain.restore.BackupDownloader
import com.vvf.smartmanager.core.domain.restore.DownloadedArtifact
import com.vvf.smartmanager.core.domain.restore.VerificationFailedException
import com.vvf.smartmanager.core.model.CloudBackupInfo
import kotlinx.coroutines.CancellationException
import java.io.File
import java.security.MessageDigest

/** Downloads a Drive backup only when its trusted appProperties SHA-256 can be verified. */
class GoogleDriveBackupDownloader(
    private val driveService: GoogleDriveService,
    private val downloadDir: File
) : BackupDownloader {
    override suspend fun download(remoteBackupId: String): Result<DownloadedArtifact> {
        if (remoteBackupId.isBlank()) {
            return Result.failure(VerificationFailedException("Remote backup id is blank"))
        }
        if (!downloadDir.exists() && !downloadDir.mkdirs()) {
            return Result.failure(VerificationFailedException("Unable to create download directory"))
        }

        val expected = driveService.getFileSha256(remoteBackupId).getOrElse {
            return Result.failure(VerificationFailedException("Backup has no trusted Drive SHA-256 metadata"))
        }.trim().lowercase()
        if (!expected.matches(Regex("^[a-f0-9]{64}$"))) {
            return Result.failure(VerificationFailedException("Trusted Drive SHA-256 metadata is invalid"))
        }

        // Hash provider IDs before using them as filenames; IDs are untrusted external input.
        val dest = File(downloadDir, "backup-${sha256Text(remoteBackupId)}.vvfbak")
        if (dest.exists() && !dest.delete()) {
            return Result.failure(VerificationFailedException("Unable to clear stale backup download"))
        }
        return try {
            driveService.downloadFile(remoteBackupId, dest.absolutePath).fold(
                onSuccess = { ok ->
                    if (!ok || !dest.isFile || dest.length() == 0L) {
                        dest.delete()
                        Result.failure(VerificationFailedException("Download produced no artifact"))
                    } else {
                        val actual = sha256(dest)
                        if (!actual.equals(expected, ignoreCase = true)) {
                            dest.delete()
                            Result.failure(VerificationFailedException("Downloaded backup SHA-256 does not match trusted Drive metadata"))
                        } else {
                            Result.success(
                                DownloadedArtifact(
                                    file = dest,
                                    backupInfo = CloudBackupInfo(
                                        backupId = remoteBackupId,
                                        timestamp = dest.lastModified(),
                                        backupName = dest.name,
                                        backupSizeBytes = dest.length(),
                                        checksumSha256 = expected
                                    ),
                                    checksumSha256 = actual
                                )
                            )
                        }
                    }
                },
                onFailure = { error ->
                    dest.delete()
                    Result.failure(error)
                }
            )
        } catch (error: Throwable) {
            dest.delete()
            if (error is CancellationException) throw error
            throw error
        }
    }

    private fun sha256Text(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8))
        .joinToString("") { "%02x".format(it) }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                digest.update(buffer, 0, n)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}
