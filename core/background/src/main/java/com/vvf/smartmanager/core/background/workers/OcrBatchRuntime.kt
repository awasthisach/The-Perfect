package com.vvf.smartmanager.core.background.workers

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * App-process bridge for OCR batch. WorkManager cannot inject OCR engine/queue;
 * VVFApplication configures the real batch runner and durable continuation scheduler.
 */
sealed interface OcrBatchOutcome {
    data class Completed(
        val processedCount: Int,
        val hasMoreCandidates: Boolean = false,
        val failedCount: Int = 0
    ) : OcrBatchOutcome
    data class RetryableFailure(val reason: String) : OcrBatchOutcome
    data class PermanentFailure(val reason: String) : OcrBatchOutcome
}

object OcrBatchRuntime {
    @Volatile
    private var batchRunner: (suspend () -> OcrBatchOutcome)? = null

    @Volatile
    private var continuationScheduler: (suspend () -> Unit)? = null

    private val batchMutex = Mutex()

    fun configure(
        scheduleContinuation: (suspend () -> Unit)? = null,
        realBatchRunner: suspend () -> OcrBatchOutcome
    ) {
        continuationScheduler = scheduleContinuation
        batchRunner = realBatchRunner
    }

    suspend fun runBatch(): OcrBatchOutcome = batchMutex.withLock {
        val configured = batchRunner
            ?: return@withLock OcrBatchOutcome.RetryableFailure("OCR batch dependencies are not ready")
        configured()
    }

    /**
     * Schedules a separate durable WorkRequest after one bounded pass.
     * Missing scheduler wiring is a real failure; never silently drop remaining candidates.
     */
    suspend fun scheduleContinuation() {
        val configured = continuationScheduler
            ?: throw IllegalStateException("OCR continuation scheduler is not configured")
        configured()
    }
}
