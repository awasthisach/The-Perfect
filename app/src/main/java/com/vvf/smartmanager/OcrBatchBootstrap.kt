package com.vvf.smartmanager

import com.vvf.smartmanager.core.background.workers.OcrBatchOutcome
import com.vvf.smartmanager.core.background.workers.OcrBatchRuntime
import com.vvf.smartmanager.core.database.VVFDatabase
import com.vvf.smartmanager.core.domain.OcrIndexingService
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.OcrOptions
import com.vvf.smartmanager.core.plugin.spi.IOcrEngine
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Wires OCR engine + DB + indexing into [OcrBatchRuntime] from the app composition root.
 */
object OcrBatchBootstrap {
    fun wire(
        database: VVFDatabase,
        ocrEngine: IOcrEngine,
        ocrIndexingService: OcrIndexingService,
        isEnabled: () -> Boolean = { true }
    ) {
        OcrBatchRuntime.configure {
            try {
                if (!isEnabled() || !ocrEngine.isEnabled) {
                    return@configure OcrBatchOutcome.Completed(0)
                }
                val recent = database.fileDao().getRecentFiles(limit = 40).first()
                val candidates = recent
                    .filter { !it.isDirectory }
                    .filter { entity ->
                        val mime = entity.mimeType.orEmpty()
                        mime.startsWith("image/") || mime == "application/pdf"
                    }
                    .filter { entity ->
                        val tags = entity.tags.orEmpty().lowercase()
                        !tags.split(',', ' ', ';').map { it.trim() }.contains("ocr")
                    }
                    .take(40)
                var processed = 0
                val failures = mutableListOf<String>()
                for (entity in candidates) {
                    val item = FileItem(
                        path = entity.path,
                        name = entity.name,
                        sizeBytes = entity.sizeBytes,
                        lastModified = entity.modifiedDate,
                        isDirectory = entity.isDirectory,
                        mimeType = entity.mimeType
                    )
                    val extraction = ocrEngine.extractText(item, OcrOptions(), null)
                    val extractionError = extraction.exceptionOrNull()
                    if (extractionError != null) {
                        if (extractionError is CancellationException) throw extractionError
                        failures += "${item.name}: extraction failed (${extractionError.message ?: "unknown error"})"
                        continue
                    }
                    val ocr = extraction.getOrThrow()
                    if (ocr.fullText.isNotBlank()) {
                        val indexing = ocrIndexingService.indexOcrResult(item, ocr)
                        val indexingError = indexing.exceptionOrNull()
                        if (indexingError != null) {
                            if (indexingError is CancellationException) throw indexingError
                            failures += "${item.name}: indexing failed (${indexingError.message ?: "unknown error"})"
                            continue
                        }
                        processed++
                    }
                }
                if (failures.isNotEmpty()) {
                    OcrBatchOutcome.RetryableFailure(
                        "Indexed $processed files; ${failures.size} candidate(s) failed: ${failures.take(5).joinToString("; ")}"
                    )
                } else {
                    OcrBatchOutcome.Completed(processed)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                OcrBatchOutcome.RetryableFailure(e.message ?: "ocr batch failed")
            }
        }
    }
}
