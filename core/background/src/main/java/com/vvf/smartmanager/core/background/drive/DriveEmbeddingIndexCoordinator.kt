package com.vvf.smartmanager.core.background.drive

import com.vvf.smartmanager.core.database.dao.DriveIndexDao
import com.vvf.smartmanager.core.domain.EmbeddingProvider
import com.vvf.smartmanager.core.domain.EmbeddingVectorCodec
import com.vvf.smartmanager.core.domain.EmbeddingMode

class DriveEmbeddingIndexCoordinator(
    private val driveIndexDao: DriveIndexDao,
    private val embeddingProvider: EmbeddingProvider,
    private val embeddingConsentGranted: () -> Boolean
) {
    suspend fun runBatch(): Result<Boolean> {
        if (!embeddingConsentGranted()) return Result.success(false)
        val backend = embeddingProvider.health().getOrNull() ?: return Result.success(false)
        if (!backend.mobileAuthEnabled || backend.model != EXPECTED_MODEL ||
            backend.version != EXPECTED_VERSION.toString() || backend.dimension != EXPECTED_DIMENSION
        ) return Result.success(false)

        val pending = driveIndexDao.getFilesNeedingEmbeddings(
            model = backend.model,
            version = EXPECTED_VERSION,
            dimension = backend.dimension,
            limit = BATCH_SIZE
        )
        if (pending.isEmpty()) return Result.success(false)
        val texts = pending.map { it.extractedText.take(MAX_TEXT_CHARS) }
        val batch = embeddingProvider.embed(texts, EmbeddingMode.DOCUMENT).getOrElse {
            return Result.failure(IllegalStateException("Authenticated embedding request failed."))
        }
        if (batch.vectors.size != pending.size) {
            return Result.failure(IllegalStateException("Embedding response count did not match the request."))
        }
        for (index in pending.indices) {
            if (!embeddingConsentGranted()) return Result.success(false)
            val vector = batch.vectors[index]
            if (vector.size != EXPECTED_DIMENSION || vector.any { !it.isFinite() }) {
                return Result.failure(IllegalStateException("Embedding response has an invalid vector."))
            }
            driveIndexDao.updateEmbeddingVector(
                driveFileId = pending[index].driveFileId,
                model = backend.model,
                version = EXPECTED_VERSION,
                dimension = backend.dimension,
                vector = EmbeddingVectorCodec.encode(vector)
            )
        }
        return Result.success(
            driveIndexDao.getFilesNeedingEmbeddings(
                model = backend.model,
                version = EXPECTED_VERSION,
                dimension = backend.dimension,
                limit = 1
            ).isNotEmpty()
        )
    }

    companion object {
        const val BATCH_SIZE = 32
        private const val MAX_TEXT_CHARS = 8_000
        private const val EXPECTED_MODEL = "gemini-embedding-2"
        private const val EXPECTED_VERSION = 3
        private const val EXPECTED_DIMENSION = 768
    }
}
