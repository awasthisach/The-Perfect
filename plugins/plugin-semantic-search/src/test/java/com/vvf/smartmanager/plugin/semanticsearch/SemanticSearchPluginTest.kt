package com.vvf.smartmanager.plugin.semanticsearch

import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.SemanticCandidate
import com.vvf.smartmanager.core.model.SemanticSearchOptions
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SemanticSearchPluginTest {

    @Test
    fun testGenerateEmbeddingDimensionAndNormalization() = runBlocking {
        val plugin = SemanticSearchPluginImpl()
        val embedding = plugin.generateEmbedding("Tax Invoice March 2026")
        
        assertEquals(128, embedding.size)
        
        var sumSquares = 0.0
        for (v in embedding) {
            sumSquares += (v * v)
        }
        val norm = kotlin.math.sqrt(sumSquares).toFloat()
        // Normalized vector L2 norm should be very close to 1.0
        assertTrue("Norm should be close to 1.0: $norm", norm > 0.95f && norm < 1.05f)
    }

    @Test
    fun testConceptualSemanticClustering() = runBlocking {
        val plugin = SemanticSearchPluginImpl()
        
        val doc1 = SemanticCandidate(
            fileItem = FileItem(
                path = "/docs/receipt_march.pdf",
                name = "receipt_march.pdf",
                sizeBytes = 1024L,
                lastModified = 0L,
                isDirectory = false,
                mimeType = "application/pdf"
            ),
            textContent = "Supermarket Grocery bill payment receipt"
        )
        val doc2 = SemanticCandidate(
            fileItem = FileItem(
                path = "/docs/prescription.pdf",
                name = "prescription.pdf",
                sizeBytes = 2048L,
                lastModified = 0L,
                isDirectory = false,
                mimeType = "application/pdf"
            ),
            textContent = "Hospital Doctor prescription medical lab report"
        )
        val doc3 = SemanticCandidate(
            fileItem = FileItem(
                path = "/docs/nda_partner.pdf",
                name = "nda_partner.pdf",
                sizeBytes = 3072L,
                lastModified = 0L,
                isDirectory = false,
                mimeType = "application/pdf"
            ),
            textContent = "Confidential agreement contract deed signatures"
        )

        val candidates = listOf(doc1, doc2, doc3)

        // Query conceptual invoice/tax
        val invoiceResults = plugin.searchSimilar(
            query = "invoice and gst tax",
            candidates = candidates,
            options = SemanticSearchOptions(similarityThreshold = 0.50f)
        )

        assertTrue(invoiceResults.isNotEmpty())
        assertEquals("/docs/receipt_march.pdf", invoiceResults.first().fileItem.path)
        assertTrue(invoiceResults.first().similarityScore >= 0.50f)

        // Query doctor medical
        val medicalResults = plugin.searchSimilar(
            query = "hospital health medicines",
            candidates = candidates,
            options = SemanticSearchOptions(similarityThreshold = 0.50f)
        )

        assertTrue(medicalResults.isNotEmpty())
        assertEquals("/docs/prescription.pdf", medicalResults.first().fileItem.path)
    }
}
