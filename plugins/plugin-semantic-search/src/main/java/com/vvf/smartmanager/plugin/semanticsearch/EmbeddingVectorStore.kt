package com.vvf.smartmanager.plugin.semanticsearch

data class EmbeddingVectorLookup(
    val filePath: String,
    val contentHash: String,
    val modelVersion: String
)

data class EmbeddingVectorRecord(
    val filePath: String,
    val contentHash: String,
    val modelVersion: String,
    val vector: FloatArray
)

/**
 * Persistence boundary for consented semantic vectors. Implementations must store vectors only
 * in app-private encrypted storage and validate the content hash/model version before reuse.
 */
interface EmbeddingVectorStore {
    suspend fun get(filePath: String, contentHash: String, modelVersion: String): FloatArray?

    suspend fun getMany(requests: List<EmbeddingVectorLookup>): Map<String, FloatArray> {
        val vectors = LinkedHashMap<String, FloatArray>()
        requests.forEach { request ->
            get(request.filePath, request.contentHash, request.modelVersion)?.let {
                vectors[request.filePath] = it
            }
        }
        return vectors
    }

    suspend fun put(filePath: String, contentHash: String, modelVersion: String, vector: FloatArray)

    suspend fun putAll(records: List<EmbeddingVectorRecord>) {
        records.forEach { put(it.filePath, it.contentHash, it.modelVersion, it.vector) }
    }

    suspend fun clear()
}
