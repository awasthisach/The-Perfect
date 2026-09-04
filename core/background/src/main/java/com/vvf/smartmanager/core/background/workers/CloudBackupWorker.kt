package com.vvf.smartmanager.core.background.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Scheduled encrypted cloud backup worker.
 *
 * Uses [CloudBackupRuntime] so Application can inject ArchiveService + upload path.
 * Never reports success for simulated work.
 */
class CloudBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i(TAG, "Cloud backup worker invoked")
        try {
            if (isStopped) return@withContext Result.retry()

            val includeVault = inputData.getBoolean("include_vault", true)
            when (val outcome = CloudBackupRuntime.run(includeVault)) {
                is CloudBackupOutcome.Completed -> {
                    Log.i(
                        TAG,
                        "Backup completed id=${outcome.backupId} size=${outcome.sizeBytes} uploaded=${outcome.uploaded}"
                    )
                    Result.success(
                        workDataOf(
                            "backup_id" to outcome.backupId,
                            "size_bytes" to outcome.sizeBytes,
                            "uploaded" to outcome.uploaded
                        )
                    )
                }
                is CloudBackupOutcome.RetryableFailure -> {
                    Log.w(TAG, "Retryable backup failure: ${outcome.reason}")
                    if (runAttemptCount < 2) Result.retry()
                    else Result.failure(workDataOf("reason" to outcome.reason))
                }
                is CloudBackupOutcome.PermanentFailure -> {
                    Log.e(TAG, "Permanent backup failure: ${outcome.reason}")
                    Result.failure(workDataOf("reason" to outcome.reason))
                }
                is CloudBackupOutcome.NotConfigured -> {
                    Log.w(TAG, "Backup not configured: ${outcome.reason}")
                    Result.failure(workDataOf("reason" to outcome.reason))
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cloud backup worker error", e)
            if (runAttemptCount < 2) Result.retry()
            else Result.failure(workDataOf("reason" to (e.message ?: "unknown")))
        }
    }

    companion object {
        private const val TAG = "CloudBackupWorker"
    }
}
