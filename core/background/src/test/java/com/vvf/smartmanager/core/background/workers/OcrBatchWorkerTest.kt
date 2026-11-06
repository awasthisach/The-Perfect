package com.vvf.smartmanager.core.background.workers

import androidx.work.ListenableWorker
import androidx.work.testing.TestListenableWorkerBuilder
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
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
class OcrBatchWorkerTest {
    private fun worker(attempts: Int = 0): OcrBatchWorker =
        TestListenableWorkerBuilder<OcrBatchWorker>(RuntimeEnvironment.getApplication())
            .setRunAttemptCount(attempts)
            .build()

    @Test
    fun healthyContinuationSchedulesFreshWorkAndDoesNotConsumeFailureRetries() = runBlocking {
        var passes = 0
        var scheduled = 0
        OcrBatchRuntime.configure(scheduleContinuation = { scheduled++ }) {
            passes++
            OcrBatchOutcome.Completed(40, hasMoreCandidates = true)
        }

        val result = worker(attempts = 12).doWork()
        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(1, passes)
        assertEquals(1, scheduled)
        assertEquals(40, (result as ListenableWorker.Result.Success).outputData.getInt("processed_count", -1))
    }

    @Test
    fun deferredFilesDoNotPreventTheNextEligibleWorkRequest() = runBlocking {
        var passes = 0
        var scheduled = 0
        OcrBatchRuntime.configure(scheduleContinuation = { scheduled++ }) {
            passes++
            if (passes == 1) OcrBatchOutcome.Completed(0, hasMoreCandidates = true, failedCount = 40)
            else OcrBatchOutcome.Completed(1, hasMoreCandidates = false)
        }

        val first = worker().doWork()
        assertTrue(first is ListenableWorker.Result.Success)
        assertEquals(1, passes)
        assertEquals(1, scheduled)
        assertEquals(40, (first as ListenableWorker.Result.Success).outputData.getInt("deferred_count", -1))

        val second = worker().doWork()
        assertTrue(second is ListenableWorker.Result.Success)
        assertEquals(2, passes)
        assertEquals(1, scheduled)
        assertEquals(1, (second as ListenableWorker.Result.Success).outputData.getInt("processed_count", -1))
    }

    @Test
    fun runtimeCanDisableOcrBeforeAQueuedContinuationRuns() = runBlocking {
        var enabled = true
        var scheduled = 0
        OcrBatchRuntime.configure(scheduleContinuation = { scheduled++ }) {
            if (!enabled) {
                OcrBatchOutcome.Completed(0)
            } else {
                enabled = false
                OcrBatchOutcome.Completed(40, hasMoreCandidates = true)
            }
        }

        assertTrue(worker().doWork() is ListenableWorker.Result.Success)
        assertEquals(1, scheduled)
        assertTrue(worker().doWork() is ListenableWorker.Result.Success)
        assertEquals(1, scheduled)
    }

    @Test
    fun actualTransientFailureStillRetries() = runBlocking {
        OcrBatchRuntime.configure { OcrBatchOutcome.RetryableFailure("database busy") }
        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
    }

    @Test
    fun exhaustedActualFailureStillFails() = runBlocking {
        OcrBatchRuntime.configure { OcrBatchOutcome.RetryableFailure("database busy") }
        assertTrue(worker(attempts = 3).doWork() is ListenableWorker.Result.Failure)
    }

    @Test
    fun permanentFailureDoesNotRetry() = runBlocking {
        OcrBatchRuntime.configure { OcrBatchOutcome.PermanentFailure("invalid configuration") }
        assertTrue(worker().doWork() is ListenableWorker.Result.Failure)
    }

    @Test
    fun cancellationDuringBatchPropagates() = runBlocking {
        OcrBatchRuntime.configure { throw CancellationException("cancelled during batch") }
        try {
            worker().doWork()
            fail("Cancellation must not become retry or success")
        } catch (cancelled: CancellationException) {
            assertEquals("cancelled during batch", cancelled.message)
        }
    }

    @Test
    fun noProgressOutcomeRetriesWithoutSchedulingContinuation() = runBlocking {
        var scheduled = 0
        OcrBatchRuntime.configure(scheduleContinuation = { scheduled++ }) {
            OcrBatchOutcome.Completed(0, hasMoreCandidates = true)
        }
        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
        assertEquals(0, scheduled)
    }
}
