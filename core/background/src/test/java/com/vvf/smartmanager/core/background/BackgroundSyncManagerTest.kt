package com.vvf.smartmanager.core.background

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.vvf.smartmanager.core.background.workers.CloudBackupWorker
import com.vvf.smartmanager.core.background.workers.FileIndexingWorker
import com.vvf.smartmanager.core.background.workers.JunkScanWorker
import com.vvf.smartmanager.core.background.workers.OcrBatchWorker
import com.vvf.smartmanager.core.background.workers.OcrBatchOutcome
import com.vvf.smartmanager.core.background.workers.OcrBatchRuntime
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
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
        syncManager = BackgroundSyncManager(context)
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

}
