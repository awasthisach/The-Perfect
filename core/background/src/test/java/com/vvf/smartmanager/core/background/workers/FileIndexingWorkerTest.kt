package com.vvf.smartmanager.core.background.workers

import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], manifest = Config.NONE)
class FileIndexingWorkerTest {
    private fun worker(attempts: Int = 0): FileIndexingWorker =
        TestListenableWorkerBuilder<FileIndexingWorker>(RuntimeEnvironment.getApplication())
            .setRunAttemptCount(attempts)
            .build()

    @After
    fun resetIndexer() {
        FileIndexingRuntime.configure { FileIndexingOutcome.RetryableFailure("test indexer reset") }
    }

    @Test
    fun completedScanReportsIndexedCount() = runBlocking {
        FileIndexingRuntime.configure { FileIndexingOutcome.Completed(7) }
        val result = worker().doWork()
        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(7, (result as ListenableWorker.Result.Success).outputData.getInt("indexed_count", -1))
    }

    @Test
    fun missingPermissionRemainsAnHonestDeferredSuccess() = runBlocking {
        FileIndexingRuntime.configure { FileIndexingOutcome.PermissionRequired("grant access") }
        val result = worker().doWork()
        assertTrue(result is ListenableWorker.Result.Success)
        val data = (result as ListenableWorker.Result.Success).outputData
        assertTrue(data.getBoolean("permission_required", false))
        assertEquals("grant access", data.getString("reason"))
    }

    @Test
    fun retryableFailureStillRetries() = runBlocking {
        FileIndexingRuntime.configure { FileIndexingOutcome.RetryableFailure("storage busy") }
        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
    }

    @Test
    fun exhaustedRetryableFailureStillFails() = runBlocking {
        FileIndexingRuntime.configure { FileIndexingOutcome.RetryableFailure("storage busy") }
        val result = worker(attempts = 3).doWork()
        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals("storage busy", (result as ListenableWorker.Result.Failure).outputData.getString("reason"))
    }

    @Test
    fun permanentFailureDoesNotRetry() = runBlocking {
        FileIndexingRuntime.configure { FileIndexingOutcome.PermanentFailure("invalid path") }
        assertTrue(worker().doWork() is ListenableWorker.Result.Failure)
    }

    @Test
    fun unexpectedFailureStillUsesRetryBudget() = runBlocking {
        FileIndexingRuntime.configure { throw IllegalStateException("indexer unavailable") }
        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
    }

    @Test
    fun exhaustedUnexpectedFailureStillFails() = runBlocking {
        FileIndexingRuntime.configure { throw IllegalStateException("indexer unavailable") }
        val result = worker(attempts = 3).doWork()
        assertTrue(result is ListenableWorker.Result.Failure)
        assertEquals("indexer unavailable", (result as ListenableWorker.Result.Failure).outputData.getString("reason"))
    }

    @Test
    fun cancellationPropagatesAtAnyRetryCount() = runBlocking {
        for (attempt in listOf(0, 3)) {
            FileIndexingRuntime.configure { throw CancellationException("cancelled-$attempt") }
            try {
                worker(attempts = attempt).doWork()
                fail("Cancellation must not become success, retry, or failure")
            } catch (cancelled: CancellationException) {
                assertEquals("cancelled-$attempt", cancelled.message)
            }
        }
    }
}
