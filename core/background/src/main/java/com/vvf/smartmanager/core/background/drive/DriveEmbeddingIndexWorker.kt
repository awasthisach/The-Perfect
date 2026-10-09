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

/** Optional neural index worker; it never runs without explicit embedding consent and App Check support. */
class DriveEmbeddingIndexWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val coordinator = DriveEmbeddingRuntime.coordinatorOrNull() ?: return Result.success()
        return coordinator.runBatch().fold(
            onSuccess = { moreWork ->
                if (moreWork) enqueueContinuation(applicationContext)
                Result.success(workDataOf("more_work" to moreWork))
            },
            onFailure = {
                if (runAttemptCount < MAX_RETRIES) Result.retry()
                else Result.failure(workDataOf("error_code" to "embedding_index_failed"))
            }
        )
    }

    companion object {
        const val UNIQUE_WORK_NAME = "drive-semantic-embedding-index"
        private const val MAX_RETRIES = 5

        private fun request() =
            OneTimeWorkRequestBuilder<DriveEmbeddingIndexWorker>()
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
                request()
            )
        }

        private fun enqueueContinuation(context: Context) {
            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_WORK_NAME,
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                request()
            )
        }

        fun cancel(context: Context) {
            WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
        }
    }
}
