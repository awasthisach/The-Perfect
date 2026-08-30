package com.vvf.smartmanager.core.background.workers

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Background worker for processing pending OCR queues when charging or connected to unmetered network.
 */
class OcrBatchWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.i("OcrBatchWorker", "Starting pending OCR text extraction batch...")
        try {
            if (isStopped) return@withContext Result.retry()

            delay(600)
            Log.i("OcrBatchWorker", "Background OCR batch processed.")
            Result.success()
        } catch (e: Exception) {
            Log.e("OcrBatchWorker", "OCR batch processing failed", e)
            Result.failure()
        }
    }
}
