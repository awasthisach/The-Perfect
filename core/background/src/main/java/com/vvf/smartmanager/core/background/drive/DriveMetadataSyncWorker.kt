package com.vvf.smartmanager.core.background.drive

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import java.util.concurrent.TimeUnit

/**
 * Executes one 50-file page and chains the next page. Tokens and file contents are never passed
 * through WorkManager input/output data.
 */
class DriveMetadataSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val coordinator = DriveSyncRuntime.coordinatorOrNull() ?: return Result.retry()
        val batch = coordinator.runOneBatch()
        return batch.fold(
            onSuccess = { moreWork ->
                if (moreWork) enqueueContinuation(applicationContext)
                Result.success(workDataOf("more_work" to moreWork))
            },
            onFailure = {
                if (runAttemptCount < MAX_RETRIES) {
                    Result.retry()
                } else {
                    Result.failure(workDataOf("error_code" to "drive_metadata_sync_failed"))
                }
            }
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "drive-semantic-metadata-sync"
        private const val MAX_RETRIES = 5

        private fun buildRequest() =
            OneTimeWorkRequestBuilder<DriveMetadataSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                buildRequest()
            )
        }

        private fun enqueueContinuation(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                buildRequest()
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
