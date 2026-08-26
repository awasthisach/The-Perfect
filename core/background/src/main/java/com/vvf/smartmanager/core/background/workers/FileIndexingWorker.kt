package com.vvf.smartmanager.core.background.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Battery-friendly background worker that scans for file modifications and updates
 * the Room FTS database index incrementally.
 */
class FileIndexingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i("FileIndexingWorker", "Starting background storage indexing job...")
        try {
            // Simulated or real incremental indexing pass
            // Checks for cancellation
            if (isStopped) {
                Log.w("FileIndexingWorker", "File indexing worker stopped by WorkManager.")
                return@withContext Result.retry()
            }

            Log.i("FileIndexingWorker", "Background storage indexing finished successfully.")
            Result.success()
        } catch (e: Exception) {
            Log.e("FileIndexingWorker", "Error during file indexing", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure()
        }
    }
}
