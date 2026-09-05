package com.vvf.smartmanager.core.background.workers

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

    fun configure(realBatchRunner: suspend () -> OcrBatchOutcome) {
        batchRunner = realBatchRunner
    }

    suspend fun runBatch(): OcrBatchOutcome {
        val configured = batchRunner
            ?: return OcrBatchOutcome.RetryableFailure("OCR batch dependencies are not ready")
        return configured()
    }
}
