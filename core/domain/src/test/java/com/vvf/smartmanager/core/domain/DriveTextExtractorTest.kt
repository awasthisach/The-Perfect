package com.vvf.smartmanager.core.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DriveTextExtractorTest {
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val extractor by lazy { DriveTextExtractor(context) }

    @Test
    fun extractsPlainTextLocally() {
        val file = File(context.cacheDir, "drive-extract-test.txt")
        file.writeText("Local-only text for semantic search.")
        try {
            assertEquals("Local-only text for semantic search.", extractor.extract(file, "text/plain"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun extractsTextFromOfficeOpenXmlDocument() {
        val file = File(context.cacheDir, "drive-extract-test.docx")
        val xml = """<?xml version="1.0" encoding="UTF-8"?>
            <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
              <w:body><w:p><w:r><w:t>Offline Office extraction</w:t></w:r></w:p></w:body>
            </w:document>""".trimIndent()
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            zip.putNextEntry(ZipEntry("word/document.xml"))
            zip.write(xml.toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
        try {
            assertEquals("Offline Office extraction", extractor.extract(
                file,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            ))
        } finally {
            file.delete()
        }
    }

    @Test
    fun imageTextExtractionRequiresExplicitConsentPath() {
        val file = File(context.cacheDir, "drive-extract-test.png")
        file.writeBytes(byteArrayOf(1, 2, 3))
        try {
            assertThrows(DriveTextExtractionException::class.java) {
                extractor.extract(file, "image/png")
            }
        } finally {
            file.delete()
        }
    }
}
