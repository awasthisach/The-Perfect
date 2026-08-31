package com.vvf.smartmanager.core.plugin.spi

import com.vvf.smartmanager.core.model.FileItem

/** Service Provider Interface for on-demand OCR text extraction. */
interface OcrPluginSPI : IOcrEngine {
    override val pluginId: String get() = "plugin.ocr.mlkit"
    override val displayName: String get() = "ML Kit OCR Text Scanner"
    override val version: String get() = "1.0.0"
}

/** Service Provider Interface for local on-device semantic search. */
interface SemanticSearchSPI : ISemanticSearchEngine {
    override val pluginId: String get() = "plugin.semantic.tflite"
    override val version: String get() = "1.0.0"
}

/** Canonical identity returned by a successful cloud upload. */
data class CloudUploadResult(
    val remoteId: String,
    val remotePath: String,
    val sizeBytes: Long
) {
    init {
        require(remoteId.isNotBlank()) { "remoteId must not be blank" }
        require(remotePath.isNotBlank()) { "remotePath must not be blank" }
        require(sizeBytes >= 0L) { "sizeBytes must be non-negative" }
    }
}

/** Service Provider Interface for external cloud storage providers. */
interface CloudDriverSPI {
    val driverId: String
    val displayName: String
    val iconResName: String

    suspend fun authenticate(): Boolean
    suspend fun listRemoteFiles(remotePath: String): List<FileItem>

    /**
     * Uploads a file. Implementations must return true only after the remote
     * provider has durably accepted the upload.
     */
    suspend fun uploadFile(localFile: FileItem, remoteDirectory: String): Boolean

    suspend fun downloadFile(remoteFile: FileItem, localDestination: String): Boolean
    suspend fun getQuotaUsage(): Pair<Long, Long>
}
