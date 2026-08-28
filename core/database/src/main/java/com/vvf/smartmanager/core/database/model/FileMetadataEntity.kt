package com.vvf.smartmanager.core.database.model

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
    val operationState: String = "IDLE" // To support DurableOperationState tests
)
