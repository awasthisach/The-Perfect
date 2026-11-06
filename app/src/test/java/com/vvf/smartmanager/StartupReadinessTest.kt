package com.vvf.smartmanager

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class StartupReadinessTest {

    @Test
    fun slowRecoveryRemainsPendingUntilAllRecoveryStagesFinish() = runTest {
        val readiness = StartupReadiness()
        val releaseRecovery = CompletableDeferred<Unit>()
        val stages = mutableListOf<String>()

        readiness.start(backgroundScope) { markRecovering ->
            stages += "database"
            markRecovering()
            stages += "journal-recovery"
            releaseRecovery.await()
            stages += "metadata-reconciliation"
        }
        runCurrent()

        assertEquals(StartupState.Recovering, readiness.state.value)
        assertFalse(readiness.isReady())
        assertEquals(listOf("database", "journal-recovery"), stages)

        val waiter = async { readiness.awaitReady() }
        runCurrent()
        assertFalse("Sensitive operations must remain blocked while recovery is pending", waiter.isCompleted)

        releaseRecovery.complete(Unit)
        advanceUntilIdle()
        waiter.await()
        assertEquals(listOf("database", "journal-recovery", "metadata-reconciliation"), stages)
        assertEquals(StartupState.Ready, readiness.state.value)
    }

    @Test
    fun concurrentStartRequestsShareOneInitializationJob() = runTest {
        val readiness = StartupReadiness()
        val release = CompletableDeferred<Unit>()
        var invocationCount = 0
        val initializer: suspend (() -> Unit) -> Unit = { mark ->
            invocationCount++
            mark()
            release.await()
        }

        val first = readiness.start(backgroundScope, initializer)
        val second = readiness.start(backgroundScope, initializer)
        runCurrent()

        assertSame(first, second)
        assertEquals(1, invocationCount)
        assertEquals(StartupState.Recovering, readiness.state.value)

        release.complete(Unit)
        advanceUntilIdle()
        readiness.awaitReady()
        assertTrue(readiness.isReady())
    }

    @Test
    fun initializationFailureIsTerminalAndNeverPublishesReady() = runTest {
        val readiness = StartupReadiness()
        readiness.start(backgroundScope) { mark ->
            mark()
            throw IllegalStateException("journal recovery failed")
        }
        advanceUntilIdle()

        assertEquals(StartupState.Failed("journal recovery failed"), readiness.state.value)
        assertFalse(readiness.isReady())
        val failure = runCatching { readiness.awaitReady() }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
    }

    @Test
    fun cancellationPropagatesAndNewProcessCanRecoverIndependently() = runTest {
        val oldProcess = StartupReadiness()
        val oldJob = oldProcess.start(backgroundScope) { mark ->
            mark()
            kotlinx.coroutines.awaitCancellation()
        }
        runCurrent()
        oldJob.cancel()
        runCurrent()

        assertFalse(oldProcess.isReady())
        assertTrue(oldProcess.state.value is StartupState.Failed)
        val cancellation = runCatching { oldProcess.awaitReady() }.exceptionOrNull()
        assertTrue(cancellation is CancellationException)

        val restartedProcess = StartupReadiness()
        restartedProcess.start(backgroundScope) { mark ->
            mark()
            // Simulates idempotent journal recovery after process recreation.
        }
        advanceUntilIdle()
        restartedProcess.awaitReady()
        assertEquals(StartupState.Ready, restartedProcess.state.value)
        assertNotSame(oldProcess, restartedProcess)
    }

    @Test
    fun successfulStartupPublishesServicesOnlyAfterDatabaseAndRecoveryOrdering() = runTest {
        val readiness = StartupReadiness()
        val order = mutableListOf<String>()
        readiness.start(backgroundScope) { mark ->
            order += "database-migrations"
            mark()
            order += "vault-journal-recovery"
            order += "vaulted-metadata-reconciliation"
            order += "service-publication"
        }
        advanceUntilIdle()
        readiness.awaitReady()

        assertEquals(
            listOf(
                "database-migrations",
                "vault-journal-recovery",
                "vaulted-metadata-reconciliation",
                "service-publication"
            ),
            order
        )
        assertEquals(StartupState.Ready, readiness.state.value)
    }

    @Test
    fun cancellingOneWaiterDoesNotCancelSharedProcessInitialization() = runTest {
        val readiness = StartupReadiness()
        val release = CompletableDeferred<Unit>()
        readiness.start(backgroundScope) { mark ->
            mark()
            release.await()
        }
        runCurrent()

        val waiter = launch { readiness.awaitReady() }
        runCurrent()
        waiter.cancel()
        runCurrent()
        assertEquals(StartupState.Recovering, readiness.state.value)

        release.complete(Unit)
        advanceUntilIdle()
        readiness.awaitReady()
        assertTrue(readiness.isReady())
    }
}
