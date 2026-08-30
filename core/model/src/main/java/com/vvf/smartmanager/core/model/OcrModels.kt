package com.vvf.smartmanager.core.model

/**
 * Detailed OCR Extraction Result.
 */
data class OcrResult(
    val fullText: String,
    val blocks: List<OcrBlock> = emptyList(),
    val totalWords: Int = 0,
    val totalLines: Int = 0,
    val pageCount: Int = 1,
    val language: String? = null,
    val processingDurationMs: Long = 0L,
    val sourceFilePath: String = "",
    val confidence: Float = 1.0f
)

/**
 * Individual detected text block in OCR parsing.
 */
data class OcrBlock(
    val text: String,
    val lineCount: Int = 1,
    val confidence: Float = 1.0f
)

/**
 * Configuration options for OCR processing to optimize memory and performance.
 */
data class OcrOptions(
    val maxDimension: Int = 2048,
    val maxPagesForPdf: Int = 50,
    val autoRotate: Boolean = true,
    val downsampleLargeImages: Boolean = true
)

/**
 * Progress status for long-running / multi-page OCR operations.
 */
data class OcrProgress(
    val currentStep: String = "",
    val progressFraction: Float = 0f, // 0.0 to 1.0
    val currentPage: Int = 1,
    val totalPages: Int = 1,
    val isComplete: Boolean = false,
    val isCancelled: Boolean = false
)
