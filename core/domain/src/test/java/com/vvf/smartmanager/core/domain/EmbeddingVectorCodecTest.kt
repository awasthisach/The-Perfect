package com.vvf.smartmanager.core.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class EmbeddingVectorCodecTest {
    @Test
    fun encodeDecodeRoundTripPreservesFloatValues() {
        val original = floatArrayOf(1f, 0.5f, -0.25f, 0f)
        val decoded = EmbeddingVectorCodec.decode(EmbeddingVectorCodec.encode(original), original.size)
        assertNotNull(decoded)
        original.indices.forEach { index -> assertEquals(original[index], decoded!![index], 0.00001f) }
    }

    @Test
    fun decodeRejectsWrongDimensionAndCosineRejectsMismatchedVectors() {
        assertNull(EmbeddingVectorCodec.decode(byteArrayOf(1, 2, 3), 768))
        assertEquals(-1f, EmbeddingVectorCodec.cosine(floatArrayOf(1f), floatArrayOf(1f, 2f)), 0f)
    }

    @Test
    fun cosineRanksAlignedVectorsAboveOrthogonalVectors() {
        assertEquals(1f, EmbeddingVectorCodec.cosine(floatArrayOf(1f, 0f), floatArrayOf(1f, 0f)), 0.00001f)
        assertEquals(0f, EmbeddingVectorCodec.cosine(floatArrayOf(1f, 0f), floatArrayOf(0f, 1f)), 0.00001f)
    }
}
