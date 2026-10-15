package com.vvf.smartmanager.plugin.semanticsearch

import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.SemanticCandidate
import com.vvf.smartmanager.core.model.SemanticSearchOptions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import java.io.IOException
import org.junit.Test

class SemanticSearchPluginTest {

    @Test
    fun cosineSimilarityNormalizesVectorsAndHandlesInvalidInputs() {
        val plugin = SemanticSearchPluginImpl()
        assertEquals(1f, plugin.computeCosineSimilarity(floatArrayOf(3f, 4f), floatArrayOf(6f, 8f)), 0.0001f)
        assertEquals(0f, plugin.computeCosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 0.0001f)
        assertEquals(-1f, plugin.computeCosineSimilarity(floatArrayOf(1f, 0f), floatArrayOf(-1f, 0f)), 0.0001f)
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

    @Test
    fun unavailableModelDoesNotFabricateAiTagSuggestions() = runBlocking {
        val plugin = SemanticSearchPluginImpl()
        val candidate = SemanticCandidate(
            fileItem = FileItem(
                path = "/docs/invoice.pdf",
                name = "invoice.pdf",
                sizeBytes = 1024L,
                lastModified = 0L,
                isDirectory = false,
                mimeType = "application/pdf"
            ),
            textContent = "invoice tax payment receipt"
        )

        assertTrue("Heuristic labels must not be represented as model output", plugin.suggestTags(candidate).isEmpty())
    }

    private class FakeEmbeddingClient : EmbeddingClient {
        var queryCalls = 0
        var documentBatchCalls = 0

        override fun cancelPendingRequests() = Unit
        override fun hasSignedInUser(): Boolean = true
        override suspend fun healthCheck(): Boolean = true

        override suspend fun embed(texts: List<String>, mode: String): List<FloatArray> {
            if (mode == "query") queryCalls++ else documentBatchCalls++
            return texts.map { text ->
                FloatArray(CloudflareEmbeddingClient.EMBEDDING_DIMENSION).also { vector ->
                    if (mode == "query" || text.contains("receipt", ignoreCase = true) ||
                        text.contains("grocery", ignoreCase = true) || text.contains("food", ignoreCase = true)
                    ) vector[0] = 1f else vector[1] = 1f
                }
            }
        }
    }

    private class MemoryVectorStore : EmbeddingVectorStore {
        private val records = mutableMapOf<String, EmbeddingVectorRecord>()

        override suspend fun get(filePath: String, contentHash: String, modelVersion: String): FloatArray? =
            records[filePath]?.takeIf {
                it.contentHash == contentHash && it.modelVersion == modelVersion
            }?.vector?.copyOf()

        override suspend fun put(filePath: String, contentHash: String, modelVersion: String, vector: FloatArray) {
            records[filePath] = EmbeddingVectorRecord(filePath, contentHash, modelVersion, vector.copyOf())
        }

        override suspend fun clear() {
            records.clear()
        }

        fun count(): Int = records.size
    }

    @Test
    fun neuralRankingUsesRealClientVectorsAndReusesPersistedVectors() = runBlocking {
        val receipt = SemanticCandidate(
            fileItem = FileItem(
                path = "gdrive://receipt",
                name = "receipt_march.pdf",
                sizeBytes = 1024L,
                lastModified = 0L,
                isDirectory = false,
                mimeType = "application/pdf"
            ),
            textContent = "receipt grocery food expenses"
        )
        val medical = SemanticCandidate(
            fileItem = FileItem(
                path = "gdrive://medical",
                name = "doctor_report.pdf",
                sizeBytes = 2048L,
                lastModified = 0L,
                isDirectory = false,
                mimeType = "application/pdf"
            ),
            textContent = "doctor hospital medical report"
        )
        val store = MemoryVectorStore()
        val firstClient = FakeEmbeddingClient()
        val firstPlugin = SemanticSearchPluginImpl(
            client = firstClient,
            vectorStore = store,
            isEmbeddingConsentGranted = { true },
            isFullContentConsentGranted = { true }
        )

        val firstResults = firstPlugin.searchSimilar(
            query = "food expenses",
            candidates = listOf(receipt, medical),
            options = SemanticSearchOptions(similarityThreshold = 0.80f)
        )

        assertEquals(listOf("gdrive://receipt"), firstResults.map { it.fileItem.path })
        assertEquals(1, firstClient.queryCalls)
        assertEquals(1, firstClient.documentBatchCalls)
        assertEquals(2, store.count())

        // A fresh plugin instance has no in-memory cache; load vectors by path + content hash
        // + model version instead of re-embedding document text.
        val secondClient = FakeEmbeddingClient()
        val secondPlugin = SemanticSearchPluginImpl(
            client = secondClient,
            vectorStore = store,
            isEmbeddingConsentGranted = { true },
            isFullContentConsentGranted = { true }
        )
        val secondResults = secondPlugin.searchSimilar(
            query = "food expenses",
            candidates = listOf(receipt, medical),
            options = SemanticSearchOptions(similarityThreshold = 0.80f)
        )

        assertEquals(listOf("gdrive://receipt"), secondResults.map { it.fileItem.path })
        assertEquals(1, secondClient.queryCalls)
        assertEquals("Persisted vectors should avoid another document embedding batch", 0, secondClient.documentBatchCalls)
    }

    @Test
    fun revokingConsentPreventsNeuralProviderCalls() = runBlocking {
        val client = FakeEmbeddingClient()
        val plugin = SemanticSearchPluginImpl(
            client = client,
            isEmbeddingConsentGranted = { false },
            isFullContentConsentGranted = { true }
        )
        val candidate = SemanticCandidate(
            fileItem = FileItem(
                path = "gdrive://receipt",
                name = "receipt.pdf",
                sizeBytes = 10L,
                lastModified = 0L,
                isDirectory = false,
                mimeType = "application/pdf"
            ),
            textContent = "receipt groceries"
        )
        assertTrue(plugin.searchSimilar("food", listOf(candidate), SemanticSearchOptions()).isEmpty())
        assertEquals(0, client.queryCalls)
        assertEquals(0, client.documentBatchCalls)
    }


    @Test
    fun remoteEmbeddingFailuresArePropagatedInsteadOfReportedAsEmptyResults() = runBlocking {
        val failingClient = object : EmbeddingClient {
            override fun cancelPendingRequests() = Unit
            override fun hasSignedInUser(): Boolean = true
            override suspend fun healthCheck(): Boolean = true
            override suspend fun embed(texts: List<String>, mode: String): List<FloatArray> {
                throw IOException("simulated embedding service outage")
            }
        }
        val plugin = SemanticSearchPluginImpl(
            client = failingClient,
            isEmbeddingConsentGranted = { true },
            isFullContentConsentGranted = { true }
        )
        val candidateA = SemanticCandidate(
            fileItem = FileItem(
                path = "gdrive://a",
                name = "a.pdf",
                sizeBytes = 10L,
                lastModified = 0L,
                isDirectory = false,
                mimeType = "application/pdf"
            ),
            textContent = "invoice"
        )
        val candidateB = SemanticCandidate(
            fileItem = FileItem(
                path = "gdrive://b",
                name = "b.pdf",
                sizeBytes = 10L,
                lastModified = 0L,
                isDirectory = false,
                mimeType = "application/pdf"
            ),
            textContent = "receipt"
        )

        try {
            plugin.generateEmbedding("invoice")
            fail("generateEmbedding must propagate service failures")
        } catch (expected: IOException) {
            assertEquals("simulated embedding service outage", expected.message)
        }

        try {
            plugin.findNearDuplicates(listOf(candidateA, candidateB), 0.8f)
            fail("findNearDuplicates must propagate service failures")
        } catch (expected: IOException) {
            assertEquals("simulated embedding service outage", expected.message)
        }
    }

}
