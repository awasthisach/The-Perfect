package com.vvf.smartmanager.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Local filesystem metadata entity for ultra-fast indexing, filtering, and duplicate scanning.
 */
@Entity(
    tableName = "file_metadata",
    indices = [
        Index(value = ["path"], unique = true),
        Index(value = ["parentPath"]),
        Index(value = ["mimeType"]),
        Index(value = ["modifiedDate"]),
        Index(value = ["sizeBytes"]),
        Index(value = ["md5Hash"]),
        Index(value = ["isFavorite"]),
        Index(value = ["isTrash"]),
        Index(value = ["deletedTimestamp"])
    ]
)
data class FileMetadataEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val path: String,
    val name: String,
    val parentPath: String,
    val sizeBytes: Long,
    val mimeType: String,
    val isDirectory: Boolean,
    val modifiedDate: Long,
    val isFavorite: Boolean = false,
    val isTrash: Boolean = false,
    val originalPath: String? = null,
    val deletedTimestamp: Long? = null,
    val tags: String = "", // Comma-separated tags
    val md5Hash: String? = null, // For Level 2 duplicate detection
    val operationState: String = "IDLE", // To support DurableOperationState tests
    /** Extracted local/Drive text; never contains auth credentials. */
    @ColumnInfo(defaultValue = "''")
    val contentText: String = "",
    /** Canonical URL for cloud-backed entries; null for local filesystem entries. */
    val canonicalUri: String? = null
    /** True only while a verified app-private offline copy exists. */
    @ColumnInfo(defaultValue = "0")
    val offlinePinned: Boolean = false,
    val offlineLocalPath: String? = null,
    val offlinePinnedAt: Long? = null,
    @ColumnInfo(defaultValue = "0")
    val offlineBytes: Long = 0L
)
