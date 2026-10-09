package com.vvf.smartmanager.core.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vvf.smartmanager.core.database.VVFDatabase
import com.vvf.smartmanager.core.database.model.DriveIndexFileEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DriveSemanticSearchCoordinatorTest {
    private lateinit var database: VVFDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = VVFDatabase.buildInMemoryDatabase(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private class FakeEmbeddingProvider : EmbeddingProvider {
        override suspend fun health() =
            Result.success(EmbeddingBackendInfo("gemini-embedding-2", "3", 768, true))

        override suspend fun embed(texts: List<String>, mode: EmbeddingMode): Result<EmbeddingBatch> {
            val vector = FloatArray(768)
            vector[0] = 1f
            return Result.success(EmbeddingBatch(listOf(vector)))
        }
    }

    @Test
    fun semanticOnlyCandidatesAppearWhenKeywordSearchHasNoHits() = runBlocking {
        val dao = database.driveIndexDao()
        val aligned = FloatArray(768).apply { this[0] = 1f }
        val orthogonal = FloatArray(768).apply { this[1] = 1f }
        dao.upsertFile(
            DriveIndexFileEntity(
                driveFileId = "semanticAlignedFileId_1234567890",
                name = "Alpha document",
                mimeType = "text/plain",
                sizeBytes = 10L,
                modifiedTimeMs = 100L,
                extractedText = "alpha content",
                embeddingVector = EmbeddingVectorCodec.encode(aligned),
                embeddingModel = "gemini-embedding-2",
                embeddingVersion = 3,
                embeddingDimension = 768,
                indexStatus = "TEXT_INDEXED"
            )
        )
        dao.upsertFile(
            DriveIndexFileEntity(
                driveFileId = "semanticOrthogonalFileId_123456789",
                name = "Beta document",
                mimeType = "text/plain",
                sizeBytes = 10L,
                modifiedTimeMs = 90L,
                extractedText = "beta content",
                embeddingVector = EmbeddingVectorCodec.encode(orthogonal),
                embeddingModel = "gemini-embedding-2",
                embeddingVersion = 3,
                embeddingDimension = 768,
                indexStatus = "TEXT_INDEXED"
            )
        )
        val local = DriveSearchRepository(dao, { true }, { true })
        val coordinator = DriveSemanticSearchCoordinator(dao, local, FakeEmbeddingProvider(), { true }, { true })

        val results = coordinator.search("completely unrelated concept")

        assertEquals(2, results.size)
        assertEquals("semanticAlignedFileId_1234567890", results.first().file.driveFileId)
        assertTrue(results.first().score > results.last().score)
    }
}
