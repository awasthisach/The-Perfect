package com.vvf.smartmanager.core.background.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

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
            if (isStopped) return@withContext Result.retry()

            when (val outcome = OcrBatchRuntime.runBatch()) {
                is OcrBatchOutcome.Completed -> {
                    Log.i(
                        TAG,
                        "OCR batch pass complete: processed=${outcome.processedCount}, " +
                            "deferred=${outcome.failedCount}, hasMore=${outcome.hasMoreCandidates}"
                    )
                    if (outcome.hasMoreCandidates) {
                        // A completed pass is not a failure. Schedule the next bounded pass
                        // separately so WorkManager's retry backoff and attempt count remain
                        // reserved for actual failures.
                        val nextBatch = OneTimeWorkRequestBuilder<OcrBatchWorker>().build()
                        WorkManager.getInstance(applicationContext).enqueueUniqueWork(
                            OCR_CONTINUATION_WORK,
                            ExistingWorkPolicy.APPEND_OR_REPLACE,
                            nextBatch
                        )
                        Result.success()
                    } else {
                        Result.success(
                            workDataOf(
                                "processed_count" to outcome.processedCount,
                                "deferred_count" to outcome.failedCount
                            )
                        )
                    }
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
        } catch (e: Exception) {
            Log.e(TAG, "OCR batch worker error", e)
            if (runAttemptCount < 3) Result.retry()
            else Result.failure(workDataOf("reason" to (e.message ?: "unknown")))
        }
    }

    companion object {
        private const val TAG = "OcrBatchWorker"
        private const val OCR_CONTINUATION_WORK = "ocr_batch_continuation"
    }
}
