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
    fun healthyContinuationDoesNotConsumeFailureRetries() = runBlocking {
        var passes = 0
        OcrBatchRuntime.configure {
            passes++
            OcrBatchOutcome.Completed(if (passes < 3) 40 else 3, hasMoreCandidates = passes < 3)
        }
        val result = worker(attempts = 12).doWork()
        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(3, passes)
        assertEquals(83, (result as ListenableWorker.Result.Success).outputData.getInt("processed_count", -1))
    }

    @Test
    fun deferredFilesDoNotPreventTheNextEligiblePass() = runBlocking {
        var passes = 0
        OcrBatchRuntime.configure {
            passes++
            if (passes == 1) OcrBatchOutcome.Completed(0, true, 40)
            else OcrBatchOutcome.Completed(1, false, 0)
        }
        val result = worker().doWork()
        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(2, passes)
        assertEquals(1, (result as ListenableWorker.Result.Success).outputData.getInt("processed_count", -1))
        assertEquals(40, (result as ListenableWorker.Result.Success).outputData.getInt("deferred_count", -1))
    }

    @Test
    fun runtimeCanDisableOcrBetweenPasses() = runBlocking {
        var passes = 0
        OcrBatchRuntime.configure {
            passes++
            if (passes == 1) OcrBatchOutcome.Completed(40, true)
            else OcrBatchOutcome.Completed(0)
        }
        val result = worker().doWork()
        assertTrue(result is ListenableWorker.Result.Success)
        assertEquals(2, passes)
        assertEquals(40, (result as ListenableWorker.Result.Success).outputData.getInt("processed_count", -1))
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
    fun cancellationBetweenPassesPropagates() = runBlocking {
        var passes = 0
        OcrBatchRuntime.configure {
            passes++
            if (passes == 1) OcrBatchOutcome.Completed(40, true)
            else throw CancellationException("cancelled between batches")
        }
        try {
            worker().doWork()
            fail("Cancellation must not become retry or success")
        } catch (_: CancellationException) {
            assertEquals(2, passes)
        }
    }

    @Test
    fun anInconsistentNoProgressOutcomeCannotSpinForever() = runBlocking {
        var passes = 0
        OcrBatchRuntime.configure {
            passes++
            OcrBatchOutcome.Completed(0, true)
        }
        assertEquals(ListenableWorker.Result.retry(), worker().doWork())
        assertEquals(1, passes)
    }
}
