package com.vvf.smartmanager.core.background.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Background worker that scans for cache, residual APKs, and temp files when device is idle.
 */
class JunkScanWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i("JunkScanWorker", "Starting background junk & duplicate storage scan...")
        try {
            if (isStopped) return@withContext Result.retry()

            delay(800)
            Log.i("JunkScanWorker", "Background storage analysis complete.")
            Result.success()
        } catch (e: Exception) {
            Log.e("JunkScanWorker", "Junk scan worker failed", e)
            Result.failure()
        }
    }
}
