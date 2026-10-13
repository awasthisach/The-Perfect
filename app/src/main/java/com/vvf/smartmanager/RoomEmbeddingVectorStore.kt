package com.vvf.smartmanager

import com.vvf.smartmanager.core.database.dao.SemanticEmbeddingDao
import com.vvf.smartmanager.core.database.model.SemanticEmbeddingEntity
import com.vvf.smartmanager.plugin.semanticsearch.CloudflareEmbeddingClient
import com.vvf.smartmanager.plugin.semanticsearch.EmbeddingVectorStore
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** Stores vectors in the app's SQLCipher-encrypted Room database. */
class RoomEmbeddingVectorStore(
    private val dao: SemanticEmbeddingDao
) : EmbeddingVectorStore {

    override suspend fun get(
        filePath: String,
        contentHash: String,
        modelVersion: String
    ): FloatArray? {
        val row = dao.getByPath(filePath) ?: return null
        if (row.contentHash != contentHash || row.modelVersion != modelVersion) return null
        if (row.vector.size != VECTOR_BYTES) {
            dao.deleteByPath(filePath)
            return null
        }
        val vector = FloatArray(CloudflareEmbeddingClient.EMBEDDING_DIMENSION)
        ByteBuffer.wrap(row.vector).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(vector)
        if (vector.any { !it.isFinite() }) {
            dao.deleteByPath(filePath)
            return null
        }
        return vector
    }

    override suspend fun put(
        filePath: String,
        contentHash: String,
        modelVersion: String,
        vector: FloatArray
    ) {
        require(vector.size == CloudflareEmbeddingClient.EMBEDDING_DIMENSION) {
            "Unexpected semantic vector dimension"
        }
        require(vector.all { it.isFinite() }) { "Semantic vector contains a non-finite value" }
        val bytes = ByteBuffer.allocate(VECTOR_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .also { buffer -> buffer.asFloatBuffer().put(vector) }
            .array()
        dao.upsert(
            SemanticEmbeddingEntity(
                filePath = filePath,
                contentHash = contentHash,
                modelVersion = modelVersion,
                vector = bytes
            )
        )
    }

    override suspend fun clear() {
        dao.deleteAll()
    }

    companion object {
        private const val VECTOR_BYTES = CloudflareEmbeddingClient.EMBEDDING_DIMENSION * Float.SIZE_BYTES
    }
}
