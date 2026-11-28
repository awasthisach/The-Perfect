package com.vvf.smartmanager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimaryStorageIndexPolicyTest {
    @Test
    fun emptyAndPartialScansMayPruneStaleRows() {
        assertTrue(shouldPruneStalePrimaryIndexRows(0, scanCompleted = true))
        assertTrue(shouldPruneStalePrimaryIndexRows(PRIMARY_STORAGE_INDEX_LIMIT - 1, scanCompleted = true))
        assertFalse(shouldPruneStalePrimaryIndexRows(0, scanCompleted = false))
        assertFalse(shouldPruneStalePrimaryIndexRows(PRIMARY_STORAGE_INDEX_LIMIT - 1, scanCompleted = false))
    }

    @Test
    fun scanAtLimitMustPreservePreviouslyIndexedRows() {
        assertFalse(shouldPruneStalePrimaryIndexRows(PRIMARY_STORAGE_INDEX_LIMIT, scanCompleted = true))
        assertFalse(shouldPruneStalePrimaryIndexRows(PRIMARY_STORAGE_INDEX_LIMIT + 1, scanCompleted = true))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeScanCountIsRejected() {
        shouldPruneStalePrimaryIndexRows(-1, scanCompleted = true)
    }
}
