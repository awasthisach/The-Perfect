package com.vvf.smartmanager.core.database.model

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey

/**
 * Encrypted SQLCipher-backed cache of consented semantic vectors. The vector is a 768-float
 * little-endian BLOB; contentHash and modelVersion prevent stale/incompatible reuse.
 */
@Entity(
    tableName = "semantic_embeddings",
    foreignKeys = [
        ForeignKey(
            entity = FileMetadataEntity::class,
            parentColumns = ["path"],
            childColumns = ["filePath"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class SemanticEmbeddingEntity(
    @PrimaryKey
    val filePath: String,
    val contentHash: String,
    val modelVersion: String,
    val vector: ByteArray,
    val updatedAt: Long = System.currentTimeMillis()
)
