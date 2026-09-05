package com.vvf.smartmanager.core.background.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Background junk / residual file scan worker.
 * Delegates to [JunkScanRuntime] configured by the application composition root.
 */
class JunkScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "Junk scan worker invoked")
        try {
            if (isStopped) return@withContext Result.retry()

            when (val outcome = JunkScanRuntime.scan()) {
                is JunkScanOutcome.Completed -> {
                    Log.i(
                        TAG,
                        "Junk scan complete: scanned=${outcome.totalScanned} " +
                            "junkItems=${outcome.junkItemCount} wastedBytes=${outcome.wastedBytes}"
                    )
                    Result.success(
                        workDataOf(
                            "total_scanned" to outcome.totalScanned,
                            "junk_item_count" to outcome.junkItemCount,
                            "wasted_bytes" to outcome.wastedBytes
                        )
                    )
                }
                is JunkScanOutcome.RetryableFailure -> {
                    Log.w(TAG, "Retryable junk scan failure: ${outcome.reason}")
                    if (runAttemptCount < 3) Result.retry()
                    else Result.failure(workDataOf("reason" to outcome.reason))
                }
                is JunkScanOutcome.PermanentFailure -> {
                    Log.e(TAG, "Permanent junk scan failure: ${outcome.reason}")
                    Result.failure(workDataOf("reason" to outcome.reason))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Junk scan worker error", e)
            if (runAttemptCount < 3) Result.retry()
            else Result.failure(workDataOf("reason" to (e.message ?: "unknown")))
        }
    }

    companion object {
        private const val TAG = "JunkScanWorker"
    }
}
