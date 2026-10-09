package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.database.model.DriveIndexFileEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveSearchRankerTest {
    private fun file(
        id: String,
        name: String,
        text: String = "",
        modified: Long = 1L,
        status: String = "TEXT_INDEXED",
        starred: Boolean = false
    ) = DriveIndexFileEntity(
        driveFileId = id,
        name = name,
        mimeType = "text/plain",
        sizeBytes = text.length.toLong(),
        modifiedTimeMs = modified,
        extractedText = text,
        indexStatus = status,
        starred = starred
    )

    @Test
    fun exactFilenameRanksAboveBodyOnlyMatch() {
        val exact = file("file-id-0000000000000001", "AYUSH license.pdf")
        val body = file("file-id-0000000000000002", "scan-2026.txt", "AYUSH license renewal information")
        val results = DriveSearchRanker.rank("AYUSH license", listOf(body, exact))

        assertEquals(exact.driveFileId, results.first().file.driveFileId)
    }

    @Test
    fun keywordFallbackIgnoresSemanticScoresWhenEmbeddingsAreOff() {
        val keyword = file("file-id-0000000000000001", "board minutes", "quarterly meeting notes")
        val unrelated = file("file-id-0000000000000002", "holiday photo", "mountain landscape")
        val results = DriveSearchRanker.rank(
            query = "board minutes",
            files = listOf(unrelated, keyword),
            semanticCosineScores = mapOf(unrelated.driveFileId to 1.0f),
            embeddingsEnabled = false
        )

        assertEquals(keyword.driveFileId, results.first().file.driveFileId)
        assertTrue(results.first().bm25Score > 0.0)
    }

    @Test
    fun hybridUsesSpecifiedWeightsOnlyWhenEnabled() {
        val doc = file("file-id-0000000000000001", "project brief", "project timeline")
        val semantic = 0.8f
        val result = DriveSearchRanker.rank(
            query = "project",
            files = listOf(doc),
            semanticCosineScores = mapOf(doc.driveFileId to semantic),
            embeddingsEnabled = true
        ).single()

        val expected = 0.55 * ((semantic + 1.0) / 2.0) +
            0.25 * result.bm25Score +
            0.20 * result.metadataScore
        assertEquals(expected, result.score, 0.000001)
    }

    @Test
    fun remoteRemovedFilesAreExcluded() {
        val removed = file("file-id-0000000000000001", "project brief", status = "REMOTE_REMOVED")
        assertFalse(DriveSearchRanker.rank("project", listOf(removed)).any())
    }
}
