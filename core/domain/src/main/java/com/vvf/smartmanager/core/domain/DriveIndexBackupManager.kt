package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.cloud.gdrive.DriveIdValidator
import com.vvf.smartmanager.core.cloud.gdrive.DriveSessionPolicy
import com.vvf.smartmanager.core.database.dao.DriveIndexDao
import com.vvf.smartmanager.core.database.model.DriveIndexFileEntity
import com.vvf.smartmanager.core.database.model.DriveSyncStateEntity
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class DriveIndexBackupSummary(
    val fileCount: Int,
    val truncated: Boolean
)

@Serializable
private data class DriveIndexBackupPayload(
    val formatVersion: Int = 1,
    val accountEmail: String,
    val exportedAtMs: Long,
    val truncated: Boolean,
    val files: List<DriveIndexBackupFile>
)

@Serializable
private data class DriveIndexBackupFile(
    val driveFileId: String,
    val name: String,
    val mimeType: String,
    val sizeBytes: Long,
    val modifiedTimeMs: Long,
    val parentIdsCsv: String,
    val webViewLink: String? = null,
    val starred: Boolean = false,
    val extractedText: String = "",
    val extractionSource: String? = null,
    val contentSha256: String? = null
)

/**
 * JSON backup contains only local index metadata and extracted text. It deliberately excludes
 * OAuth/Firebase tokens, API keys, vector blobs, pinned paths and file bytes.
 */
