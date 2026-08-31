package com.vvf.smartmanager.plugin.clouddrivers

import com.vvf.smartmanager.core.model.FileItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudDriverPluginsTest {
    private val sample = FileItem(
        path = "/tmp/sample.txt",
        name = "sample.txt",
        sizeBytes = 10L,
        lastModified = 0L,
        isDirectory = false,
        mimeType = "text/plain"
    )

    @Test
    fun allPlaceholderDrivers_failClosed() = runBlocking {
        val drivers = listOf(
            OneDriveDriverImpl(),
            DropboxDriverImpl(),
            NextCloudDriverImpl(),
            S3StorageDriverImpl(),
            LocalNasDriverImpl()
        )

        drivers.forEach { driver ->
            assertFalse("${driver.displayName} must not claim authentication", driver.authenticate())
            assertTrue("${driver.displayName} must not expose synthetic files", driver.listRemoteFiles("root").isEmpty())
            assertFalse("${driver.displayName} must reject uploads", driver.uploadFile(sample, "root"))
            assertFalse("${driver.displayName} must reject downloads", driver.downloadFile(sample, "/tmp/out"))
            assertEquals("${driver.displayName} must not report synthetic quota", 0L to 0L, driver.getQuotaUsage())
        }
    }
}
