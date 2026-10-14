package com.vvf.smartmanager.core.cloud.gdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveIndexingPolicyTest {
    @Test
    fun batchesAreBoundedToFiftyAndPreserveOrder() {
        val input = (1..121).toList()
        val batches = DriveIndexingPolicy.batches(input)
        assertEquals(listOf(50, 50, 21), batches.map { it.size })
        assertEquals(input, batches.flatten())
    }

    @Test
    fun emptyBatchInputProducesNoBatches() {
        assertTrue(DriveIndexingPolicy.batches(emptyList<Int>()).isEmpty())
    }

    @Test
    fun unchangedFilesAreSkippedButUnknownTimestampsAreReindexed() {
        val files = listOf(
            DriveFileDto(id = "same", modifiedTime = "2026-10-01T10:00:00Z"),
            DriveFileDto(id = "changed", modifiedTime = "2026-10-02T10:00:00Z"),
            DriveFileDto(id = "unknown", modifiedTime = null)
        )
        val changed = DriveIndexingPolicy.changedOnly(
            files,
            mapOf("same" to "2026-10-01T10:00:00Z", "changed" to "2026-10-01T10:00:00Z")
        )
        assertEquals(listOf("changed", "unknown"), changed.map { it.id })
    }

    @Test
    fun filesWithMissingIdsOrTimestampsAreAlwaysReindexed() {
        val files = listOf(
            DriveFileDto(id = null, modifiedTime = "2026-10-01T10:00:00Z"),
            DriveFileDto(id = "", modifiedTime = "2026-10-01T10:00:00Z"),
            DriveFileDto(id = "no-time", modifiedTime = null)
        )
        assertEquals(3, DriveIndexingPolicy.changedOnly(files, emptyMap()).size)
    }

    @Test
    fun missingModifiedTimeNeverCountsAsUnchanged() {
        assertTrue(DriveIndexingPolicy.isUnchanged(100L, 100L, null, null))
        assertTrue(!DriveIndexingPolicy.isUnchanged(0L, 0L, null, null))
        assertTrue(!DriveIndexingPolicy.isUnchanged(101L, 100L, "md5", "md5"))
        assertTrue(!DriveIndexingPolicy.isUnchanged(100L, 100L, "new", "old"))
        assertTrue(!DriveIndexingPolicy.isUnchanged(100L, null, "same", "same"))
    }

    @Test
    fun failedBatchDoesNotAdvanceCursor() {
        assertEquals(
            "cursor-1",
            DriveIndexingPolicy.cursorAfterBatch("cursor-1", "cursor-2", "cursor-final", batchCommitted = false)
        )
    }

    @Test
    fun committedBatchUsesNextPageThenFinalStartCursor() {
        assertEquals(
            "page-2",
            DriveIndexingPolicy.cursorAfterBatch("page-1", "page-2", "final-3", batchCommitted = true)
        )
        assertEquals(
            "final-3",
            DriveIndexingPolicy.cursorAfterBatch("page-1", null, "final-3", batchCommitted = true)
        )
        assertEquals(
            "page-1",
            DriveIndexingPolicy.cursorAfterBatch("page-1", " ", " ", batchCommitted = true)
        )
    }

    @Test
    fun blankCursorIsRejectedInsteadOfPersistingInvalidCheckpoint() {
        assertThrows(IllegalArgumentException::class.java) {
            DriveIndexingPolicy.cursorAfterBatch("", "page-2", "final-3", batchCommitted = true)
        }
    }

    @Test
    fun boundedIndexNeverExceedsTwentyThousand() {
        val files = (1..20_010).map { DriveFileDto(id = "drive-file-$it") }
        assertEquals(20_000, DriveIndexingPolicy.bounded(files).size)
        assertEquals(10, DriveIndexingPolicy.bounded(files, 10).size)
        assertEquals(1, DriveIndexingPolicy.bounded(files, 1).size)
    }

    @Test
    fun invalidBoundIsRejected() {
        val files = listOf(DriveFileDto(id = "one"))
        assertThrows(IllegalArgumentException::class.java) {
            DriveIndexingPolicy.bounded(files, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            DriveIndexingPolicy.bounded(files, DriveIndexingPolicy.MAX_FILES + 1)
        }
    }
}
