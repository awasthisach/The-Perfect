package com.vvf.smartmanager.plugin.clouddrivers

import com.vvf.smartmanager.core.model.FileItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudDriverPluginsTest {
    @Test
    fun oneDriveIsFailClosedStub() = runBlocking {
        val driver = OneDriveDriverImpl()
        assertFalse(driver.authenticate())
        assertTrue(driver.listRemoteFiles("/").isEmpty())
        assertFalse(
            driver.uploadFile(
                FileItem("/tmp/a", "a", 1L, 0L, false, null),
                "/"
            )
        )
        assertEquals(0L to 0L, driver.getQuotaUsage())
    }

    @Test
    fun dropboxIsFailClosedStub() = runBlocking {
        val driver = DropboxDriverImpl()
        assertFalse(driver.authenticate())
        assertTrue(driver.listRemoteFiles("/").isEmpty())
    }

    @Test
    fun nextCloudS3NasAreFailClosedStubs() = runBlocking {
        listOf(NextCloudDriverImpl(), S3StorageDriverImpl(), LocalNasDriverImpl()).forEach { driver ->
            assertFalse(driver.authenticate())
            assertTrue(driver.listRemoteFiles("/").isEmpty())
            assertFalse(driver.downloadFile(FileItem("r", "r", 1L, 0L, false, null), "/tmp/x"))
        }
    }
}
