package com.vvf.smartmanager.core.background

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ExistingWorkPolicy
import androidx.work.ListenableWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.testing.TestListenableWorkerBuilder
import androidx.work.testing.WorkManagerTestInitHelper
import com.vvf.smartmanager.core.background.workers.CloudBackupWorker
import com.vvf.smartmanager.core.background.workers.FileIndexingWorker
import com.vvf.smartmanager.core.background.workers.FileIndexingOutcome
import com.vvf.smartmanager.core.background.workers.FileIndexingRuntime
import com.vvf.smartmanager.core.background.workers.IndexingAndOcrCoordinatorWorker
import com.vvf.smartmanager.core.background.workers.JunkScanWorker
import com.vvf.smartmanager.core.background.workers.OcrBatchWorker
import com.vvf.smartmanager.core.background.workers.OcrBatchOutcome
import com.vvf.smartmanager.core.background.workers.OcrBatchRuntime
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.TimeUnit
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class BackgroundSyncManagerTest {

    private lateinit var context: Context
    private lateinit var syncManager: BackgroundSyncManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        WorkManagerTestInitHelper.initializeTestWorkManager(context)
        FileIndexingRuntime.configure { FileIndexingOutcome.Completed(0) }
        OcrBatchRuntime.configure { OcrBatchOutcome.Completed(0, hasMoreCandidates = false, failedCount = 0) }
        syncManager = BackgroundSyncManager(context)
    }

    @After
    fun tearDown() {
        WorkManagerTestInitHelper.closeWorkDatabase()
    }


    private suspend fun awaitOcrWorkToFinish() {
        val workManager = WorkManager.getInstance(context)
        val infos = workManager
            .getWorkInfosForUniqueWork(BackgroundConstants.UNIQUE_IMMEDIATE_OCR_BATCH_WORK)
            .get()
        assertEquals(1, infos.size)
        withTimeout(10_000) {
            while (true) {
                val info = workManager.getWorkInfoById(infos.single().id).get()
                if (info?.state?.isFinished == true) {
                    assertEquals(androidx.work.WorkInfo.State.SUCCEEDED, info.state)
                    return@withTimeout
                }
                delay(10)
            }
        }
    }

    @Test
    fun testBackgroundSyncManagerInitialization() {
        assertNotNull(syncManager)
    }

    @Test
    fun testScheduleWorkManagerTasks() {
        // Verify scheduling calls run safely without throwing exceptions
        syncManager.schedulePeriodicIndexing(intervalHours = 6)
        syncManager.schedulePeriodicCloudBackup(intervalHours = 24, wifiOnly = true, chargingOnly = true)
        syncManager.schedulePeriodicJunkScan(intervalHours = 12)
        syncManager.schedulePeriodicOcrBatch(intervalHours = 6)

        syncManager.triggerImmediateIndexing()
        syncManager.triggerImmediateBackup(includeVault = false)
        syncManager.triggerImmediateOcrBatch()
        syncManager.triggerImmediateIndexingThenOcr()

        syncManager.cancelAllScheduledWork()
    }
    @Test
    fun ocrWorkerContinuesBoundedPassesWithoutRetryBackoff() = runBlocking {
        var passes = 0
        OcrBatchRuntime.configure {
            passes++
            if (passes == 1) {
                OcrBatchOutcome.Completed(38, hasMoreCandidates = true, failedCount = 2)
            } else {
                OcrBatchOutcome.Completed(7, hasMoreCandidates = false, failedCount = 1)
            }
        }

        val worker = TestListenableWorkerBuilder<OcrBatchWorker>(context).build()
        val result = worker.doWork()

        assertTrue("Expected success result, got: $result", result is ListenableWorker.Result.Success)
        assertEquals(2, passes)
        assertEquals(45, (result as ListenableWorker.Result.Success).outputData.getInt("processed_count", -1))
        assertEquals(3, (result as ListenableWorker.Result.Success).outputData.getInt("deferred_count", -1))
    }

    @Test
    fun ocrWorkerCompletesWhenNoFurtherCandidatesRemain() = runBlocking {
        OcrBatchRuntime.configure {
            OcrBatchOutcome.Completed(
                processedCount = 7,
                hasMoreCandidates = false,
                failedCount = 1
            )
        }

        val worker = TestListenableWorkerBuilder<OcrBatchWorker>(context).build()
        val result = worker.doWork()

        assertTrue("Expected success result, got: $result", result is ListenableWorker.Result.Success)
        assertEquals(7, (result as ListenableWorker.Result.Success).outputData.getInt("processed_count", -1))
        assertEquals(1, (result as ListenableWorker.Result.Success).outputData.getInt("deferred_count", -1))
    }

    @Test
    fun coordinatorSchedulesOcrEvenWhenIndexingFailsPermanently() = runBlocking {
        FileIndexingRuntime.configure { FileIndexingOutcome.PermanentFailure("scan failed") }
        var ocrPasses = 0
        OcrBatchRuntime.configure {
            ocrPasses++
            OcrBatchOutcome.Completed(2, hasMoreCandidates = false, failedCount = 0)
        }

        val worker = TestListenableWorkerBuilder<IndexingAndOcrCoordinatorWorker>(context).build()
        val result = worker.doWork()

        assertTrue("Expected indexing failure, got: $result", result is ListenableWorker.Result.Failure)
        val queuedOcr = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(BackgroundConstants.UNIQUE_IMMEDIATE_OCR_BATCH_WORK).get()
        assertEquals(1, queuedOcr.size)
        awaitOcrWorkToFinish()
        assertEquals(1, ocrPasses)
    }

    @Test
    fun coordinatorSchedulesOcrAfterSuccessfulIndexing() = runBlocking {
        FileIndexingRuntime.configure { FileIndexingOutcome.Completed(3) }
        var ocrPasses = 0
        OcrBatchRuntime.configure {
            ocrPasses++
            OcrBatchOutcome.Completed(1, hasMoreCandidates = false, failedCount = 0)
        }

        val worker = TestListenableWorkerBuilder<IndexingAndOcrCoordinatorWorker>(context).build()
        val result = worker.doWork()

        assertTrue("Expected success result, got: $result", result is ListenableWorker.Result.Success)
        val queuedOcr = WorkManager.getInstance(context)
            .getWorkInfosForUniqueWork(BackgroundConstants.UNIQUE_IMMEDIATE_OCR_BATCH_WORK).get()
        assertEquals(1, queuedOcr.size)
        ocrPassesForAwait = ocrPasses
        awaitOcrWorkToFinish(1)
        assertEquals(1, ocrPasses)
        assertEquals(3, (result as ListenableWorker.Result.Success).outputData.getInt("indexed_count", -1))
    }

    @Test
    fun permissionRescanRequestIsAppendedInsteadOfDroppedWhenOldWorkIsPending() = runBlocking {
        val workManager = WorkManager.getInstance(context)
        val existing = OneTimeWorkRequestBuilder<IndexingAndOcrCoordinatorWorker>()
            .setInitialDelay(1, TimeUnit.DAYS)
            .build()
        workManager.enqueueUniqueWork(
            "immediate_indexing_then_ocr",
            ExistingWorkPolicy.KEEP,
            existing
        ).result.get()

        syncManager.triggerImmediateIndexingThenOcr()

        val queued = workManager.getWorkInfosForUniqueWork("immediate_indexing_then_ocr").get()
        assertEquals(2, queued.size)
    }

}
