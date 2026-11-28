package com.vvf.smartmanager.core.domain.restore.impl

import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.domain.restore.BackupDownloader
import com.vvf.smartmanager.core.domain.restore.DownloadedArtifact
import com.vvf.smartmanager.core.domain.restore.VerificationFailedException
import com.vvf.smartmanager.core.model.CloudBackupInfo
import java.io.File
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Downloads a Drive backup into a private temporary file, verifies its local digest,
 * and only then publishes the completed artifact under the restore working directory.
 */
class GoogleDriveBackupDownloader(
    private val driveService: GoogleDriveService,
    private val downloadDir: File
) : BackupDownloader {
    override suspend fun download(remoteBackupId: String): Result<DownloadedArtifact> =
        withContext(Dispatchers.IO) {
            if (!isSafeDriveFileId(remoteBackupId)) {
                return@withContext Result.failure(
                    VerificationFailedException("Remote backup id contains unsafe path characters")
                )
            }
            if (!downloadDir.exists() && !downloadDir.mkdirs()) {
                return@withContext Result.failure(
                    VerificationFailedException("Unable to create download directory")
                )
            }

            try {
                val root = downloadDir.canonicalFile
                val destination = File(root, "backup-${UUID.randomUUID()}.vvfbak").canonicalFile
                if (destination.parentFile != root) {
                    return@withContext Result.failure(
                        VerificationFailedException("Backup destination escaped the private download directory")
                    )
                }
                val staged = File(root, ".backup-${UUID.randomUUID()}.part")
                try {
                    val downloaded = driveService.downloadFile(remoteBackupId, staged.absolutePath)
                    val ok = downloaded.getOrElse { error ->
                        if (error is CancellationException) throw error
                        return@withContext Result.failure(error)
                    }
                    if (!ok || !staged.isFile || staged.length() == 0L) {
                        return@withContext Result.failure(
                            VerificationFailedException("Download produced no complete artifact")
                        )
                    }

                    val checksum = sha256(staged)
                    if (!staged.renameTo(destination)) {
                        staged.copyTo(destination, overwrite = false)
                        if (!staged.delete()) {
                            return@withContext Result.failure(
                                VerificationFailedException("Unable to remove temporary backup after publishing")
                            )
                        }
                    }

                    Result.success(
                        DownloadedArtifact(
                            file = destination,
                            backupInfo = CloudBackupInfo(
                                backupId = remoteBackupId,
                                timestamp = destination.lastModified(),
                                backupName = destination.name,
                                backupSizeBytes = destination.length(),
                                checksumSha256 = checksum
                            ),
                            checksumSha256 = checksum
                        )
                    )
                } finally {
                    if (staged.exists()) staged.delete()
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Result.failure(error)
            }
        }

    private fun isSafeDriveFileId(value: String): Boolean =
        value.length in 1..256 && value.all { character ->
            character in 'a'..'z' ||
                character in 'A'..'Z' ||
                character in '0'..'9' ||
                character == '-' ||
                character == '_'
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
