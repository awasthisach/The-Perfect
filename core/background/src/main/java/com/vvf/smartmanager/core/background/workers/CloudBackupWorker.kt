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
 * Production policy: fail closed until a real backup orchestrator is injected via
 * [BackgroundSyncManager] / application composition. Simulated delays must never
 * be reported as successful backups.
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
            // Real pipeline (upload + integrity + durable state) is not yet wired
            // into this worker. Report honest failure so operators and UI do not
            // assume a backup completed.
            Log.w(
                TAG,
                "Cloud backup pipeline not configured — fail closed " +
                    "(includeVault=$includeVault). Wire real orchestrator before enabling."
            )
            Result.failure(
                workDataOf(
                    "reason" to "cloud_backup_pipeline_not_configured",
                    "include_vault" to includeVault
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Cloud backup worker error", e)
            if (runAttemptCount < 2) Result.retry() else Result.failure(
                workDataOf("reason" to (e.message ?: "unknown"))
            )
        }
    }

    companion object {
        private const val TAG = "CloudBackupWorker"
    }
}
