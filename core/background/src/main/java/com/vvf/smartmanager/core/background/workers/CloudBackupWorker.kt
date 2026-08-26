package com.vvf.smartmanager.core.background.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Background worker that triggers automated encrypted backups when connected to Wi-Fi and charging.
 */
class CloudBackupWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i("CloudBackupWorker", "Starting scheduled background cloud backup...")
        try {
            if (isStopped) return@withContext Result.retry()

            val includeVault = inputData.getBoolean("include_vault", true)
            // Simulated backup synchronization
            delay(1000)

            Log.i("CloudBackupWorker", "Scheduled cloud backup completed (Vault Included: $includeVault).")
            Result.success()
        } catch (e: Exception) {
            Log.e("CloudBackupWorker", "Scheduled cloud backup failed", e)
            if (runAttemptCount < 2) Result.retry() else Result.failure()
        }
    }
}
