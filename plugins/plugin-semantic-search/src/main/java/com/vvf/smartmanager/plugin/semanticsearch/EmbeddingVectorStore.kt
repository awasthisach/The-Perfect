package com.vvf.smartmanager.plugin.semanticsearch

/**
 * Persistence boundary for consented semantic vectors. Implementations must store vectors only
 * in app-private encrypted storage and validate the content hash/model version before reuse.
 */
interface EmbeddingVectorStore {
    suspend fun get(filePath: String, contentHash: String, modelVersion: String): FloatArray?
    suspend fun put(filePath: String, contentHash: String, modelVersion: String, vector: FloatArray)
    suspend fun clear()
}
