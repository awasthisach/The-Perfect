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

/** Bounded local extraction worker; text and bytes never leave app-private storage. */
class DriveContentIndexWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val coordinator = DriveContentIndexRuntime.coordinatorOrNull() ?: return Result.retry()
        return coordinator.runBatch().fold(
            onSuccess = { moreWork ->
                if (moreWork) enqueueContinuation(applicationContext)
                Result.success(workDataOf("more_work" to moreWork))
            },
            onFailure = {
                if (runAttemptCount < MAX_RETRIES) Result.retry()
                else Result.failure(workDataOf("error_code" to "drive_content_index_failed"))
            }
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "drive-semantic-content-index"
        private const val MAX_RETRIES = 5

        private fun buildRequest() =
            OneTimeWorkRequestBuilder<DriveContentIndexWorker>()
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
