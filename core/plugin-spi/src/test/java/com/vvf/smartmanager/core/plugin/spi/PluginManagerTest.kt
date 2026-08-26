package com.vvf.smartmanager.core.plugin.spi

import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.OcrOptions
import com.vvf.smartmanager.core.model.OcrProgress
import com.vvf.smartmanager.core.model.OcrResult
import com.vvf.smartmanager.core.model.SimilarityMatch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginManagerTest {

    private class FakeOcrPlugin : OcrPluginSPI {
        override val isEnabled: Boolean = true
        override val engineName: String = "Fake ML Kit"

        override suspend fun isModelDownloaded(): Boolean = true
        override suspend fun downloadModel(onProgress: (Float) -> Unit): Boolean {
            onProgress(1.0f)
            return true
        }

        override suspend fun extractText(
            fileItem: FileItem,
            options: OcrOptions,
            onProgress: (OcrProgress) -> Unit
        ): Result<OcrResult> {
            return Result.success(
                OcrResult(
                    extractedText = "Sample OCR Text",
                    language = "en",
                    confidence = 0.98f,
                    totalWords = 3,
                    pageCount = 1,
                    processingDurationMs = 150L
                )
            )
        }

        override fun cancel() {}
    }

    private class FakeSemanticPlugin : SemanticSearchSPI {
        override val isEnabled: Boolean = true
        override val modelName: String = "Fake TFLite MobileBERT"
        override val embeddingDimension: Int = 256

        override suspend fun isModelReady(): Boolean = true
        override suspend fun downloadModel(onProgress: (Float) -> Unit): Boolean = true

        override suspend fun generateEmbedding(text: String): Result<FloatArray> {
            return Result.success(FloatArray(256) { 0.1f })
        }

        override suspend fun findSimilarFiles(
            query: String,
            targetFiles: List<FileItem>,
            minSimilarity: Float
        ): Result<List<SimilarityMatch>> {
            return Result.success(emptyList())
        }

        override fun calculateCosineSimilarity(vec1: FloatArray, vec2: FloatArray): Float = 0.95f
        override fun release() {}
    }

    @Test
    fun testPluginManagerRegistrationAndLifecycle() = runBlocking {
        val pluginManager = PluginManager()

        val ocrPlugin = FakeOcrPlugin()
        val semanticPlugin = FakeSemanticPlugin()

        pluginManager.registerOcrPlugin(ocrPlugin)
        pluginManager.registerSemanticPlugin(semanticPlugin)

        assertNotNull(pluginManager.getOcrPlugin())
        assertNotNull(pluginManager.getSemanticPlugin())

        // Refresh descriptors
        pluginManager.refreshPluginDescriptors()
        val plugins = pluginManager.pluginsState.value

        assertEquals(3, plugins.size) // OCR, Semantic AI, Cloud Drivers

        val ocrDescriptor = plugins.find { it.category == PluginCategory.OCR }
        assertNotNull(ocrDescriptor)
        assertEquals("plugin.ocr.mlkit", ocrDescriptor?.id)
        assertTrue(ocrDescriptor?.isInstalled == true)

        val semanticDescriptor = plugins.find { it.category == PluginCategory.SEMANTIC_AI }
        assertNotNull(semanticDescriptor)
        assertEquals("plugin.semantic.tflite", semanticDescriptor?.id)
        assertTrue(semanticDescriptor?.isInstalled == true)

        // Toggle state
        pluginManager.setPluginEnabled("plugin.ocr.mlkit", false)
        val updatedPlugins = pluginManager.pluginsState.value
        val updatedOcr = updatedPlugins.find { it.id == "plugin.ocr.mlkit" }
        assertFalse(updatedOcr?.isEnabled == true)
    }
}