class DriveIndexBackupManager(
    private val driveIndexDao: DriveIndexDao,
    private val currentAccountEmail: () -> String?,
    private val allowOcrContent: () -> Boolean
) {
    private val json = Json {
        encodeDefaults = true
        explicitNulls = false
        ignoreUnknownKeys = false
    }

    suspend fun exportTo(output: OutputStream): Result<DriveIndexBackupSummary> = try {
        val email = currentAccountEmail()?.trim()?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Sign in to Google Drive before exporting the local index.")
        val fetchedRecords = driveIndexDao.getRecentFiles(MAX_FILES + 1)
        var totalTextChars = 0
        var truncated = fetchedRecords.size > MAX_FILES
        val records = fetchedRecords.take(MAX_FILES)
        val payloadFiles = records.map { record ->
            val remaining = (MAX_TOTAL_TEXT_CHARS - totalTextChars).coerceAtLeast(0)
            val allowed = minOf(MAX_TEXT_PER_FILE, remaining)
            val text = if (record.extractionSource == "OCR" && !allowOcrContent()) {
                ""
            } else {
                record.extractedText.take(allowed)
            }
            totalTextChars += text.length
            if (text.length < record.extractedText.length) truncated = true
            DriveIndexBackupFile(
                driveFileId = record.driveFileId,
                name = record.name.take(255),
                mimeType = record.mimeType.take(255),
                sizeBytes = record.sizeBytes,
                modifiedTimeMs = record.modifiedTimeMs,
                parentIdsCsv = record.parentIdsCsv.take(4096),
                webViewLink = record.webViewLink?.takeIf(::isSafeDriveLink),
                starred = record.starred,
                extractedText = text,
                extractionSource = record.extractionSource?.takeIf { it != "OCR" || allowOcrContent() },
                contentSha256 = record.contentSha256
            )
        }
        val payload = DriveIndexBackupPayload(
            accountEmail = email,
            exportedAtMs = System.currentTimeMillis(),
            truncated = truncated,
            files = payloadFiles
        )
        val bytes = json.encodeToString(payload).toByteArray(StandardCharsets.UTF_8)
        require(bytes.size <= MAX_BACKUP_BYTES) { "Local index backup exceeds the 50 MiB safety limit." }
        output.write(bytes)
        output.flush()
        Result.success(DriveIndexBackupSummary(payloadFiles.size, truncated))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        Result.failure(IllegalStateException(e.message ?: "Could not export the local index."))
    }

    suspend fun importFrom(input: InputStream): Result<DriveIndexBackupSummary> = try {
        val email = currentAccountEmail()?.trim()?.lowercase()
            ?.takeIf { it.isNotBlank() }
            ?: throw IllegalStateException("Sign in to Google Drive before importing the local index.")
        val bytes = readBounded(input, MAX_BACKUP_BYTES)
        val payload = json.decodeFromString<DriveIndexBackupPayload>(String(bytes, StandardCharsets.UTF_8))
        require(payload.formatVersion == 1) { "Unsupported local index backup version." }
        require(DriveSessionPolicy.accountsMatch(payload.accountEmail, email)) {
            "This backup belongs to a different Google account."
        }
        require(payload.files.size <= MAX_FILES) { "Backup contains too many files." }
        require(payload.files.map { it.driveFileId }.distinct().size == payload.files.size) {
            "Backup contains duplicate file ids."
        }

        var totalTextChars = 0
        val validated = payload.files.map { item ->
            require(DriveIdValidator.isValidFileId(item.driveFileId)) { "Backup contains an invalid Drive id." }
            require(item.name.isNotBlank() && item.name.length <= 255) { "Backup contains an invalid file name." }
            require(item.mimeType.length <= 255 && item.parentIdsCsv.length <= 4096) {
                "Backup metadata exceeds allowed limits."
            }
            require(item.sizeBytes >= 0L && item.modifiedTimeMs >= 0L) { "Backup contains invalid file metadata." }
            require(item.extractedText.length <= MAX_TEXT_PER_FILE) { "Backup contains an oversized text entry." }
            totalTextChars += item.extractedText.length
            require(totalTextChars <= MAX_TOTAL_TEXT_CHARS) { "Backup extracted text exceeds the safety limit." }
            require(item.contentSha256 == null || SHA256_PATTERN.matches(item.contentSha256)) {
                "Backup contains an invalid SHA-256 digest."
            }
            require(item.parentIdsCsv.split(',').filter { it.isNotBlank() }.all(DriveIdValidator::isValidParentId)) {
                "Backup contains an invalid parent id."
            }
            require(item.webViewLink == null || isSafeDriveLink(item.webViewLink)) {
                "Backup contains an unsafe preview link."
            }
            item
        }

        var syncState = driveIndexDao.getSyncState()
        if (syncState?.accountEmail != null && syncState.accountEmail != email) {
            driveIndexDao.clearUnpinnedDriveRecords()
            driveIndexDao.markPinsFromPreviousAccount()
            syncState = DriveSyncStateEntity(accountEmail = email)
        }
        var imported = 0
        for (item in validated) {
            val existing = driveIndexDao.getByDriveId(item.driveFileId)
            val ocrAllowed = allowOcrContent()
            val removeOcr = item.extractionSource == "OCR" && !ocrAllowed
            val contentChanged = existing != null && existing.modifiedTimeMs != item.modifiedTimeMs
            val text = when {
                removeOcr || contentChanged -> ""
                else -> item.extractedText
            }
            val source = when {
                removeOcr || contentChanged -> null
                else -> item.extractionSource
            }
            driveIndexDao.upsertFile(
                DriveIndexFileEntity(
                    id = existing?.id ?: 0L,
                    driveFileId = item.driveFileId,
                    name = item.name,
                    mimeType = item.mimeType,
                    sizeBytes = item.sizeBytes,
                    modifiedTimeMs = item.modifiedTimeMs,
                    parentIdsCsv = item.parentIdsCsv,
                    webViewLink = item.webViewLink,
                    starred = item.starred,
                    extractedText = text,
                    extractionSource = source,
                    contentSha256 = if (contentChanged) null else item.contentSha256,
                    embeddingModel = null,
                    embeddingVersion = null,
                    embeddingDimension = null,
                    embeddingVector = null,
                    pinnedPath = existing?.pinnedPath,
                    pinnedAtMs = existing?.pinnedAtMs,
                    pinnedModifiedTimeMs = existing?.pinnedModifiedTimeMs,
                    indexStatus = when {
                        removeOcr -> "OCR_CONSENT_REQUIRED"
                        contentChanged || text.isBlank() -> "METADATA_ONLY"
                        else -> "TEXT_INDEXED"
                    },
                    indexAttempts = 0,
                    lastIndexedAtMs = if (text.isNotBlank()) System.currentTimeMillis() else 0L
                )
            )
            imported++
        }
        val now = System.currentTimeMillis()
        val currentState = syncState ?: DriveSyncStateEntity(accountEmail = email)
        val reconciledState = if (currentState.accountEmail == email && currentState.changeStartPageToken != null) {
            currentState.copy(
                accountEmail = email,
                fullListPageToken = "",
                fullListGeneration = now,
                fullListFilesSeen = 0,
                changePageToken = null,
                indexingInProgress = true,
                listingIncomplete = true,
                lastError = "Imported index; reconciling with Drive."
            )
        } else {
            currentState.copy(
                accountEmail = email,
                fullListPageToken = null,
                fullListGeneration = null,
                fullListFilesSeen = 0,
                changePageToken = null,
                indexingInProgress = false,
                listingIncomplete = true,
                lastError = "Imported index; Drive sync is required."
            )
        }
        driveIndexDao.saveSyncState(reconciledState)
        Result.success(DriveIndexBackupSummary(imported, payload.truncated))
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        Result.failure(IllegalStateException(e.message ?: "Could not import the local index."))
    }

    private fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        input.use { source ->
            while (true) {
                val read = source.read(buffer)
                if (read < 0) break
                total += read
                require(total <= maxBytes) { "Local index backup exceeds the 50 MiB safety limit." }
                output.write(buffer, 0, read)
            }
        }
        return output.toByteArray()
    }

    private fun isSafeDriveLink(value: String): Boolean =
        value.startsWith("https://drive.google.com/") || value.startsWith("https://docs.google.com/")

    companion object {
        private const val MAX_FILES = 20_000
        private const val MAX_TEXT_PER_FILE = 100_000
        private const val MAX_TOTAL_TEXT_CHARS = 5_000_000
        private const val MAX_BACKUP_BYTES = 50 * 1024 * 1024
        private val SHA256_PATTERN = Regex("^[a-fA-F0-9]{64}$")
    }
}
