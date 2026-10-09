package com.vvf.smartmanager.core.domain

import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.sqrt

object EmbeddingVectorCodec {
    fun encode(vector: FloatArray): ByteArray {
        val buffer = ByteBuffer.allocate(vector.size * Float.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        vector.forEach { value ->
            require(value.isFinite()) { "Embedding vector contains a non-finite value." }
            buffer.putFloat(value)
        }
        return buffer.array()
    }

    fun decode(bytes: ByteArray?, expectedDimension: Int): FloatArray? {
        if (bytes == null || expectedDimension <= 0 || bytes.size != expectedDimension * Float.SIZE_BYTES) return null
        val buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        return FloatArray(expectedDimension) { buffer.float }.takeIf { vector -> vector.all(Float::isFinite) }
    }

    fun cosine(left: FloatArray, right: FloatArray): Float {
        if (left.isEmpty() || left.size != right.size) return -1f
        var dot = 0.0
        var leftNorm = 0.0
        var rightNorm = 0.0
        for (index in left.indices) {
            val a = left[index].toDouble()
            val b = right[index].toDouble()
            if (!a.isFinite() || !b.isFinite()) return -1f
            dot += a * b
            leftNorm += a * a
            rightNorm += b * b
        }
        if (leftNorm <= 0.0 || rightNorm <= 0.0) return -1f
        return (dot / (sqrt(leftNorm) * sqrt(rightNorm))).toFloat().coerceIn(-1f, 1f)
    }
}
