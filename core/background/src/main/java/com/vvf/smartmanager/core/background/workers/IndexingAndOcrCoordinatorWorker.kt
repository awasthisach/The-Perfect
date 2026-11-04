package com.vvf.smartmanager.core.background.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.vvf.smartmanager.core.background.BackgroundConstants
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Runs a fresh primary-storage scan and schedules OCR independently of the scan outcome.
 *
 * OCR must still process already-indexed eligible files when a new scan fails or storage
 * permission is unavailable. The OCR worker re-queries the database after this coordinator
 * finishes, so newly indexed files are included as well.
 */
class IndexingAndOcrCoordinatorWorker(
    appContext: Context,
    workerParams: androidx.work.WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val indexingOutcome = try {
            FileIndexingRuntime.index()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            FileIndexingOutcome.RetryableFailure(error.message ?: "unexpected indexing error")
        }

        if (isStopped) throw CancellationException("Indexing coordinator stopped")

        try {
            val ocrRequest = OneTimeWorkRequestBuilder<OcrBatchWorker>()
                .addTag("immediate_ocr")
                .build()
            WorkManager.getInstance(applicationContext)
                .enqueueUniqueWork(
                    IMMEDIATE_OCR_WORK,
                    ExistingWorkPolicy.APPEND_OR_REPLACE,
                    ocrRequest
                )
                .result
                .get()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Log.e(TAG, "Unable to enqueue OCR after indexing pass", error)
            return@withContext if (runAttemptCount < MAX_ATTEMPTS) {
                Result.retry()
            } else {
                Result.failure(workDataOf("reason" to "OCR scheduling failed: ${error.message ?: "unknown"}"))
            }
        }

        when (indexingOutcome) {
            is FileIndexingOutcome.Completed -> {
                Result.success(workDataOf("indexed_count" to indexingOutcome.indexedCount))
            }
            is FileIndexingOutcome.PermissionRequired -> {
                Result.success(
                    workDataOf(
                        "permission_required" to true,
                        "reason" to indexingOutcome.reason
                    )
                )
            }
            is FileIndexingOutcome.RetryableFailure -> {
                Log.w(TAG, "Retryable indexing failure; OCR was still scheduled: ${indexingOutcome.reason}")
                if (runAttemptCount < MAX_ATTEMPTS) Result.retry()
                else Result.failure(workDataOf("reason" to indexingOutcome.reason))
            }
            is FileIndexingOutcome.PermanentFailure -> {
                Log.e(TAG, "Permanent indexing failure; OCR was still scheduled: ${indexingOutcome.reason}")
                Result.failure(workDataOf("reason" to indexingOutcome.reason))
            }
        }
    }

    companion object {
        private const val TAG = "IndexingOcrCoordinator"
        private const val IMMEDIATE_OCR_WORK = BackgroundConstants.UNIQUE_IMMEDIATE_OCR_BATCH_WORK
        private const val MAX_ATTEMPTS = 3
    }
}
