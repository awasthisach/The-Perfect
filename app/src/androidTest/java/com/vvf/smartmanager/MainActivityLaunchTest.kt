package com.vvf.smartmanager

import android.content.Intent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivityLaunchTest {

    @Test
    fun launcherActivityStartsAndRemainsAliveWithoutProductionFirebaseSecrets() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val targetContext = instrumentation.targetContext
        val launchIntent = targetContext.packageManager
            .getLaunchIntentForPackage(targetContext.packageName)
            ?: error("VVF Smart Manager launcher activity is not declared")

        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val activity = instrumentation.startActivitySync(launchIntent)

        try {
            assertTrue("Launcher intent must resolve to MainActivity", activity is MainActivity)
            assertFalse("MainActivity must not finish during startup", activity.isFinishing)
            assertFalse("MainActivity must not be destroyed during startup", activity.isDestroyed)
            val app = targetContext.applicationContext as VVFApplication
            runBlocking { withTimeout(60_000) { app.startupReadiness.awaitReady() } }
            assertTrue("Real instrumented Application startup must reach Ready", app.startupReadiness.isReady())
            assertTrue("Encrypted Room database must be initialized before Ready", app::database.isInitialized)
            assertTrue("Vault repository must be initialized before Ready", app::vaultRepository.isInitialized)
        } finally {
            activity.finish()
            instrumentation.waitForIdleSync()
        }
    }
}
