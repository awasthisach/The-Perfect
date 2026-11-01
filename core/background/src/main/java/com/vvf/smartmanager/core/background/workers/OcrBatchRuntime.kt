package com.vvf.smartmanager.core.background.workers

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * App-process bridge for OCR batch. WorkManager cannot inject OCR engine/queue;
 * VVFApplication configures the real batch runner at process start.
 */
sealed interface OcrBatchOutcome {
    data class Completed(val processedCount: Int) : OcrBatchOutcome
    data class RetryableFailure(val reason: String) : OcrBatchOutcome
    data class PermanentFailure(val reason: String) : OcrBatchOutcome
}

object OcrBatchRuntime {
    @Volatile
    private var batchRunner: (suspend () -> OcrBatchOutcome)? = null
    private val batchMutex = Mutex()

    fun configure(realBatchRunner: suspend () -> OcrBatchOutcome) {
        batchRunner = realBatchRunner
    }

    suspend fun runBatch(): OcrBatchOutcome = batchMutex.withLock {
        val configured = batchRunner
            ?: return@withLock OcrBatchOutcome.RetryableFailure("OCR batch dependencies are not ready")
        configured()
    }
}
