package com.vvf.smartmanager.core.plugin.spi

import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.OcrOptions
import com.vvf.smartmanager.core.model.OcrProgress
import com.vvf.smartmanager.core.model.OcrResult
import kotlinx.coroutines.flow.Flow

/**
 * Service Provider Interface for on-demand OCR text extraction plugin,
 * conforming to IOcrEngine standard contract.
 */
interface OcrPluginSPI : IOcrEngine {
    override val pluginId: String get() = "plugin.ocr.mlkit"
    override val displayName: String get() = "ML Kit OCR Text Scanner"
    override val version: String get() = "1.0.0"
}

/**
 * Service Provider Interface for local on-device TFLite semantic embeddings & similarity search,
 * conforming to ISemanticSearchEngine standard contract.
 */
interface SemanticSearchSPI : ISemanticSearchEngine {
    override val pluginId: String get() = "plugin.semantic.tflite"
    override val version: String get() = "1.0.0"
}


/**
 * Service Provider Interface for external cloud storage providers (OneDrive, Dropbox, S3, NextCloud, NAS).
 */
interface CloudDriverSPI {
    val driverId: String
    val displayName: String
    val iconResName: String

    suspend fun authenticate(): Boolean
    suspend fun listRemoteFiles(remotePath: String): List<FileItem>
    suspend fun uploadFile(localFile: FileItem, remoteDirectory: String): Boolean
    suspend fun downloadFile(remoteFile: FileItem, localDestination: String): Boolean
    suspend fun getQuotaUsage(): Pair<Long, Long> // (Used, Total)
}
