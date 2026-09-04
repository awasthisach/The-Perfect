package com.vvf.smartmanager.core.background.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.workDataOf
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
            if (isStopped) {
                Log.w("FileIndexingWorker", "File indexing worker stopped by WorkManager.")
                return@withContext Result.retry()
            }

            when (val outcome = FileIndexingRuntime.index()) {
                is FileIndexingOutcome.Completed -> {
                    Log.i("FileIndexingWorker", "Indexed ${outcome.indexedCount} storage item(s).")
                    Result.success(workDataOf("indexed_count" to outcome.indexedCount))
                }
                is FileIndexingOutcome.PermissionRequired -> {
                    // WorkManager cannot grant access. Finish honestly and retry on the next
                    // scheduled pass after the user returns from the Settings permission flow.
                    Log.i("FileIndexingWorker", "Indexing deferred until storage access is granted: ${outcome.reason}")
                    Result.success(workDataOf("permission_required" to true, "reason" to outcome.reason))
                }
                is FileIndexingOutcome.RetryableFailure -> {
                    Log.w("FileIndexingWorker", "Retryable indexing failure: ${outcome.reason}")
                    if (runAttemptCount < 3) Result.retry() else Result.failure(workDataOf("reason" to outcome.reason))
                }
                is FileIndexingOutcome.PermanentFailure -> {
                    Log.e("FileIndexingWorker", "Permanent indexing failure: ${outcome.reason}")
                    Result.failure(workDataOf("reason" to outcome.reason))
                }
            }
        } catch (e: Exception) {
            Log.e("FileIndexingWorker", "Unhandled error during file indexing", e)
            if (runAttemptCount < 3) Result.retry() else Result.failure(workDataOf("reason" to (e.message ?: "unknown error")))
        }
    }
}
