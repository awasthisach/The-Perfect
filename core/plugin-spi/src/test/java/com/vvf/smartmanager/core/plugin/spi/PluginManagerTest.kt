package com.vvf.smartmanager.core.plugin.spi

import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.OcrOptions
import com.vvf.smartmanager.core.model.OcrProgress
import com.vvf.smartmanager.core.model.OcrResult
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginManagerTest {
    private class FakeOcrPlugin : OcrPluginSPI {
        override val isEnabled: Boolean = true
        override val displayName: String = "Fake ML Kit"
        override suspend fun isModelDownloaded(): Boolean = true
        override suspend fun downloadModel(progressCallback: (Float) -> Unit): Boolean {
            progressCallback(1.0f)
            return true
        }
        override suspend fun extractText(
            fileItem: FileItem,
            options: OcrOptions,
            onProgress: ((OcrProgress) -> Unit)?
        ): Result<OcrResult> = Result.success(
            OcrResult("Sample OCR Text", "en", 0.98f, 3, 1, 150L)
        )
        override fun cancelOngoing() = Unit
    }

    private class FakeSemanticPlugin : SemanticSearchSPI {
        override val isEnabled: Boolean = true
        override val displayName: String = "Fake TFLite MobileBERT"
        override fun isModelReady(): Boolean = true
        override suspend fun downloadModel(progressCallback: (Float) -> Unit): Boolean = true
        override suspend fun generateEmbedding(text: String): FloatArray = FloatArray(256) { 0.1f }
        override suspend fun searchSimilar(
            query: String,
            candidates: List<com.vvf.smartmanager.core.model.SemanticCandidate>,
            options: com.vvf.smartmanager.core.model.SemanticSearchOptions
        ) = emptyList<com.vvf.smartmanager.core.model.SemanticSearchResult>()
        override suspend fun findNearDuplicates(
            candidates: List<com.vvf.smartmanager.core.model.SemanticCandidate>,
            similarityThreshold: Float
        ) = emptyList<com.vvf.smartmanager.core.model.NearDuplicateCluster>()
        override suspend fun suggestTags(candidate: com.vvf.smartmanager.core.model.SemanticCandidate) =
            emptyList<com.vvf.smartmanager.core.model.AiSuggestedTag>()
        override fun computeCosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float = 0.95f
    }

    @Test
    fun registeredPluginsAreReflectedInDescriptors() = runBlocking {
        val manager = PluginManager()
        manager.registerOcrPlugin(FakeOcrPlugin())
        manager.registerSemanticPlugin(FakeSemanticPlugin())
        manager.refreshPluginDescriptors()

        assertEquals(3, manager.pluginsState.value.size)
        assertTrue(manager.pluginsState.value.first { it.id == "plugin.ocr.mlkit" }.isInstalled)
        assertTrue(manager.pluginsState.value.first { it.id == "plugin.semantic.tflite" }.isInstalled)

        manager.setPluginEnabled("plugin.ocr.mlkit", false)
        assertFalse(manager.pluginsState.value.first { it.id == "plugin.ocr.mlkit" }.isEnabled)
    }

    @Test
    fun invalidDownloadProgressIsRejected() = runBlocking {
        val manager = PluginManager()
        manager.refreshPluginDescriptors()
        assertThrows(IllegalArgumentException::class.java) {
            manager.updateDownloadProgress("plugin.ocr.mlkit", 1.5f, false)
        }
    }

    @Test
    fun duplicateCloudDriverIdsAreIgnored() {
        val driver = object : CloudDriverSPI {
            override val driverId = "test"
            override val displayName = "Test"
            override val iconResName = "test"
            override suspend fun authenticate() = true
            override suspend fun listRemoteFiles(remotePath: String) = emptyList<FileItem>()
            override suspend fun uploadFile(localFile: FileItem, remoteDirectory: String) =
                CloudUploadResult("id", "/backup", 0)
            override suspend fun downloadFile(remoteFile: FileItem, localDestination: String) = true
            override suspend fun getQuotaUsage() = 0L to 1L
        }

        val manager = PluginManager(cloudDrivers = listOf(driver, driver))
        assertNotNull(manager.getCloudDrivers())
        assertEquals(1, manager.getCloudDrivers().size)
    }

    @Test
    fun cloudUploadResultRejectsInvalidIdentity() {
        assertThrows(IllegalArgumentException::class.java) { CloudUploadResult("", "/backup", 1) }
        assertThrows(IllegalArgumentException::class.java) { CloudUploadResult("id", "", 1) }
        assertThrows(IllegalArgumentException::class.java) { CloudUploadResult("id", "/backup", -1) }
    }

    @Test
    fun cloudUploadResultPreservesCanonicalMetadata() {
        val result = CloudUploadResult("remote-123", "/backup/archive.enc", 4096)
        assertEquals("remote-123", result.remoteId)
        assertEquals("/backup/archive.enc", result.remotePath)
        assertEquals(4096, result.sizeBytes)
    }
}
