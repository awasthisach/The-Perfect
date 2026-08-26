package com.vvf.smartmanager.core.plugin.spi

import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.OcrOptions
import com.vvf.smartmanager.core.model.OcrProgress
import com.vvf.smartmanager.core.model.OcrResult
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
        ): Result<OcrResult> {
            return Result.success(
                OcrResult(
                    fullText = "Sample OCR Text",
                    language = "en",
                    confidence = 0.98f,
                    totalWords = 3,
                    pageCount = 1,
                    processingDurationMs = 150L
                )
            )
        }

        override fun cancelOngoing() {}
    }

    private class FakeSemanticPlugin : SemanticSearchSPI {
        override val isEnabled: Boolean = true
        override val displayName: String = "Fake TFLite MobileBERT"

        override fun isModelReady(): Boolean = true
        override suspend fun downloadModel(progressCallback: (Float) -> Unit): Boolean = true

        override suspend fun generateEmbedding(text: String): FloatArray {
            return FloatArray(256) { 0.1f }
        }

        override suspend fun searchSimilar(
            query: String,
            candidates: List<com.vvf.smartmanager.core.model.SemanticCandidate>,
            options: com.vvf.smartmanager.core.model.SemanticSearchOptions
        ): List<com.vvf.smartmanager.core.model.SemanticSearchResult> {
            return emptyList()
        }

        override suspend fun findNearDuplicates(
            candidates: List<com.vvf.smartmanager.core.model.SemanticCandidate>,
            similarityThreshold: Float
        ): List<com.vvf.smartmanager.core.model.NearDuplicateCluster> {
            return emptyList()
        }

        override suspend fun suggestTags(candidate: com.vvf.smartmanager.core.model.SemanticCandidate): List<com.vvf.smartmanager.core.model.AiSuggestedTag> {
            return emptyList()
        }

        override fun computeCosineSimilarity(vectorA: FloatArray, vectorB: FloatArray): Float = 0.95f
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
