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
import com.vvf.smartmanager.core.database.dao.FileDao

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
                val fileDao = database.fileDao()
                val candidates = fileDao.getOcrCandidates(
                    limit = OCR_BATCH_SIZE,
                    nowEpochMillis = System.currentTimeMillis()
                ).first()
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
                        deferCandidate(fileDao, item.path)
                        failures += "${item.name}: extraction failed (${extractionError.message ?: "unknown error"})"
                        continue
                    }
                    val ocr = extraction.getOrThrow()
                    if (ocr.fullText.isNotBlank()) {
                        val indexing = ocrIndexingService.indexOcrResult(item, ocr)
                        val indexingError = indexing.exceptionOrNull()
                        if (indexingError != null) {
                            if (indexingError is CancellationException) throw indexingError
                            deferCandidate(fileDao, item.path)
                            failures += "${item.name}: indexing failed (${indexingError.message ?: "unknown error"})"
                            continue
                        }
                        clearCandidateDeferral(fileDao, item.path)
                        processed++
                    } else {
                        // A successful but text-empty OCR result is complete, not a retry.
                        clearCandidateDeferral(fileDao, item.path)
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

    private suspend fun deferCandidate(fileDao: FileDao, path: String) {
        updateRetryMarker(fileDao, path, System.currentTimeMillis() + OCR_RETRY_DELAY_MILLIS)
    }

    private suspend fun clearCandidateDeferral(fileDao: FileDao, path: String) {
        updateRetryMarker(fileDao, path, null)
    }

    private suspend fun updateRetryMarker(fileDao: FileDao, path: String, retryAt: Long?) {
        val entity = fileDao.getByPath(path) ?: return
        val tags = entity.tags.split(',').map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith(OCR_RETRY_TAG_PREFIX) }
            .toMutableList()
        if (retryAt != null) tags += "$OCR_RETRY_TAG_PREFIX$retryAt"
        fileDao.updateTagsByPath(path, tags.joinToString(","))
    }

    private const val OCR_BATCH_SIZE = 40
    private const val OCR_RETRY_DELAY_MILLIS = 6 * 60 * 60 * 1000L
    private const val OCR_RETRY_TAG_PREFIX = "ocr_retry_after:"
}
