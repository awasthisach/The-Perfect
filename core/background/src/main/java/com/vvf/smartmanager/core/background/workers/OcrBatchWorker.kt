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

/**
 * Runs one bounded OCR pass. Remaining candidates are continued by a separate durable
 * WorkRequest so a large library cannot keep one Worker alive past WorkManager's execution limit.
 */
class OcrBatchWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "OCR batch worker invoked")
        try {
            currentCoroutineContext().ensureActive()
            if (isStopped) throw CancellationException("OCR batch worker stopped")

            when (val outcome = OcrBatchRuntime.runBatch()) {
                is OcrBatchOutcome.Completed -> {
                    Log.i(
                        TAG,
                        "OCR batch pass complete: processed=${outcome.processedCount}, " +
                            "deferred=${outcome.failedCount}, hasMore=${outcome.hasMoreCandidates}"
                    )
                    if (outcome.hasMoreCandidates) {
                        check(outcome.processedCount > 0 || outcome.failedCount > 0) {
                            "OCR batch reported remaining candidates without making progress"
                        }
                        // A healthy full batch is success. Queue the next bounded pass rather than
                        // consuming WorkManager's failure retry/backoff budget.
                        OcrBatchRuntime.scheduleContinuation()
                    }
                    Result.success(
                        workDataOf(
                            "processed_count" to outcome.processedCount,
                            "deferred_count" to outcome.failedCount
                        )
                    )
                }
                is OcrBatchOutcome.RetryableFailure -> {
                    Log.w(TAG, "Retryable OCR batch failure: ${outcome.reason}")
                    if (runAttemptCount < 3) Result.retry()
                    else Result.failure(workDataOf("reason" to outcome.reason))
                }
                is OcrBatchOutcome.PermanentFailure -> {
                    Log.e(TAG, "Permanent OCR batch failure: ${outcome.reason}")
                    Result.failure(workDataOf("reason" to outcome.reason))
                }
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "OCR batch worker error", error)
            if (runAttemptCount < 3) Result.retry()
            else Result.failure(workDataOf("reason" to (error.message ?: "unknown")))
        }
    }

    companion object {
        private const val TAG = "OcrBatchWorker"
    }
}
