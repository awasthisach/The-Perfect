package com.vvf.smartmanager.core.cloud.gdrive

import org.junit.Assert.assertEquals
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
    }

    @Test
    fun boundedIndexNeverExceedsTwentyThousand() {
        val files = (1..20_010).map { DriveFileDto(id = "drive-file-$it") }
        assertEquals(20_000, DriveIndexingPolicy.bounded(files).size)
        assertTrue(DriveIndexingPolicy.bounded(files, 10).size == 10)
    }
}
