package com.vvf.smartmanager.core.database.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Locally indexed metadata and content for one Google Drive file. File bytes themselves are
 * never stored in this row; pinnedPath must point only inside app-private storage.
 */
@Entity(
    tableName = "drive_index_files",
    indices = [
        Index(value = ["driveFileId"], unique = true),
        Index(value = ["name"]),
        Index(value = ["mimeType"]),
        Index(value = ["sizeBytes"]),
        Index(value = ["modifiedTimeMs"]),
        Index(value = ["pinnedAtMs"])
    ]
)
data class DriveIndexFileEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
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
    val embeddingVector: ByteArray? = null,
    val pinnedPath: String? = null,
    val pinnedAtMs: Long? = null,
    val pinnedModifiedTimeMs: Long? = null,
    val indexStatus: String = "METADATA_ONLY",
    val indexAttempts: Int = 0,
    val lastIndexedAtMs: Long = 0L
)
