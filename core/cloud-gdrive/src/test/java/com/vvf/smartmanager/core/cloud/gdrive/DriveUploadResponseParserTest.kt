package com.vvf.smartmanager.core.cloud.gdrive

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class DriveUploadResponseParserTest {

    @Test
    fun parsesDriveUploadResponseWithIntegrityMetadata() {
        val response = DriveUploadResponseParser.parse(
            """
            {
              "id": "file-123",
              "name": "backup.db",
              "mimeType": "application/octet-stream",
              "size": "42",
              "md5Checksum": "d41d8cd98f00b204e9800998ecf8427e",
              "parents": ["folder-1"]
            }
            """.trimIndent()
        )

        assertNotNull(response)
        assertEquals("file-123", response?.id)
        assertEquals("42", response?.size)
        assertEquals("d41d8cd98f00b204e9800998ecf8427e", response?.md5Checksum)
        assertEquals(listOf("folder-1"), response?.parents)
    }

    @Test
    fun ignoresUnknownDriveFields() {
        val response = DriveUploadResponseParser.parse(
            """{"id":"file-123","unknown":"ignored"}"""
        )

        assertEquals("file-123", response?.id)
    }
}
