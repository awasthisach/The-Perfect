package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.cloud.gdrive.DriveIdValidator
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.database.dao.DriveIndexDao
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.security.MessageDigest
import java.util.UUID

/** Private offline cache with a hard count/size cap and oldest-first eviction. */
class DriveOfflinePinManager(
    private val filesDir: File,
    private val driveService: GoogleDriveService,
    private val driveIndexDao: DriveIndexDao
) {
    suspend fun pin(fileId: String): Result<File> = withContext(Dispatchers.IO) {
        var temporary: File? = null
        var destination: File? = null
        try {
            require(DriveIdValidator.isValidFileId(fileId)) { "Invalid Drive file id." }
            val metadata = driveIndexDao.getByDriveId(fileId)
                ?: throw IllegalStateException("File is not present in the local Drive index.")
            require(!metadata.mimeType.equals("application/vnd.google-apps.folder", ignoreCase = true)) {
                "Folders cannot be pinned offline."
            }

            val pinDirectory = File(filesDir, PIN_DIRECTORY).canonicalFile
            require(pinDirectory.path.startsWith(filesDir.canonicalFile.path + File.separator)) {
                "Offline cache path is invalid."
            }
            if (!pinDirectory.exists() && !pinDirectory.mkdirs()) {
                throw IllegalStateException("Could not create private offline cache.")
            }
            val tempFile = File(pinDirectory, ".${fileId}.${UUID.randomUUID()}.part").canonicalFile
            temporary = tempFile
            require(tempFile.path.startsWith(pinDirectory.path + File.separator)) { "Invalid temporary cache path." }

            val downloaded = driveService.downloadFile(fileId, tempFile.absolutePath).getOrElse { throw it }
            require(downloaded && tempFile.isFile) { "Drive did not provide a local file for offline pinning." }
            val byteCount = tempFile.length()
            require(byteCount in 1L..MAX_PINNED_BYTES) { "File is empty or exceeds the 200 MB offline limit." }
            if (metadata.sizeBytes > 0L && !metadata.mimeType.startsWith("application/vnd.google-apps.") &&
                metadata.sizeBytes != byteCount
            ) {
                throw IllegalStateException("Downloaded file size does not match Drive metadata.")
            }
            val digest = sha256(tempFile)
            val existingPinPath = metadata.pinnedPath
            val newDestination = File(pinDirectory, "${fileId}.${UUID.randomUUID()}.offline").canonicalFile
            destination = newDestination
            require(newDestination.path.startsWith(pinDirectory.path + File.separator)) { "Invalid offline destination path." }
            Files.move(
                tempFile.toPath(),
                newDestination.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
            temporary = null

            evictOldestUntilFits(fileId, byteCount)
            driveIndexDao.markPinned(fileId, newDestination.absolutePath, System.currentTimeMillis())
            driveIndexDao.updateContentSha256(fileId, digest)
            existingPinPath?.let { oldPath ->
                val oldFile = File(oldPath).canonicalFile
                if (oldFile.path.startsWith(pinDirectory.path + File.separator) &&
                    oldFile != newDestination && oldFile.exists()
                ) oldFile.delete()
            }
            Result.success(newDestination)
        } catch (cancelled: CancellationException) {
            temporary?.delete()
            throw cancelled
        } catch (e: Exception) {
            temporary?.delete()
            destination?.let { candidate ->
                if (candidate.exists() && candidate.canonicalPath.startsWith(File(filesDir, PIN_DIRECTORY).canonicalPath + File.separator)) {
                    candidate.delete()
                }
            }
            Result.failure(IllegalStateException(e.message ?: "Could not pin this file offline."))
        }
    }

    suspend fun unpin(fileId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            require(DriveIdValidator.isValidFileId(fileId)) { "Invalid Drive file id." }
            val record = driveIndexDao.getByDriveId(fileId)
                ?: return@withContext Result.success(false)
            val path = record.pinnedPath
            if (path != null) {
                val candidate = File(path).canonicalFile
                val pinRoot = File(filesDir, PIN_DIRECTORY).canonicalFile
                require(candidate.path.startsWith(pinRoot.path + File.separator)) {
                    "Stored offline path is outside the app-private pin directory."
                }
                if (candidate.exists() && !candidate.delete()) {
                    throw IllegalStateException("Could not remove the local offline copy.")
                }
            }
            driveIndexDao.unpin(fileId)
            Result.success(true)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(IllegalStateException(e.message ?: "Could not unpin this file."))
        }
    }

    private suspend fun evictOldestUntilFits(newFileId: String, newSize: Long) {
        val pins = driveIndexDao.getPinnedFiles()
            .filter { it.driveFileId != newFileId }
            .sortedBy { it.pinnedAtMs ?: Long.MIN_VALUE }
            .toMutableList()
        var totalBytes = pins.sumOf { record ->
            record.pinnedPath?.let { path ->
                runCatching { File(path).canonicalFile.length() }.getOrDefault(record.sizeBytes)
            } ?: record.sizeBytes
        }
        var count = pins.size
        while (count + 1 > MAX_PINNED_FILES || totalBytes + newSize > MAX_PINNED_BYTES) {
            val oldest = pins.removeFirstOrNull()
                ?: throw IllegalStateException("Offline cache cannot fit within the configured limit.")
            val path = oldest.pinnedPath
            val pathFile = path?.let { File(it).canonicalFile }
            val pinnedBytes = pathFile?.takeIf { it.exists() }?.length() ?: oldest.sizeBytes
            if (pathFile != null) {
                val root = File(filesDir, PIN_DIRECTORY).canonicalFile
                require(pathFile.path.startsWith(root.path + File.separator)) {
                    "Stored offline path is outside the app-private pin directory."
                }
                if (pathFile.exists() && !pathFile.delete()) {
                    throw IllegalStateException("Could not evict the oldest offline copy.")
                }
            }
            driveIndexDao.unpin(oldest.driveFileId)
            totalBytes = (totalBytes - pinnedBytes).coerceAtLeast(0L)
            count--
        }
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
        private const val PIN_DIRECTORY = "drive-offline-pins"
    }
}
