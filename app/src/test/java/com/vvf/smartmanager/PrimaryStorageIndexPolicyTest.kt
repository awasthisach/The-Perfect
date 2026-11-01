package com.vvf.smartmanager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PrimaryStorageIndexPolicyTest {
    @Test
    fun emptyAndPartialScansMayPruneStaleRows() {
        assertTrue(shouldPruneStalePrimaryIndexRows(0))
        assertTrue(shouldPruneStalePrimaryIndexRows(PRIMARY_STORAGE_INDEX_LIMIT - 1))
    }

    @Test
    fun scanAtLimitMustPreservePreviouslyIndexedRows() {
        assertFalse(shouldPruneStalePrimaryIndexRows(PRIMARY_STORAGE_INDEX_LIMIT))
        assertFalse(shouldPruneStalePrimaryIndexRows(PRIMARY_STORAGE_INDEX_LIMIT + 1))
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeScanCountIsRejected() {
        shouldPruneStalePrimaryIndexRows(-1)
    }
}
