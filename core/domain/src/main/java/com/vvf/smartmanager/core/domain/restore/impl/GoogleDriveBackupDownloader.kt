package com.vvf.smartmanager.core.domain.restore.impl

import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.domain.restore.BackupDownloader
import com.vvf.smartmanager.core.domain.restore.DownloadedArtifact
import com.vvf.smartmanager.core.domain.restore.VerificationFailedException
import com.vvf.smartmanager.core.model.CloudBackupInfo
import java.io.File
import java.security.MessageDigest

/** Downloads a provider backup artifact; computes local SHA-256 for independent verification. */
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
        val dest = File(downloadDir, "backup-$remoteBackupId.vvfbak")
        return driveService.downloadFile(remoteBackupId, dest.absolutePath).fold(
            onSuccess = { ok ->
                if (!ok || !dest.isFile || dest.length() == 0L) {
                    Result.failure(VerificationFailedException("Download produced no artifact"))
                } else {
                    val checksum = sha256(dest)
                    Result.success(
                        DownloadedArtifact(
                            file = dest,
                            backupInfo = CloudBackupInfo(
                                backupId = remoteBackupId,
                                timestamp = dest.lastModified(),
                                backupName = dest.name,
                                backupSizeBytes = dest.length(),
                                checksumSha256 = checksum
                            ),
                            checksumSha256 = checksum
                        )
                    )
                }
            },
            onFailure = { Result.failure(it) }
        )
    }

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
