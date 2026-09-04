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
 *
 * Production policy: fail closed until the cleaner domain use-case is injected
 * and can report real findings. Simulated success is not allowed.
 */
class JunkScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "Junk scan worker invoked")
        try {
            if (isStopped) return@withContext Result.retry()

            Log.w(
                TAG,
                "Junk scan pipeline not configured — fail closed. " +
                    "Inject CleanerUseCase / storage analysis before enabling periodic runs."
            )
            Result.failure(workDataOf("reason" to "junk_scan_pipeline_not_configured"))
        } catch (e: Exception) {
            Log.e(TAG, "Junk scan worker error", e)
            Result.failure(workDataOf("reason" to (e.message ?: "unknown")))
        }
    }

    companion object {
        private const val TAG = "JunkScanWorker"
    }
}
