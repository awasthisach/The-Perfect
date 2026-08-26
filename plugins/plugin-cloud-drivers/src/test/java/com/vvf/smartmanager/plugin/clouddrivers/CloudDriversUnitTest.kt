package com.vvf.smartmanager.plugin.clouddrivers

import com.vvf.smartmanager.core.model.FileItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudDriversUnitTest {

    @Test
    fun testOneDriveDriverLifecycle() = runBlocking {
        val driver = OneDriveDriverImpl()
        assertEquals("plugin.cloud.onedrive", driver.driverId)
        assertEquals("Microsoft OneDrive", driver.displayName)

        // Before authentication
        val filesBeforeAuth = driver.listRemoteFiles("/")
        assertTrue(filesBeforeAuth.isEmpty())

        // Authenticate
        val authResult = driver.authenticate()
        assertTrue(authResult)

        // List files
        val filesAfterAuth = driver.listRemoteFiles("/")
        assertEquals(1, filesAfterAuth.size)
        assertEquals("OfficeReport_2026.docx", filesAfterAuth[0].name)

        // Upload file
        val dummyFile = FileItem(
            path = "/storage/emulated/0/docs/test.docx",
            name = "test.docx",
            sizeBytes = 500000L,
            lastModified = System.currentTimeMillis(),
            isDirectory = false,
            mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
        )
        val uploadSuccess = driver.uploadFile(dummyFile, "Documents")
        assertTrue(uploadSuccess)

        // Quota
        val (used, total) = driver.getQuotaUsage()
        assertTrue(used > 1_500_000_000L)
        assertEquals(5_000_000_000L, total)
    }

    @Test
    fun testDropboxDriverLifecycle() = runBlocking {
        val driver = DropboxDriverImpl()
        assertEquals("plugin.cloud.dropbox", driver.driverId)
        assertEquals("Dropbox", driver.displayName)

        assertTrue(driver.authenticate())
        val files = driver.listRemoteFiles("/")
        assertEquals(1, files.size)
        assertEquals("Family_Vacation.jpg", files[0].name)

        val (used, total) = driver.getQuotaUsage()
        assertEquals(800_000_000L, used)
        assertEquals(2_000_000_000L, total)
    }

    @Test
    fun testNextCloudDriverLifecycle() = runBlocking {
        val driver = NextCloudDriverImpl()
        assertEquals("plugin.cloud.nextcloud", driver.driverId)
        assertEquals("NextCloud (Self-Hosted)", driver.displayName)

        assertTrue(driver.authenticate())
        val files = driver.listRemoteFiles("/")
        assertEquals(1, files.size)
        assertEquals("Nextcloud_Sync_Archive.zip", files[0].name)
    }

    @Test
    fun testS3DriverLifecycle() = runBlocking {
        val driver = S3StorageDriverImpl()
        assertEquals("plugin.cloud.s3", driver.driverId)
        assertEquals("AWS S3 / S3-Compatible", driver.displayName)

        assertTrue(driver.authenticate())
        val files = driver.listRemoteFiles("/")
        assertEquals(1, files.size)
        assertEquals("system_logs_2026.log", files[0].name)
    }

    @Test
    fun testLocalNasDriverLifecycle() = runBlocking {
        val driver = LocalNasDriverImpl()
        assertEquals("plugin.cloud.nas", driver.driverId)
        assertEquals("Local NAS (WebDAV/SMB)", driver.displayName)

        assertTrue(driver.authenticate())
        val files = driver.listRemoteFiles("/")
        assertEquals(1, files.size)
        assertEquals("Media_Library_Backup.iso", files[0].name)
    }
}
