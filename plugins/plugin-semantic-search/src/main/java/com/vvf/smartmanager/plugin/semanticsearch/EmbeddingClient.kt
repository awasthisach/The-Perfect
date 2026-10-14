package com.vvf.smartmanager.plugin.semanticsearch

/**
 * Authenticated remote embedding contract. Kept separate from transport so semantic ranking,
 * persistence and consent behavior can be tested without network access or production tokens.
 */
interface EmbeddingClient {
    fun cancelPendingRequests()

    fun hasSignedInUser(): Boolean

    suspend fun healthCheck(): Boolean

    suspend fun embed(texts: List<String>, mode: String): List<FloatArray>
}
