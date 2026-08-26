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
import com.vvf.smartmanager.core.background.workers.OcrBatchWorker
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.emptyFlow
import java.util.concurrent.TimeUnit

/**
 * Central WorkManager Orchestrator for battery-friendly, offline-first background operations.
 * Safely handles test environments where WorkManager is not pre-configured.
 */
class BackgroundSyncManager(private val context: Context) {

    private val workManager: WorkManager? by lazy {
        try {
            WorkManager.getInstance(context)
        } catch (_: Throwable) {
            null
        }
    }

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
            ExistingWorkPolicy.REPLACE,
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
     * Trigger immediate OCR batch processing pass.
     */
    fun triggerImmediateOcrBatch() {
        val wm = workManager ?: return
        val request = OneTimeWorkRequestBuilder<OcrBatchWorker>()
            .addTag("immediate_ocr")
            .build()

        wm.enqueue(request)
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
