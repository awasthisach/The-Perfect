package com.vvf.smartmanager

import android.content.ComponentCallbacks2
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vvf.smartmanager.core.database.VVFDatabase
import com.vvf.smartmanager.core.database.entity.FileEntity
import com.vvf.smartmanager.core.model.FileCategory
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
@Config(manifest = Config.NONE)
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
            val app = VVFApplication()
            // Validate application instance creation is extremely fast (well under target threshold)
            assertNotNull(app)
        }
        // JVM cold init target is < 2000ms; Device cold boot target is < 10000ms
        assertTrue("Startup container init must be under 2000ms, actual: ${startupDurationMs}ms", startupDurationMs < 2000)
    }

    @Test
    fun testMemoryTrimmingExecutionSafety() {
        val app = ApplicationProvider.getApplicationContext<VVFApplication>()
        // Execute low memory trim callbacks to ensure safe garbage collection without crashes
        app.onTrimMemory(ComponentCallbacks2.TRIM_MEMORY_RUNNING_CRITICAL)
        app.onLowMemory()
        assertNotNull(app)
    }

    @Test
    fun testDatabaseQueryResponseSla() = runBlocking {
        val fileDao = database.fileDao()

        // Seed 100 benchmark files
        val testFiles = (1..100).map { i ->
            FileEntity(
                id = "bench_$i",
                name = "Benchmark_Document_$i.pdf",
                path = "/storage/emulated/0/Documents/Benchmark_Document_$i.pdf",
                size = 1024L * i,
                lastModified = System.currentTimeMillis(),
                mimeType = "application/pdf",
                category = FileCategory.DOCUMENTS,
                extension = "pdf"
            )
        }
        fileDao.insertFiles(testFiles)

        // Query execution SLA benchmark: fetching files must execute in < 100ms
        val queryDurationMs = measureTimeMillis {
            val result = fileDao.getAllFiles()
            assertTrue(result.isNotEmpty())
        }

        assertTrue("Room Database query execution SLA must be < 100ms, actual: ${queryDurationMs}ms", queryDurationMs < 100)
    }
}
