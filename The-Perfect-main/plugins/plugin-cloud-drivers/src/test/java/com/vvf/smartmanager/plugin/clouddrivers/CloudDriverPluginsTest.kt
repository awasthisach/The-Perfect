package com.vvf.smartmanager.plugin.clouddrivers

import com.vvf.smartmanager.core.model.FileItem
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CloudDriverPluginsTest {

    @Test
    fun testOneDriveDriverLifecycle() = runBlocking {
        val driver = OneDriveDriverImpl()
        assertEquals("plugin.cloud.onedrive", driver.driverId)
        
        val auth = driver.authenticate()
        assertTrue(auth)

        val files = driver.listRemoteFiles("/")
        assertTrue(files.isNotEmpty())

        val (used, total) = driver.getQuotaUsage()
        assertTrue(total == 5_000_000_000L)
        assertTrue(used > 0L)
    }

    @Test
    fun testDropboxDriverLifecycle() = runBlocking {
        val driver = DropboxDriverImpl()
        assertEquals("plugin.cloud.dropbox", driver.driverId)

        val auth = driver.authenticate()
        assertTrue(auth)

        val files = driver.listRemoteFiles("/")
        assertTrue(files.isNotEmpty())

        val testFile = FileItem(
            path = "/storage/emulated/0/DCIM/photo.jpg",
            name = "photo.jpg",
            sizeBytes = 1000L,
            lastModified = 0L,
            isDirectory = false,
            mimeType = "image/jpeg"
        )
        val uploaded = driver.uploadFile(testFile, "/Photos")
        assertTrue(uploaded)
    }

    @Test
    fun testNextCloudDriverLifecycle() = runBlocking {
        val driver = NextCloudDriverImpl()
        assertEquals("plugin.cloud.nextcloud", driver.driverId)

        val auth = driver.authenticate()
        assertTrue(auth)

        val files = driver.listRemoteFiles("/")
        assertTrue(files.isNotEmpty())
    }
}
