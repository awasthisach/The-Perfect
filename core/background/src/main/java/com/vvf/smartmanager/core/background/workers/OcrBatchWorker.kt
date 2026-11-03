package com.vvf.smartmanager.core.background.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield

/**
 * Background OCR batch worker for pending document queues.
 * Delegates to [OcrBatchRuntime] configured by the application composition root.
 */
class OcrBatchWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "OCR batch worker invoked")
        try {
            runBoundedPasses()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Log.e(TAG, "OCR batch worker error", e)
            if (runAttemptCount < 3) Result.retry()
            else Result.failure(workDataOf("reason" to (e.message ?: "unknown")))
        }
    }

    private suspend fun runBoundedPasses(): Result {
        var totalProcessed = 0
        var totalDeferred = 0
        while (true) {
            currentCoroutineContext().ensureActive()
            if (isStopped) throw CancellationException("OCR batch worker stopped")

            when (val outcome = OcrBatchRuntime.runBatch()) {
                is OcrBatchOutcome.Completed -> {
                    Log.i(
                        TAG,
                        "OCR batch pass complete: processed=${outcome.processedCount}, " +
                            "deferred=${outcome.failedCount}, hasMore=${outcome.hasMoreCandidates}"
                    )
                    totalProcessed += outcome.processedCount
                    totalDeferred += outcome.failedCount
                    if (!outcome.hasMoreCandidates) {
                        return Result.success(
                            workDataOf(
                                "processed_count" to totalProcessed,
                                "deferred_count" to totalDeferred
                            )
                        )
                    }
                    check(outcome.processedCount > 0 || outcome.failedCount > 0) {
                        "OCR batch reported remaining candidates without making progress"
                    }
                    // A full successful pass is continuation, not a failed work attempt.
                    // Re-enter the runtime (which re-checks opt-in) without WorkManager backoff.
                    // Cancellation/WorkManager's execution limit can stop between bounded passes;
                    // per-file completion/defer markers preserve progress for the next execution.
                    yield()
                }
                is OcrBatchOutcome.RetryableFailure -> {
                    Log.w(TAG, "Retryable OCR batch failure: ${outcome.reason}")
                    return if (runAttemptCount < 3) Result.retry()
                    else Result.failure(workDataOf("reason" to outcome.reason))
                }
                is OcrBatchOutcome.PermanentFailure -> {
                    Log.e(TAG, "Permanent OCR batch failure: ${outcome.reason}")
                    return Result.failure(workDataOf("reason" to outcome.reason))
                }
            }
        }
    }

    companion object {
        private const val TAG = "OcrBatchWorker"
    }
}
