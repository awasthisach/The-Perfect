package com.vvf.smartmanager.plugin.clouddrivers

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Alias coverage for SPI fail-closed stubs (no fake remote success). */
class CloudDriversUnitTest {
    @Test
    fun stubsNeverReportSuccessfulAuth() = runBlocking {
        assertFalse(OneDriveDriverImpl().authenticate())
        assertFalse(DropboxDriverImpl().authenticate())
        assertTrue(OneDriveDriverImpl().listRemoteFiles("").isEmpty())
    }
}
