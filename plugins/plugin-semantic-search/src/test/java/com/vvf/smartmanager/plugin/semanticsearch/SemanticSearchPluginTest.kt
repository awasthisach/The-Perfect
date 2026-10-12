package com.vvf.smartmanager.plugin.semanticsearch

import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.SemanticCandidate
import com.vvf.smartmanager.core.model.SemanticSearchOptions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticSearchPluginTest {

    @Test
    fun cosineSimilarityNormalizesVectorsAndHandlesInvalidInputs() {
        val plugin = SemanticSearchPluginImpl()
        assertEquals(1f, plugin.computeCosineSimilarity(floatArrayOf(3f, 4f), floatArrayOf(6f, 8f)), 0.0001f)
        assertEquals(0f, plugin.computeCosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 0.0001f)
        assertEquals(0f, plugin.computeCosineSimilarity(floatArrayOf(0f, 0f), floatArrayOf(1f, 1f)), 0.0001f)
        assertEquals(0f, plugin.computeCosineSimilarity(floatArrayOf(1f), floatArrayOf(1f, 2f)), 0.0001f)
        assertEquals(0f, plugin.computeCosineSimilarity(floatArrayOf(Float.NaN), floatArrayOf(1f)), 0.0001f)
    }

    @Test
    fun semanticSearchAndSimilarityClusteringFailClosedWithoutRealModel() = runBlocking {
        val plugin = SemanticSearchPluginImpl()
        val receipt = SemanticCandidate(
            fileItem = FileItem(
                path = "/docs/receipt_march.pdf",
                name = "receipt_march.pdf",
                sizeBytes = 1024L,
                lastModified = 0L,
                isDirectory = false,
                mimeType = "application/pdf"
            ),
            textContent = "Supermarket grocery bill payment receipt"
        )
        val prescription = SemanticCandidate(
            fileItem = FileItem(
                path = "/docs/prescription.pdf",
                name = "prescription.pdf",
                sizeBytes = 2048L,
                lastModified = 0L,
                isDirectory = false,
                mimeType = "application/pdf"
            ),
            textContent = "Hospital doctor prescription medical lab report"
        )
        val candidates = listOf(receipt, prescription)

        assertFalse("Hash vectors must not be advertised as a neural model", plugin.isModelReady())
        assertFalse("A fake download must not report success", plugin.downloadModel { })
        assertTrue("Unconfigured provider must not emit synthetic vectors", plugin.generateEmbedding("invoice tax").isEmpty())
        assertTrue(
            plugin.searchSimilar(
                query = "invoice tax",
                candidates = candidates,
                options = SemanticSearchOptions(similarityThreshold = 0.50f)
            ).isEmpty()
        )
        assertTrue(plugin.findNearDuplicates(candidates, similarityThreshold = 0.80f).isEmpty())
    }
}
