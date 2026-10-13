package com.vvf.smartmanager

import com.vvf.smartmanager.core.database.dao.SemanticEmbeddingDao
import com.vvf.smartmanager.core.database.model.SemanticEmbeddingEntity
import com.vvf.smartmanager.plugin.semanticsearch.CloudflareEmbeddingClient
import com.vvf.smartmanager.plugin.semanticsearch.EmbeddingVectorLookup
import com.vvf.smartmanager.plugin.semanticsearch.EmbeddingVectorRecord
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
    ): FloatArray? = decode(
        dao.getByPath(filePath),
        expectedHash = contentHash,
        expectedModelVersion = modelVersion
    )

    override suspend fun getMany(requests: List<EmbeddingVectorLookup>): Map<String, FloatArray> {
        if (requests.isEmpty()) return emptyMap()
        val byPath = requests.associateBy { it.filePath }
        val vectors = LinkedHashMap<String, FloatArray>()
        requests.map { it.filePath }.distinct().chunked(SQLITE_BIND_BATCH_SIZE).forEach { paths ->
            dao.getByPaths(paths).forEach rowLoop@{ row ->
                val request = byPath[row.filePath] ?: return@rowLoop
                decode(row, request.contentHash, request.modelVersion)?.let { vectors[row.filePath] = it }
            }
        }
        return vectors
    }

    override suspend fun put(
        filePath: String,
        contentHash: String,
        modelVersion: String,
        vector: FloatArray
    ) {
        dao.upsert(toEntity(filePath, contentHash, modelVersion, vector))
    }

    override suspend fun putAll(records: List<EmbeddingVectorRecord>) {
        if (records.isNotEmpty()) dao.upsertAll(records.map { record ->
            toEntity(record.filePath, record.contentHash, record.modelVersion, record.vector)
        })
    }

    override suspend fun clear() {
        dao.deleteAll()
    }

    private suspend fun decode(
        row: SemanticEmbeddingEntity?,
        expectedHash: String,
        expectedModelVersion: String
    ): FloatArray? {
        row ?: return null
        if (row.contentHash != expectedHash || row.modelVersion != expectedModelVersion) return null
        if (row.vector.size != VECTOR_BYTES) {
            dao.deleteByPath(row.filePath)
            return null
        }
        val vector = FloatArray(CloudflareEmbeddingClient.EMBEDDING_DIMENSION)
        ByteBuffer.wrap(row.vector).order(ByteOrder.LITTLE_ENDIAN).asFloatBuffer().get(vector)
        if (vector.any { !it.isFinite() }) {
            dao.deleteByPath(row.filePath)
            return null
        }
        return vector
    }

    private fun toEntity(
        filePath: String,
        contentHash: String,
        modelVersion: String,
        vector: FloatArray
    ): SemanticEmbeddingEntity {
        require(vector.size == CloudflareEmbeddingClient.EMBEDDING_DIMENSION) {
            "Unexpected semantic vector dimension"
        }
        require(vector.all { it.isFinite() }) { "Semantic vector contains a non-finite value" }
        val bytes = ByteBuffer.allocate(VECTOR_BYTES)
            .order(ByteOrder.LITTLE_ENDIAN)
            .also { buffer -> buffer.asFloatBuffer().put(vector) }
            .array()
        return SemanticEmbeddingEntity(
            filePath = filePath,
            contentHash = contentHash,
            modelVersion = modelVersion,
            vector = bytes
        )
    }

    companion object {
        private const val VECTOR_BYTES = CloudflareEmbeddingClient.EMBEDDING_DIMENSION * Float.SIZE_BYTES
        private const val SQLITE_BIND_BATCH_SIZE = 200
    }
}
