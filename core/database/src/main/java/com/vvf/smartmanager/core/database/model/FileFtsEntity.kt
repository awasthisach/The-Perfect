package com.vvf.smartmanager.core.database.model

import androidx.room.Entity
import androidx.room.Fts4
import androidx.room.PrimaryKey

/**
 * FTS4 full-text search virtual table mapped to FileMetadataEntity.
 */
@Entity(tableName = "file_fts")
@Fts4(contentEntity = FileMetadataEntity::class)
data class FileFtsEntity(
    @PrimaryKey
    val rowid: Long,
    val name: String,
    val path: String,
    val tags: String,
    val mimeType: String
)
