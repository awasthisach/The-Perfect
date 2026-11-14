package com.vvf.smartmanager.core.background

import android.content.Context
import androidx.work.Constraints
import androidx.work.Data
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.vvf.smartmanager.core.background.workers.CloudBackupWorker
import com.vvf.smartmanager.core.background.workers.FileIndexingWorker
import com.vvf.smartmanager.core.background.workers.JunkScanWorker
import com.vvf.smartmanager.core.background.workers.IndexingAndOcrCoordinatorWorker
import com.vvf.smartmanager.core.background.workers.OcrBatchWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.runInterruptible
import java.util.concurrent.TimeUnit

/**
 * Central WorkManager Orchestrator for battery-friendly, offline-first background operations.
 * Safely handles test environments where WorkManager is not pre-configured.
 */
class BackgroundSyncManager(
    private val context: Context,
    private val workManagerProvider: () -> WorkManager? = {
        try {
            WorkManager.getInstance(context)
        } catch (_: Exception) {
            null
        }
    }
) {

    private val workManager: WorkManager? by lazy(workManagerProvider)

    /**
     * Schedule periodic incremental storage indexing.
     */
    fun schedulePeriodicIndexing(intervalHours: Long = 6L) {
        val wm = workManager ?: return
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<FileIndexingWorker>(
            intervalHours, TimeUnit.HOURS,
            15, TimeUnit.MINUTES // flex interval
        )
            .setConstraints(constraints)
            .addTag(BackgroundConstants.UNIQUE_STORAGE_ANALYZER_WORK)
            .build()

        wm.enqueueUniquePeriodicWork(
            BackgroundConstants.UNIQUE_STORAGE_ANALYZER_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Schedule scheduled cloud backups with network and battery constraints.
     */
    fun schedulePeriodicCloudBackup(
        intervalHours: Long = 24L,
        wifiOnly: Boolean = true,
        chargingOnly: Boolean = true,
        includeVault: Boolean = true
    ) {
        val wm = workManager ?: return
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
            .setRequiresCharging(chargingOnly)
            .setRequiresBatteryNotLow(true)
            .build()

        val inputData = Data.Builder()
            .putBoolean("include_vault", includeVault)
            .build()

        val request = PeriodicWorkRequestBuilder<CloudBackupWorker>(
            intervalHours, TimeUnit.HOURS,
            30, TimeUnit.MINUTES
        )
            .setConstraints(constraints)
            .setInputData(inputData)
            .addTag(BackgroundConstants.UNIQUE_CLOUD_SYNC_WORK)
            .build()

        wm.enqueueUniquePeriodicWork(
            BackgroundConstants.UNIQUE_CLOUD_SYNC_WORK,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    /**
     * Schedule periodic junk and duplicate cleaner analyzer.
     */
    fun schedulePeriodicJunkScan(intervalHours: Long = 12L) {
        val wm = workManager ?: return
        val constraints = Constraints.Builder()
            .setRequiresDeviceIdle(true)
            .setRequiresBatteryNotLow(true)
            .build()

        val request = PeriodicWorkRequestBuilder<JunkScanWorker>(
            intervalHours, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag(BackgroundConstants.UNIQUE_CLEANER_ANALYSIS_WORK)
            .build()

        wm.enqueueUniquePeriodicWork(
            BackgroundConstants.UNIQUE_CLEANER_ANALYSIS_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Trigger immediate one-time storage indexing pass.
     */
    fun triggerImmediateIndexing() {
        val wm = workManager ?: return
        val request = OneTimeWorkRequestBuilder<FileIndexingWorker>()
            .addTag("immediate_indexing")
            .build()

        wm.enqueueUniqueWork(
            "immediate_indexing",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    /**
     * Run a fresh storage scan and then independently enqueue OCR.
     * The coordinator schedules OCR even if indexing fails, so existing indexed files remain
     * eligible for OCR. APPEND_OR_REPLACE preserves a permission-rescan request if older work
     * is running or in backoff instead of silently dropping it with KEEP.
     */
    fun triggerImmediateIndexingThenOcr() {
        val wm = workManager ?: return
        val request = OneTimeWorkRequestBuilder<IndexingAndOcrCoordinatorWorker>()
            .addTag("immediate_indexing_then_ocr")
            .build()
        wm.enqueueUniqueWork(
            "immediate_indexing_then_ocr",
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
    }

    /**
     * Trigger immediate one-time cloud backup snapshot.
     */
    fun triggerImmediateBackup(includeVault: Boolean = true) {
        val wm = workManager ?: return
        val inputData = Data.Builder()
            .putBoolean("include_vault", includeVault)
            .build()

        val request = OneTimeWorkRequestBuilder<CloudBackupWorker>()
            .setInputData(inputData)
            .addTag("immediate_backup")
            .build()

        wm.enqueueUniqueWork(
            "immediate_backup",
            ExistingWorkPolicy.REPLACE,
            request
        )
    }

    /**
     * Schedule OCR retries and remaining candidate batches. The runtime checks the user's
     * opt-in setting before touching any file, so this schedule is safe while OCR is disabled.
     */
    fun schedulePeriodicOcrBatch(intervalHours: Long = 6L) {
        val wm = workManager ?: return
        val constraints = Constraints.Builder()
            .setRequiresBatteryNotLow(true)
            .setRequiresStorageNotLow(true)
            .build()
        val request = PeriodicWorkRequestBuilder<OcrBatchWorker>(
            intervalHours, TimeUnit.HOURS,
            1, TimeUnit.HOURS
        )
            .setConstraints(constraints)
            .addTag(BackgroundConstants.UNIQUE_OCR_BATCH_WORK)
            .build()
        wm.enqueueUniquePeriodicWork(
            BackgroundConstants.UNIQUE_OCR_BATCH_WORK,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    /**
     * Trigger immediate OCR batch processing pass.
     */
    suspend fun triggerImmediateOcrBatch() {
        val wm = checkNotNull(workManager) {
            "WorkManager is unavailable; refusing to drop an OCR batch or continuation"
        }
        val request = OneTimeWorkRequestBuilder<OcrBatchWorker>()
            .addTag("immediate_ocr")
            .build()

        // Await WorkManager's durable enqueue operation before the current worker can report
        // success. If persistence fails or times out, the caller retries rather than losing work.
        val operation = wm.enqueueUniqueWork(
            BackgroundConstants.UNIQUE_IMMEDIATE_OCR_BATCH_WORK,
            ExistingWorkPolicy.APPEND_OR_REPLACE,
            request
        )
        runInterruptible(Dispatchers.IO) {
            operation.result.get(10, TimeUnit.SECONDS)
        }
    }

    /**
     * Stop all scheduled and queued OCR work when the user revokes OCR consent.
     * Cancelling the unique one-time chain also interrupts an active CoroutineWorker.
     *
     * Official WorkManager API: cancelUniqueWork(uniqueWorkName) cancels unfinished work
     * in the named unique chain for both one-time and periodic unique work.
     * There is no cancelUniquePeriodicWork method.
     * @see <a href="https://developer.android.com/reference/androidx/work/WorkManager#cancelUniqueWork(java.lang.String)">WorkManager.cancelUniqueWork</a>
     */
    fun cancelOcrBatchWork() {
        val wm = workManager ?: return
        wm.cancelUniqueWork(BackgroundConstants.UNIQUE_IMMEDIATE_OCR_BATCH_WORK)
        wm.cancelUniqueWork(BackgroundConstants.UNIQUE_OCR_BATCH_WORK)
    }

    /**
     * Observe status of cloud sync jobs.
     */
    fun getCloudSyncWorkInfos(): Flow<List<WorkInfo>> {
        return workManager?.getWorkInfosByTagFlow(BackgroundConstants.UNIQUE_CLOUD_SYNC_WORK) ?: emptyFlow()
    }

    /**
     * Cancel all background tasks.
     */
    fun cancelAllScheduledWork() {
        workManager?.cancelAllWork()
    }
}
