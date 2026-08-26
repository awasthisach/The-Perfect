package com.vvf.smartmanager.core.database.model

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity representing an encrypted file safely locked in the VVF Secure Vault.
 */
@Entity(
    tableName = "vault_items",
    indices = [
        Index(value = ["encryptedFileName"], unique = true),
        Index(value = ["category"]),
        Index(value = ["isDecoy"]),
        Index(value = ["createdAt"])
    ]
)
data class VaultItemEntity(
    @PrimaryKey
    val id: String, // UUID
    val encryptedFileName: String, // Stored encrypted filename on disk
    val originalName: String,
    val originalPath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val category: String, // Images, Videos, Documents, Audio, Archives, Other
    val encryptionIv: String, // Base64 encoded 12-byte initialization vector
    val authTag: String? = null, // Optional explicit Base64 auth tag
    val createdAt: Long = System.currentTimeMillis(),
    val notes: String? = null,
    @ColumnInfo(defaultValue = "0")
    val isDecoy: Boolean = false // Plausible deniability fake vault flag
)
