package com.vvf.smartmanager.plugin.clouddrivers

import com.vvf.smartmanager.core.model.FileItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudDriversUnitTest {
    private fun assertUnconfigured(driver: com.vvf.smartmanager.core.plugin.spi.CloudDriverSPI) = runBlocking {
        assertFalse(driver.authenticate())
        assertTrue(driver.listRemoteFiles("/").isEmpty())
        val file = FileItem("/tmp/test.bin", "test.bin", 1L, 0L, false)
        assertFalse(driver.uploadFile(file, "/"))
        assertFalse(driver.downloadFile(file, "/tmp/out.bin"))
        assertEquals(0L, driver.getQuotaUsage().first)
        assertEquals(0L, driver.getQuotaUsage().second)
    }

    @Test fun oneDriveFailsClosed() { assertUnconfigured(OneDriveDriverImpl()) }
    @Test fun dropboxFailsClosed() { assertUnconfigured(DropboxDriverImpl()) }
    @Test fun nextCloudFailsClosed() { assertUnconfigured(NextCloudDriverImpl()) }
    @Test fun s3FailsClosed() { assertUnconfigured(S3StorageDriverImpl()) }
    @Test fun nasFailsClosed() { assertUnconfigured(LocalNasDriverImpl()) }
}
