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
 *
 * Production policy: fail closed until OCR queue + engine are injected via
 * application composition. Simulated success is not allowed.
 */
class OcrBatchWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "OCR batch worker invoked")
        try {
            if (isStopped) return@withContext Result.retry()

            Log.w(
                TAG,
                "OCR batch pipeline not configured — fail closed. " +
                    "Inject pending OCR queue + OcrEngine before enabling periodic runs."
            )
            Result.failure(workDataOf("reason" to "ocr_batch_pipeline_not_configured"))
        } catch (e: Exception) {
            Log.e(TAG, "OCR batch worker error", e)
            Result.failure(workDataOf("reason" to (e.message ?: "unknown")))
        }
    }

    companion object {
        private const val TAG = "OcrBatchWorker"
    }
}
