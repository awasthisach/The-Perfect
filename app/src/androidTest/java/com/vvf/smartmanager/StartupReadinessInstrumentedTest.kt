package com.vvf.smartmanager

import android.os.Handler
import android.os.Looper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

@RunWith(AndroidJUnit4::class)
class StartupReadinessInstrumentedTest {

    @Test
    fun delayedRecoveryDoesNotBlockMainLooperAndKeepsBarrierClosed() = runBlocking {
        val readiness = StartupReadiness()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        val finishRecovery = CompletableDeferred<Unit>()
        try {
            readiness.start(scope) { markRecovering ->
                markRecovering()
                delay(750)
                finishRecovery.await()
            }

            // A main-looper callback must execute while the injected recovery is still pending.
            val mainLooperProbe = CountDownLatch(1)
            Handler(Looper.getMainLooper()).post { mainLooperProbe.countDown() }
            assertTrue(
                "Main looper must remain responsive during slow recovery",
                mainLooperProbe.await(2, TimeUnit.SECONDS)
            )
            assertEquals(StartupState.Recovering, readiness.state.value)
            assertFalse("Protected work must remain gated until recovery completes", readiness.isReady())

            finishRecovery.complete(Unit)
            withTimeout(5_000) { readiness.awaitReady() }
            assertEquals(StartupState.Ready, readiness.state.value)
        } finally {
            finishRecovery.complete(Unit)
            scope.cancel()
        }
    }

    @Test
    fun injectedRecoveryFailureNeverReleasesProtectedOperation() = runBlocking {
        val readiness = StartupReadiness()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        var protectedOperationExecuted = false
        try {
            val job = readiness.start(scope) { markRecovering ->
                markRecovering()
                throw IllegalStateException("instrumented recovery fault")
            }
            withTimeout(5_000) { job.join() }
            assertTrue(readiness.state.value is StartupState.Failed)
            val result = runCatching {
                readiness.awaitReady()
                protectedOperationExecuted = true
            }
            assertTrue(result.exceptionOrNull() is IllegalStateException)
            assertFalse(protectedOperationExecuted)
        } finally {
            scope.cancel()
        }
    }

    @Test
    fun productionApplicationUsesRealInstrumentedStartupPath() = runBlocking {
        val app = InstrumentationRegistry.getInstrumentation().targetContext
            .applicationContext as VVFApplication
        withTimeout(60_000) { app.startupReadiness.awaitReady() }
        assertTrue(app.startupReadiness.isReady())
    }
}
