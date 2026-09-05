package com.vvf.smartmanager.core.background.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
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
                    Log.i(TAG, "OCR batch complete: processed=${outcome.processedCount}")
                    Result.success(workDataOf("processed_count" to outcome.processedCount))
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
        } catch (e: Exception) {
            Log.e(TAG, "OCR batch worker error", e)
            if (runAttemptCount < 3) Result.retry()
            else Result.failure(workDataOf("reason" to (e.message ?: "unknown")))
        }
    }

    companion object {
        private const val TAG = "OcrBatchWorker"
    }
}
