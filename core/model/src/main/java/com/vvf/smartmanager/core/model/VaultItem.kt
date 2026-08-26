package com.vvf.smartmanager.core.model

import java.io.File

/**
 * Model representing an encrypted item in the Secure Vault.
 */
data class VaultItem(
    val id: String,
    val encryptedFileName: String = "",
    val originalName: String = "",
    val originalPath: String = "",
    val mimeType: String = "application/octet-stream",
    val sizeBytes: Long = 0L,
    val category: String = "Other",
    val encryptionIv: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val notes: String? = null,
    val isDecoy: Boolean = false,
    // Convenience / backward-compat fields
    val vaultRelativePath: String = encryptedFileName,
    val fileName: String = if (originalName.isNotEmpty()) originalName else "Encrypted_File",
    val encryptedTimestamp: Long = createdAt,
    val ivBase64: String = encryptionIv
) {
    val extension: String
        get() = (if (originalName.isNotEmpty()) originalName else fileName).substringAfterLast('.', "").lowercase()

    val formattedCategory: String
        get() = category.replaceFirstChar { if (it.isLowerCase()) it.titlecase() else it.toString() }
}

/**
 * Storage space breakdown across internal, external, and vault partitions.
 */
data class StorageBreakdown(
    val totalBytes: Long,
    val usedBytes: Long,
    val freeBytes: Long,
    val imagesBytes: Long = 0L,
    val videosBytes: Long = 0L,
    val audioBytes: Long = 0L,
    val docsBytes: Long = 0L,
    val systemBytes: Long = 0L,
    val vaultBytes: Long = 0L
) {
    val usedPercentage: Float
        get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()) else 0f
}

/**
 * Model representing plugin metadata and installation state.
 */
data class PluginInfo(
    val id: String,
    val name: String,
    val description: String,
    val version: String,
    val isInstalled: Boolean = false,
    val isEnabled: Boolean = false,
    val downloadSizeBytes: Long = 0L,
    val category: PluginType = PluginType.CORE_EXTENSION
)

enum class PluginType {
    OCR,
    SEMANTIC_SEARCH,
    CLOUD_DRIVER,
    CORE_EXTENSION
}
