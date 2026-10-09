package com.vvf.smartmanager.core.background.drive

import android.content.Context
import com.vvf.smartmanager.core.cloud.gdrive.DriveIdValidator
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.database.dao.DriveIndexDao
import com.vvf.smartmanager.core.database.model.DriveIndexFileEntity
import com.vvf.smartmanager.core.domain.DriveTextExtractionException
import com.vvf.smartmanager.core.domain.DriveTextExtractor
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.OcrOptions
import com.vvf.smartmanager.core.plugin.spi.IOcrEngine
import kotlinx.coroutines.CancellationException
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest

/**
 * Processes at most 50 locally downloaded files per run. Native text extraction stays on-device;
 * image OCR is skipped until the separate full-content consent is granted.
 */
class DriveContentIndexCoordinator(
    private val context: Context,
    private val driveService: GoogleDriveService,
    private val driveIndexDao: DriveIndexDao,
    private val textExtractor: DriveTextExtractor,
    private val ocrEngine: IOcrEngine,
    private val fullContentConsentGranted: () -> Boolean
) {
    suspend fun runBatch(): Result<Boolean> {
        val pending = driveIndexDao.getFilesNeedingText(BATCH_SIZE)
        if (pending.isEmpty()) return Result.success(false)

        for (record in pending) {
            try {
                indexOne(record)
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                driveIndexDao.updateIndexStatus(record.driveFileId, "EXTRACTION_FAILED", System.currentTimeMillis())
            }
        }
        return Result.success(driveIndexDao.getFilesNeedingText(1).isNotEmpty())
    }

    private suspend fun indexOne(record: DriveIndexFileEntity) {
        val isImage = record.mimeType.startsWith("image/", ignoreCase = true)
        if (isImage && !fullContentConsentGranted()) {
            driveIndexDao.updateIndexStatus(
                record.driveFileId,
                "OCR_CONSENT_REQUIRED",
                System.currentTimeMillis()
            )
            return
        }
        if (record.sizeBytes > MAX_FILE_BYTES && !record.mimeType.startsWith("application/vnd.google-apps.")) {
            driveIndexDao.updateIndexStatus(record.driveFileId, "TOO_LARGE", System.currentTimeMillis())
            return
        }
        require(DriveIdValidator.isValidFileId(record.driveFileId)) { "Invalid Drive file id in local index." }

        val tempDirectory = File(context.filesDir, "drive-index-temp").apply { mkdirs() }
        val tempFile = File(tempDirectory, record.driveFileId + ".content")
        try {
            val extractedMime = driveService.downloadForIndexing(
                fileId = record.driveFileId,
                mimeType = record.mimeType,
                destinationPath = tempFile.absolutePath
            ).getOrElse {
                driveIndexDao.updateIndexStatus(record.driveFileId, "DOWNLOAD_FAILED", System.currentTimeMillis())
                return
            }
            if (!tempFile.isFile || tempFile.length() > MAX_FILE_BYTES) {
                driveIndexDao.updateIndexStatus(record.driveFileId, "TOO_LARGE", System.currentTimeMillis())
                return
            }

            if (record.contentSha256 == null) {
                driveIndexDao.updateContentSha256(record.driveFileId, sha256(tempFile))
            }

            val text = if (isImage) {
                if (!fullContentConsentGranted()) {
                    driveIndexDao.updateIndexStatus(
                        record.driveFileId,
                        "OCR_CONSENT_REQUIRED",
                        System.currentTimeMillis()
                    )
                    return
                }
                val fileItem = FileItem(
                    path = tempFile.absolutePath,
                    name = record.name,
                    sizeBytes = tempFile.length(),
                    lastModified = record.modifiedTimeMs,
                    isDirectory = false,
                    mimeType = record.mimeType
                )
                ocrEngine.extractText(fileItem, OcrOptions(), null)
                    .getOrElse { throw DriveTextExtractionException("On-device OCR failed.") }
                    .fullText
            } else {
                textExtractor.extract(tempFile, extractedMime)
            }

            if (isImage && !fullContentConsentGranted()) {
                driveIndexDao.updateIndexStatus(
                    record.driveFileId,
                    "OCR_CONSENT_REQUIRED",
                    System.currentTimeMillis()
                )
                return
            }
            driveIndexDao.updateExtractedText(
                driveFileId = record.driveFileId,
                text = text.take(DriveTextExtractor.MAX_EXTRACTED_CHARACTERS),
                source = if (isImage) "OCR" else "NATIVE_TEXT",
                status = if (isImage) "OCR_INDEXED" else "TEXT_INDEXED",
                indexedAtMs = System.currentTimeMillis()
            )
        } finally {
            tempFile.delete()
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
        const val BATCH_SIZE = 50
        private const val MAX_FILE_BYTES = 50L * 1024L * 1024L
    }
}
