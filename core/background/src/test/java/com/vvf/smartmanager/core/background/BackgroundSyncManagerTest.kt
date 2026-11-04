package com.vvf.smartmanager.core.background

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import com.vvf.smartmanager.core.background.workers.CloudBackupWorker
import com.vvf.smartmanager.core.background.workers.FileIndexingWorker
import com.vvf.smartmanager.core.background.workers.FileIndexingRuntime
import com.vvf.smartmanager.core.background.workers.JunkScanWorker
import com.vvf.smartmanager.core.background.workers.OcrBatchWorker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.fail
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

        syncManager.triggerImmediateIndexing()
        syncManager.triggerImmediateBackup(includeVault = false)
        syncManager.triggerImmediateOcrBatch()

        syncManager.cancelAllScheduledWork()
    }
    @Test
    fun fileIndexingWorkerPropagatesCancellationInsteadOfRetrying() = runBlocking {
        FileIndexingRuntime.configure {
            throw CancellationException("test cancellation")
        }
        val worker = TestListenableWorkerBuilder<FileIndexingWorker>(context).build()

        try {
            worker.doWork()
            fail("Expected CancellationException to propagate")
        } catch (cancelled: CancellationException) {
            assertEquals("test cancellation", cancelled.message)
        }
    }

}
