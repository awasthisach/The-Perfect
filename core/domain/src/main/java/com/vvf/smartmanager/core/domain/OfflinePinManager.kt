package com.vvf.smartmanager.core.domain

import android.content.Context
import com.vvf.smartmanager.core.cloud.gdrive.DriveIdValidator
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.database.dao.DriveIndexDao
import com.vvf.smartmanager.core.database.model.DriveIndexFileEntity
import java.io.File
import java.io.FileInputStream
import kotlinx.coroutines.CancellationException
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/**
 * Keeps pinned bytes only under filesDir/pinned, enforces a 200 MiB / 80-file budget, and evicts
 * the oldest pins. No Drive mutation is performed by pin/unpin.
 */
class OfflinePinManager(
    private val context: Context,
    private val driveService: GoogleDriveService,
    private val driveIndexDao: DriveIndexDao
) {
    suspend fun pin(fileId: String): Result<DriveIndexFileEntity> {
        var stagingFile: File? = null
        return try {
            require(DriveIdValidator.isValidFileId(fileId)) { "Invalid Drive file id." }
            val record = driveIndexDao.getByDriveId(fileId)
                ?: throw IllegalStateException("File is not in the local Drive index.")
            require(record.indexStatus != "REMOTE_REMOVED") { "This file is no longer available in Drive." }
            require(record.mimeType != "application/vnd.google-apps.folder") { "Folders cannot be pinned as files." }
            if (record.pinnedPath != null && record.pinnedModifiedTimeMs == record.modifiedTimeMs &&
                safePinnedFile(record.pinnedPath)?.isFile == true
            ) {
                return Result.success(record)
            }
            require(record.sizeBytes <= MAX_PINNED_BYTES || record.sizeBytes == 0L) {
                "File exceeds the offline pin size limit."
            }

            val stagingDirectory = File(context.filesDir, "pinned/.staging").apply { mkdirs() }
            val staged = File(stagingDirectory, "$fileId.${UUID.randomUUID()}.tmp")
            stagingFile = staged
            driveService.downloadFile(fileId, staged.absolutePath).getOrElse {
                throw IllegalStateException("Download failed.")
            }
            val downloadedBytes = staged.length()
            require(downloadedBytes <= MAX_PINNED_BYTES) { "File exceeds the offline pin size limit." }

            val pins = driveIndexDao.getPinnedFilesOldestFirst().filterNot { it.driveFileId == fileId }
            var totalPinnedBytes = pins.sumOf { safePinnedFile(it.pinnedPath)?.length() ?: 0L }
            var remainingPins = pins.size
            var evictionIndex = 0
            val toEvict = mutableListOf<DriveIndexFileEntity>()
            while (remainingPins + 1 > MAX_PINNED_FILES || totalPinnedBytes + downloadedBytes > MAX_PINNED_BYTES) {
                val oldest = pins.getOrNull(evictionIndex++)
                    ?: throw IllegalStateException("Pinned storage budget cannot fit this file.")
                toEvict += oldest
                totalPinnedBytes -= safePinnedFile(oldest.pinnedPath)?.length() ?: 0L
                remainingPins--
            }

            val pinnedDirectory = File(context.filesDir, "pinned").apply { mkdirs() }
            val finalFile = File(pinnedDirectory, "$fileId.bin")
            Files.move(
                staged.toPath(),
                finalFile.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
            stagingFile = null

            record.pinnedPath?.let { oldPath ->
                if (oldPath != finalFile.absolutePath) safePinnedFile(oldPath)?.delete()
            }
            toEvict.forEach { old ->
                safePinnedFile(old.pinnedPath)?.delete()
                driveIndexDao.unpin(old.driveFileId)
            }

            if (record.contentSha256 == null) {
                driveIndexDao.updateContentSha256(fileId, sha256(finalFile))
            }
            driveIndexDao.markPinned(
                driveFileId = fileId,
                path = finalFile.absolutePath,
                pinnedAtMs = System.currentTimeMillis(),
                pinnedModifiedTimeMs = record.modifiedTimeMs
            )
            Result.success(driveIndexDao.getByDriveId(fileId) ?: record)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "Could not pin file offline."))
        } finally {
            stagingFile?.delete()
        }
    }

    suspend fun unpin(fileId: String): Result<Boolean> = try {
        require(DriveIdValidator.isValidFileId(fileId)) { "Invalid Drive file id." }
        val record = driveIndexDao.getByDriveId(fileId)
        record?.pinnedPath?.let { safePinnedFile(it)?.delete() }
        driveIndexDao.unpin(fileId)
        Result.success(true)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        Result.failure(IllegalStateException("Could not remove the offline pin."))
    }

    private fun safePinnedFile(path: String?): File? {
        if (path.isNullOrBlank()) return null
        val root = File(context.filesDir, "pinned").canonicalFile
        val file = File(path).canonicalFile
        return file.takeIf { it.path.startsWith(root.path + File.separator) }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val read = input.read(buffer)
                if (read < 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
    }

    companion object {
        const val MAX_PINNED_FILES = 80
        const val MAX_PINNED_BYTES = 200L * 1024L * 1024L
    }
}
