package com.vvf.smartmanager.core.domain

import androidx.room.withTransaction
import com.vvf.smartmanager.core.cloud.gdrive.DriveIdValidator
import com.vvf.smartmanager.core.database.VVFDatabase
import com.vvf.smartmanager.core.database.model.DriveIndexFileEntity
import com.vvf.smartmanager.core.database.model.DriveSyncStateEntity
import kotlinx.serialization.Serializable
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.Base64

/**
 * Versioned JSON backup for the local Drive index only.
 * OAuth/Firebase credentials, Drive change-page tokens, vault records and pinned file bytes are excluded.
 */
class DriveIndexBackupManager(private val database: VVFDatabase) {
    private val json = Json {
        ignoreUnknownKeys = false
        explicitNulls = false
        encodeDefaults = true
    }

    suspend fun exportTo(output: OutputStream): Result<Int> = try {
        val files = database.driveIndexDao().getAllForBackup()
        require(files.size <= MAX_FILES) { "Local index exceeds the backup file-count limit." }
        val envelope = BackupEnvelope(
            schemaVersion = SCHEMA_VERSION,
            createdAtMs = System.currentTimeMillis(),
            files = files.map(::toBackupEntry)
        )
        val bytes = json.encodeToString(envelope).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BACKUP_BYTES) { "Local index backup exceeds 100 MB." }
        output.write(bytes)
        output.flush()
        Result.success(files.size)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        Result.failure(IllegalStateException(e.message ?: "Could not export local index JSON."))
    }

    suspend fun importFrom(input: InputStream): Result<Int> = try {
        val bytes = readBounded(input, MAX_BACKUP_BYTES)
        val envelope = json.decodeFromString<BackupEnvelope>(bytes.toString(Charsets.UTF_8))
        require(envelope.schemaVersion == SCHEMA_VERSION) { "Unsupported local index backup version." }
        require(envelope.files.size <= MAX_FILES) { "Backup contains too many files." }
        require(envelope.files.map { it.driveFileId }.distinct().size == envelope.files.size) {
            "Backup contains duplicate Drive file IDs."
        }
        val imported = envelope.files.map(::toEntity)
        val dao = database.driveIndexDao()
        database.withTransaction {
            val pinnedById = dao.getPinnedFiles().associateBy { it.driveFileId }
            dao.clearUnpinnedForImport()
            val merged = imported.map { incoming ->
                val pinned = pinnedById[incoming.driveFileId] ?: return@map incoming
                val contentChanged = pinned.modifiedTimeMs != incoming.modifiedTimeMs
                incoming.copy(
                    pinnedPath = pinned.pinnedPath,
                    pinnedAtMs = pinned.pinnedAtMs,
                    indexStatus = if (contentChanged) "METADATA_CHANGED_PIN_STALE" else incoming.indexStatus
                )
            }
            dao.upsertFiles(merged)
            // Never import Drive page/change tokens. Force the next sync to establish a fresh cursor.
            dao.saveSyncState(
                DriveSyncStateEntity(
                    indexingInProgress = false,
                    listingIncomplete = true,
                    lastError = null
                )
            )
        }
        Result.success(imported.size)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (e: Exception) {
        Result.failure(IllegalArgumentException(e.message ?: "Local index backup is invalid."))
    }

    private fun toBackupEntry(file: DriveIndexFileEntity): BackupEntry {
        val vector = file.embeddingVector?.takeIf {
            file.embeddingModel == EXPECTED_EMBEDDING_MODEL &&
                file.embeddingVersion == EXPECTED_EMBEDDING_VERSION &&
                file.embeddingDimension == EXPECTED_EMBEDDING_DIMENSION &&
                it.size == EXPECTED_EMBEDDING_DIMENSION * Float.SIZE_BYTES
        }
        return BackupEntry(
            driveFileId = file.driveFileId,
            name = file.name,
            mimeType = file.mimeType,
            sizeBytes = file.sizeBytes,
            modifiedTimeMs = file.modifiedTimeMs,
            parentIdsCsv = file.parentIdsCsv,
            webViewLink = file.webViewLink,
            starred = file.starred,
            extractedText = file.extractedText,
            extractionSource = file.extractionSource,
            contentSha256 = file.contentSha256,
            embeddingModel = if (vector != null) file.embeddingModel else null,
            embeddingVersion = if (vector != null) file.embeddingVersion else null,
            embeddingDimension = if (vector != null) file.embeddingDimension else null,
            embeddingVectorBase64 = vector?.let { Base64.getEncoder().encodeToString(it) },
            indexStatus = file.indexStatus,
            lastIndexedAtMs = file.lastIndexedAtMs
        )
    }

    private fun toEntity(entry: BackupEntry): DriveIndexFileEntity {
        require(DriveIdValidator.isValidFileId(entry.driveFileId)) { "Backup contains an invalid Drive file ID." }
        require(entry.name.isNotBlank() && entry.name.length <= MAX_NAME_CHARS && entry.name.none { it.isISOControl() }) {
            "Backup contains an invalid file name."
        }
        require(entry.mimeType.length <= MAX_MIME_CHARS && entry.sizeBytes >= 0L && entry.modifiedTimeMs >= 0L) {
            "Backup contains invalid file metadata."
        }
        require(entry.extractedText.length <= MAX_TEXT_CHARS) { "Backup contains oversized extracted text." }
        require(entry.extractionSource == null || entry.extractionSource in setOf("NATIVE_TEXT", "OCR")) {
            "Backup contains an unsupported extraction source."
        }
        require(entry.contentSha256 == null || SHA256_REGEX.matches(entry.contentSha256)) {
            "Backup contains an invalid SHA-256 value."
        }
        require(entry.indexStatus in ALLOWED_INDEX_STATUSES) { "Backup contains an unsupported index status." }
        val vector = entry.embeddingVectorBase64?.let { encoded ->
            require(entry.embeddingModel == EXPECTED_EMBEDDING_MODEL &&
                entry.embeddingVersion == EXPECTED_EMBEDDING_VERSION &&
                entry.embeddingDimension == EXPECTED_EMBEDDING_DIMENSION
            ) { "Backup contains an unsupported embedding contract." }
            val decoded = try {
                Base64.getDecoder().decode(encoded)
            } catch (_: IllegalArgumentException) {
                throw IllegalArgumentException("Backup contains an invalid embedding vector.")
            }
            require(decoded.size == EXPECTED_EMBEDDING_DIMENSION * Float.SIZE_BYTES) {
                "Backup contains an invalid embedding vector dimension."
            }
            decoded
        }
        return DriveIndexFileEntity(
            driveFileId = entry.driveFileId,
            name = entry.name,
            mimeType = entry.mimeType,
            sizeBytes = entry.sizeBytes,
            modifiedTimeMs = entry.modifiedTimeMs,
            parentIdsCsv = entry.parentIdsCsv.take(MAX_PARENT_IDS_CHARS),
            webViewLink = entry.webViewLink?.take(MAX_LINK_CHARS),
            starred = entry.starred,
            extractedText = entry.extractedText,
            extractionSource = entry.extractionSource,
            contentSha256 = entry.contentSha256,
            embeddingModel = entry.embeddingModel,
            embeddingVersion = entry.embeddingVersion,
            embeddingDimension = entry.embeddingDimension,
            embeddingVector = vector,
            indexStatus = entry.indexStatus,
            lastIndexedAtMs = entry.lastIndexedAtMs.coerceAtLeast(0L)
        )
    }

    private fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "Local index backup exceeds 100 MB." }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    @Serializable
    private data class BackupEnvelope(
        val schemaVersion: Int,
        val createdAtMs: Long,
        val files: List<BackupEntry>
    )

    @Serializable
    private data class BackupEntry(
        val driveFileId: String,
        val name: String,
        val mimeType: String,
        val sizeBytes: Long,
        val modifiedTimeMs: Long,
        val parentIdsCsv: String = "",
        val webViewLink: String? = null,
        val starred: Boolean = false,
        val extractedText: String = "",
        val extractionSource: String? = null,
        val contentSha256: String? = null,
        val embeddingModel: String? = null,
        val embeddingVersion: Int? = null,
        val embeddingDimension: Int? = null,
        val embeddingVectorBase64: String? = null,
        val indexStatus: String = "METADATA_ONLY",
        val lastIndexedAtMs: Long = 0L
    )

    companion object {
        const val SCHEMA_VERSION = 1
        private const val MAX_FILES = 20_000
        private const val MAX_BACKUP_BYTES = 100 * 1024 * 1024
        private const val MAX_NAME_CHARS = 1024
        private const val MAX_MIME_CHARS = 256
        private const val MAX_TEXT_CHARS = 1_000_000
        private const val MAX_PARENT_IDS_CHARS = 16_384
        private const val MAX_LINK_CHARS = 4096
        private const val EXPECTED_EMBEDDING_MODEL = "gemini-embedding-2"
        private const val EXPECTED_EMBEDDING_VERSION = 3
        private const val EXPECTED_EMBEDDING_DIMENSION = 768
        private val SHA256_REGEX = Regex("[a-fA-F0-9]{64}")
        private val ALLOWED_INDEX_STATUSES = setOf(
            "METADATA_ONLY", "TEXT_INDEXED", "OCR_INDEXED", "OCR_CONSENT_REQUIRED",
            "DOWNLOAD_FAILED", "EXTRACTION_FAILED", "TOO_LARGE", "METADATA_CHANGED_PIN_STALE",
            "REMOTE_REMOVED"
        )
    }
}
