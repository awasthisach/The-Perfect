package com.vvf.smartmanager

import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveOfficeTextExtractorTest {

    @Test
    fun extractsWordTextAndDecodesXmlEntities() {
        val file = temporaryZip(
            "word/document.xml" to """<?xml version="1.0"?><w:document><w:body><w:p><w:r><w:t>Procurement &amp; Finance</w:t></w:r></w:p></w:body></w:document>"""
        )
        try {
            val text = DriveOfficeTextExtractor.extract(file, DriveOfficeTextExtractor.DOCX)
            assertTrue(text.contains("Procurement & Finance"))
        } finally {
            file.delete()
        }
    }

    @Test
    fun extractsSpreadsheetSharedStringsAndCellValues() {
        val file = temporaryZip(
            "xl/sharedStrings.xml" to """<sst><si><t>Annual report</t></si><si><t>Reviewed</t></si></sst>""",
            "xl/worksheets/sheet1.xml" to """<worksheet><sheetData><row r="1"><c r="A1" t="s"><v>0</v></c><c r="B1" t="s"><v>1</v></c><c r="C1"><v>2026</v></c></row></sheetData></worksheet>"""
        )
        try {
            val text = DriveOfficeTextExtractor.extract(file, DriveOfficeTextExtractor.XLSX)
            assertTrue(text.contains("Annual report"))
            assertTrue(text.contains("Reviewed"))
            assertTrue(text.contains("2026"))
        } finally {
            file.delete()
        }
    }

    private fun temporaryZip(vararg entries: Pair<String, String>): File {
        val file = File.createTempFile("drive-office-test", ".zip")
        ZipOutputStream(FileOutputStream(file)).use { zip ->
            entries.forEach { (name, content) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(content.toByteArray(Charsets.UTF_8))
                zip.closeEntry()
            }
        }
        return file
    }
}
