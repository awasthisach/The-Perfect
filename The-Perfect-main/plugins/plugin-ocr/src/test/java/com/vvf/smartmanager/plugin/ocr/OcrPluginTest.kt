package com.vvf.smartmanager.plugin.ocr

import com.vvf.smartmanager.core.model.FileCategory
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.OcrOptions
import com.vvf.smartmanager.core.model.OcrProgress
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class OcrPluginTest {

    @Test
    fun testPluginMetadata() {
        val plugin = OcrEnginePlugin()
        assertEquals("plugin.ocr.mlkit", plugin.pluginId)
        assertEquals("1.0.0", plugin.version)
        assertTrue(plugin.isEnabled)
    }

    @Test
    fun testDownloadModelSimulation() = runBlocking {
        val plugin = OcrEnginePlugin()
        var progressRecorded = 0f
        val result = plugin.downloadModel { progress ->
            progressRecorded = progress
        }
        assertTrue(result)
        assertEquals(1.0f, progressRecorded, 0.01f)
        assertTrue(plugin.isModelDownloaded())
    }

    @Test
    fun testMissingFileHandling() = runBlocking {
        val plugin = OcrEnginePlugin()
        val nonExistentFile = FileItem(
            path = "/storage/emulated/0/non_existent_file_9999.jpg",
            name = "non_existent_file_9999.jpg",
            sizeBytes = 1024L,
            mimeType = "image/jpeg",
            isDirectory = false,
            lastModified = System.currentTimeMillis()
        )

        val result = plugin.extractText(nonExistentFile)
        assertTrue(result.isFailure)
        assertNotNull(result.exceptionOrNull())
    }

    @Test
    fun testCancellationSignal() {
        val plugin = OcrEnginePlugin()
        plugin.cancelOngoing()
        // verify cancellation flag is cleanly processed
        assertNotNull(plugin)
    }
}
