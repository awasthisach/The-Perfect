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
        val entity = fileDao.getByPath(path) ?: return
        val previousAttempt = entity.tags.split(',')
            .firstOrNull { it.trim().startsWith(OCR_RETRY_TAG_PREFIX) }
            ?.trim()
            ?.substringAfter(':')
            ?.split(':')
            ?.getOrNull(1)
            ?.toIntOrNull() ?: 0
        val attempt = (previousAttempt + 1).coerceAtMost(MAX_RETRY_ATTEMPT)
        val retryAt = System.currentTimeMillis() + calculateOcrRetryDelayMillis(attempt)
        fileDao.setOcrRetryMarker(path, "$OCR_RETRY_TAG_PREFIX$retryAt:$attempt")
    }

    private suspend fun clearCandidateDeferral(fileDao: FileDao, path: String) {
        fileDao.setOcrRetryMarker(path, null)
    }

    private const val OCR_BATCH_SIZE = 40
    private const val MAX_RETRY_ATTEMPT = 10
    private const val OCR_RETRY_BASE_DELAY_MILLIS = 6 * 60 * 60 * 1000L
    private const val OCR_RETRY_MAX_DELAY_MILLIS = 7 * 24 * 60 * 60 * 1000L
    private const val OCR_RETRY_TAG_PREFIX = "ocr_retry_after:"
}

/** Exponential retry backoff lets a failing recent batch yield to older pending files. */
internal fun calculateOcrRetryDelayMillis(attempt: Int): Long {
    require(attempt >= 1) { "attempt must be positive" }
    val multiplier = 1L shl (attempt - 1).coerceAtMost(10)
    return (OCR_RETRY_BASE_DELAY_MILLIS * multiplier).coerceAtMost(OCR_RETRY_MAX_DELAY_MILLIS)
}
