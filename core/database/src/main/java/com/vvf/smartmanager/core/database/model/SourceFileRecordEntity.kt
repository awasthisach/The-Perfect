package com.vvf.smartmanager.core.database.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.vvf.smartmanager.core.model.IndexedSourceFile

/**
 * Encrypted catalog entry keyed by provider identity, never by a display path.
 *
 * This table stores metadata only. It does not store document text, OCR output, chunks,
 * embeddings, or instructions to mutate the underlying source file.
 */
@Entity(
    tableName = "source_file_records",
    indices = [
        Index(value = ["sourceType", "sourceInstanceId"]),
        Index(value = ["accessState"]),
        Index(value = ["sourceType", "sourceInstanceId", "sourceFileId"], unique = true)
    ]
)
data class SourceFileRecordEntity(
    @PrimaryKey
    val identityKey: String,
    val sourceType: String,
    val sourceInstanceId: String,
    val sourceFileId: String,
    val displayName: String,
    val sizeBytes: Long,
    val mimeType: String?,
    val modifiedAtEpochMillis: Long,
    val accessState: String,
    val sha256: String?
) {
    companion object {
        fun from(file: IndexedSourceFile): SourceFileRecordEntity =
            SourceFileRecordEntity(
                identityKey = file.identity.stableKey,
                sourceType = file.identity.sourceType.name,
                sourceInstanceId = file.identity.sourceInstanceId,
                sourceFileId = file.identity.sourceFileId,
                displayName = file.displayName,
                sizeBytes = file.sizeBytes,
                mimeType = file.mimeType,
                modifiedAtEpochMillis = file.modifiedAtEpochMillis,
                accessState = file.accessState.name,
                sha256 = file.sha256?.lowercase()
            )
    }
}
