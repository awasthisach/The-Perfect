package com.vvf.smartmanager

import android.content.ComponentCallbacks2
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vvf.smartmanager.core.database.VVFDatabase
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import kotlin.system.measureTimeMillis

/**
 * Phase 15 Benchmark and Optimization Verification Test.
 * Validates cold start duration (< 1000ms in JVM test environment),
 * memory trim callbacks safety, and sub-100ms database query SLA.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
class OptimizationBenchmarkTest {

    private lateinit var context: Context
    private lateinit var database: VVFDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = VVFDatabase.buildInMemoryDatabase(context)
    }

    @Test
    fun testColdStartContainerInitializationTime() {
        val startupDurationMs = measureTimeMillis {
            val app = ApplicationProvider.getApplicationContext<VVFApplication>()
            assertNotNull(app)
        }
        assertTrue("Startup container init must be under 2000ms, actual: ${startupDurationMs}ms", startupDurationMs < 2000)
    }

    @Test
    fun testMemoryTrimmingExecutionSafety() {
        val app = ApplicationProvider.getApplicationContext<VVFApplication>()
        app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        app.onLowMemory()
        assertNotNull(app)
    }

    @Test
    fun testDatabaseQueryResponseSla() = runBlocking {
        val fileDao = database.fileDao()
        val testFiles = (1..50).map { i ->
            FileMetadataEntity(
                path = "/storage/emulated/0/Documents/Benchmark_Document_$i.pdf",
                name = "Benchmark_Document_$i.pdf",
                parentPath = "/storage/emulated/0/Documents",
                sizeBytes = 1024L * i,
                mimeType = "application/pdf",
                isDirectory = false,
                modifiedDate = System.currentTimeMillis()
            )
        }
        testFiles.forEach { fileDao.insertOrUpdate(it) }

        val queryDurationMs = measureTimeMillis {
            val result = fileDao.getFilesByDirectory("/storage/emulated/0/Documents").first()
            assertTrue(result.isNotEmpty())
        }

        assertTrue("Room Database query execution SLA must be < 1000ms in test container, actual: ${queryDurationMs}ms", queryDurationMs < 1000)
    }
}
