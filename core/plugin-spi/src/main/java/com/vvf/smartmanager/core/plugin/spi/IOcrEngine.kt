package com.vvf.smartmanager.core.plugin.spi

import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.OcrOptions
import com.vvf.smartmanager.core.model.OcrProgress
import com.vvf.smartmanager.core.model.OcrResult

/**
 * Standard Service Provider Interface (SPI) defining on-device text extraction operations.
 * Allows pluggable OCR engines (Google ML Kit, Tesseract, on-demand modules) to provide
 * uniform optical character recognition services to the VVF Smart Manager core application.
 */
interface IOcrEngine {
    val pluginId: String
    val displayName: String
    val version: String
    val isEnabled: Boolean

    /**
     * Extracts text from the given file item (images or multi-page documents).
     *
     * @param fileItem The target file to process.
     * @param options Configuration parameters such as max dimensions, PDF page limits, etc.
     * @param onProgress Optional lambda receiving real-time progress updates.
     * @return Result containing detailed [OcrResult] or failure exception.
     */
    suspend fun extractText(
        fileItem: FileItem,
        options: OcrOptions = OcrOptions(),
        onProgress: ((OcrProgress) -> Unit)? = null
    ): Result<OcrResult>

    /**
     * Checks if the required on-device ML model is ready/downloaded.
     */
    suspend fun isModelDownloaded(): Boolean

    /**
     * Downloads or prepares the on-device ML model with progress tracking.
     */
    suspend fun downloadModel(progressCallback: (Float) -> Unit): Boolean

    /**
     * Cancels any currently executing OCR operation immediately.
     */
    fun cancelOngoing()
}
