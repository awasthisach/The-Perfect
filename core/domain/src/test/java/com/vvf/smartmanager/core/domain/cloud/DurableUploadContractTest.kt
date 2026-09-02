package com.vvf.smartmanager.core.domain.cloud

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DurableUploadContractTest {
    @Test
    fun rejectsNullBlankAndWhitespace() {
        assertFalse(DurableUploadContract.isDurableRemoteId(null))
        assertFalse(DurableUploadContract.isDurableRemoteId(""))
        assertFalse(DurableUploadContract.isDurableRemoteId("   "))
    }

    @Test
    fun acceptsNonBlankId() {
        assertTrue(DurableUploadContract.isDurableRemoteId("drive-abc"))
        assertEquals("drive-abc", DurableUploadContract.requireDurableRemoteId("  drive-abc  "))
    }

    @Test(expected = IllegalArgumentException::class)
    fun requireThrowsOnBlank() {
        DurableUploadContract.requireDurableRemoteId("  ")
    }
}
