package com.vvf.smartmanager.core.domain

data class EmbeddingBackendInfo(
    val model: String,
    val version: String,
    val dimension: Int,
    val mobileAuthEnabled: Boolean
)

data class EmbeddingBatch(val vectors: List<FloatArray>)

enum class EmbeddingMode(val wireValue: String) {
    QUERY("query"),
    DOCUMENT("document")
}

/** Secure embedding contract. Implementations must require user consent and Firebase/App Check auth. */
interface EmbeddingProvider {
    suspend fun health(): Result<EmbeddingBackendInfo>
    suspend fun embed(texts: List<String>, mode: EmbeddingMode): Result<EmbeddingBatch>
}

class DisabledEmbeddingProvider : EmbeddingProvider {
    override suspend fun health(): Result<EmbeddingBackendInfo> =
        Result.success(EmbeddingBackendInfo("", "", 0, mobileAuthEnabled = false))

    override suspend fun embed(texts: List<String>, mode: EmbeddingMode): Result<EmbeddingBatch> =
        Result.failure(IllegalStateException("Authenticated embedding backend is unavailable."))
}
