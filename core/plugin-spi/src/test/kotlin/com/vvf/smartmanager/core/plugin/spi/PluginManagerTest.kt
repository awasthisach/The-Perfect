package com.vvf.smartmanager.core.plugin.spi

import com.vvf.smartmanager.core.model.FileItem
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginManagerTest {
    @Test
    fun cloudDriverContractAcceptsBooleanUploadResult() = runTest {
        val driver = object : CloudDriverSPI {
            override val driverId = "test.cloud"
            override val displayName = "Test Cloud"
            override val iconResName = "test"
            override suspend fun authenticate() = true
            override suspend fun listRemoteFiles(remotePath: String) = emptyList<FileItem>()
            override suspend fun uploadFile(localFile: FileItem, remoteDirectory: String) = true
            override suspend fun downloadFile(remoteFile: FileItem, localDestination: String) = true
            override suspend fun getQuotaUsage() = 0L to 0L
        }

        assertTrue(driver.authenticate())
        assertEquals(true, driver.uploadFile(testFile(), "/"))
    }

    private fun testFile() = FileItem(
        name = "test.txt",
        path = "/tmp/test.txt",
        size = 12L,
        mimeType = "text/plain",
        lastModified = 0L,
        isDirectory = false
    )
}
