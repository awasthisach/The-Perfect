package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.data.FileManagerRepository
import com.vvf.smartmanager.core.data.SearchRepository
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.OcrOptions
import com.vvf.smartmanager.core.model.OcrProgress
import com.vvf.smartmanager.core.model.OcrResult
import com.vvf.smartmanager.core.plugin.spi.IOcrEngine
import java.io.File
import java.util.Locale

/**
 * UseCase for performing on-device OCR text extraction on images and PDFs.
 */
class ExtractTextUseCase(
    private val ocrPlugin: IOcrEngine
) {
    suspend operator fun invoke(
        fileItem: FileItem,
        options: OcrOptions = OcrOptions(),
        onProgress: ((OcrProgress) -> Unit)? = null
    ): Result<OcrResult> {
        if (!ocrPlugin.isEnabled) {
            return Result.failure(IllegalStateException("OCR Plugin is disabled in Plugin Manager."))
        }
        return ocrPlugin.extractText(fileItem, options, onProgress)
    }

    fun cancel() {
        ocrPlugin.cancelOngoing()
    }
}

/**
 * UseCase for indexing extracted OCR text safely into Core Search metadata/tags.
 * Guarantees sanitization, deduplication, and max text length constraints to prevent DB bloat.
 */
class IndexOcrTextUseCase(
    private val searchRepository: SearchRepository
) {
    suspend operator fun invoke(
        fileItem: FileItem,
        ocrResult: OcrResult,
        maxCustomKeywords: Int = 5
    ): Result<Boolean> {
        if (ocrResult.fullText.isBlank()) {
            return Result.success(true)
        }

        // 1. Tag with foundational #ocr tag
        searchRepository.addTagToFile(fileItem.path, "ocr")

        // 2. Extract salient keywords (min 4 chars, alphanumeric only, lowercased, deduplicated)
        val stopwords = setOf(
            "the", "and", "for", "with", "this", "that", "from", "have", "been",
            "page", "date", "name", "total", "your", "will", "they", "what", "which"
        )

        val words = ocrResult.fullText
            .lowercase(Locale.getDefault())
            .replace(Regex("[^a-z0-9\\s]"), " ")
            .split(Regex("\\s+"))
            .filter { it.length in 4..20 && !stopwords.contains(it) }
            .distinct()
            .take(maxCustomKeywords)

        for (word in words) {
            searchRepository.addTagToFile(fileItem.path, word)
        }

        return Result.success(true)
    }
}

/**
 * UseCase for saving extracted OCR text to an offline .txt file.
 */
class SaveOcrTextUseCase(
    private val fileManagerRepository: FileManagerRepository
) {
    suspend operator fun invoke(
        originalFile: FileItem,
        ocrResult: OcrResult,
        customFileName: String? = null
    ): Result<FileItem> {
        val parentDir = File(originalFile.path).parent ?: fileManagerRepository.getDefaultStoragePath()
        val baseName = if (!customFileName.isNullOrBlank()) {
            if (customFileName.endsWith(".txt")) customFileName else "$customFileName.txt"
        } else {
            val rawName = originalFile.name.substringBeforeLast(".")
            "${rawName}_ocr_text.txt"
        }

        val textBytes = ocrResult.fullText.toByteArray(Charsets.UTF_8)
        return fileManagerRepository.createFile(
            parentPath = parentDir,
            fileName = baseName,
            content = textBytes
        )
    }
}
