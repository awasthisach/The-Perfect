package com.vvf.smartmanager.core.domain

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.io.File
import java.nio.charset.StandardCharsets
import java.util.zip.ZipFile
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

class DriveTextExtractionException(message: String) : IllegalStateException(message)

/**
 * Extracts text locally from PDFs, Office Open XML, and plain text. It never uploads content.
 * Legacy binary Office formats and image OCR are intentionally handled as separate capabilities.
 */
class DriveTextExtractor(context: Context) {
    init {
        PDFBoxResourceLoader.init(context.applicationContext)
    }

    fun extract(file: File, mimeType: String): String {
        require(file.isFile && file.canRead()) { "Downloaded file is unavailable." }
        if (file.length() > MAX_FILE_BYTES) throw DriveTextExtractionException("File exceeds the local extraction limit.")
        val type = mimeType.lowercase()
        val extension = file.extension.lowercase()
        val text = when {
            type == "application/pdf" || extension == "pdf" -> extractPdf(file)
            type == "application/vnd.openxmlformats-officedocument.wordprocessingml.document" || extension == "docx" ->
                extractWordDocument(file)
            type == "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet" || extension == "xlsx" ->
                extractSpreadsheet(file)
            type == "application/vnd.openxmlformats-officedocument.presentationml.presentation" || extension == "pptx" ->
                extractPresentation(file)
            type.startsWith("text/") || type.contains("csv") || type.contains("json") ||
                type.contains("xml") || extension in TEXT_EXTENSIONS -> readText(file)
            type.startsWith("image/") -> throw DriveTextExtractionException("OCR consent is required before image text can be indexed.")
            type.startsWith("application/vnd.google-apps.") ->
                throw DriveTextExtractionException("Google Workspace file must be exported before local extraction.")
            else -> throw DriveTextExtractionException("This file format is not supported for text extraction.")
        }
        return text.take(MAX_EXTRACTED_CHARACTERS).trim()
    }

    private fun extractPdf(file: File): String =
        PDDocument.load(file).use { document ->
            val pageCount = document.numberOfPages
            val stripper = PDFTextStripper()
            if (pageCount <= PDF_FULL_PAGE_LIMIT) {
                stripper.startPage = 1
                stripper.endPage = pageCount
                stripper.getText(document).take(MAX_EXTRACTED_CHARACTERS)
            } else {
                val first = stripper.apply { startPage = 1; endPage = PDF_SAMPLE_PAGES }.getText(document)
                val last = stripper.apply {
                    startPage = (pageCount - PDF_SAMPLE_PAGES + 1).coerceAtLeast(1)
                    endPage = pageCount
                }.getText(document)
                (first + "\\n\\n[Middle pages omitted from local index]\\n\\n" + last)
                    .take(MAX_EXTRACTED_CHARACTERS)
            }
        }

    private fun extractWordDocument(file: File): String = ZipFile(file).use { zip ->
        val parts = zip.entries().asSequence()
            .filter { entry ->
                !entry.isDirectory && (
                    entry.name == "word/document.xml" ||
                        entry.name.startsWith("word/header") && entry.name.endsWith(".xml") ||
                        entry.name.startsWith("word/footer") && entry.name.endsWith(".xml") ||
                        entry.name == "word/footnotes.xml" ||
                        entry.name == "word/endnotes.xml"
                    )
            }
            .toList()
            .sortedBy { it.name }
        parts.joinToString(" ") { entry -> xmlText(readZipEntryBounded(zip, entry)) }
    }

    private fun extractSpreadsheet(file: File): String = ZipFile(file).use { zip ->
        val sharedStrings = zip.getEntry("xl/sharedStrings.xml")?.let { entry ->
            xmlTextNodes(readZipEntryBounded(zip, entry), "t")
        }.orEmpty()
        val sheets = zip.entries().asSequence()
            .filter { !it.isDirectory && Regex("xl/worksheets/sheet[0-9]+\\.xml").matches(it.name) }
            .sortedBy { it.name }
            .toList()
        val values = mutableListOf<String>()
        for (sheet in sheets) {
            val document = parseXml(readZipEntryBounded(zip, sheet))
            val cells = document.getElementsByTagNameNS("*", "c")
            for (index in 0 until cells.length) {
                val cell = cells.item(index) as? org.w3c.dom.Element ?: continue
                val valueNode = cell.getElementsByTagNameNS("*", "v").item(0)
                val value = valueNode?.textContent?.trim().orEmpty()
                if (value.isBlank()) continue
                val resolved = if (cell.getAttribute("t") == "s") {
                    sharedStrings.getOrNull(value.toIntOrNull() ?: -1).orEmpty()
                } else value
                if (resolved.isNotBlank()) values += resolved
            }
            values += xmlTextNodesFromDocument(document, "t")
            if (values.sumOf { it.length } >= MAX_EXTRACTED_CHARACTERS) break
        }
        values.joinToString(" ").take(MAX_EXTRACTED_CHARACTERS)
    }

    private fun extractPresentation(file: File): String = ZipFile(file).use { zip ->
        zip.entries().asSequence()
            .filter { !it.isDirectory && Regex("ppt/slides/slide[0-9]+\\.xml").matches(it.name) }
            .sortedBy { it.name }
            .joinToString(" ") { entry ->
                xmlTextNodes(readZipEntryBounded(zip, entry), "t")
            }
    }

    private fun readZipEntryBounded(zip: ZipFile, entry: java.util.zip.ZipEntry): ByteArray {
        val declaredSize = entry.size
        if (declaredSize > MAX_XML_ENTRY_BYTES.toLong()) {
            throw DriveTextExtractionException("Office document contains an oversized XML part.")
        }
        return zip.getInputStream(entry).use { input ->
            val output = java.io.ByteArrayOutputStream(
                if (declaredSize in 1L..MAX_XML_ENTRY_BYTES.toLong()) declaredSize.toInt() else 8192
            )
            val buffer = ByteArray(8192)
            var total = 0
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                total += count
                if (total > MAX_XML_ENTRY_BYTES) {
                    throw DriveTextExtractionException("Office document XML part exceeds the safe extraction limit.")
                }
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
    }

    private fun readText(file: File): String =
        file.inputStream().bufferedReader(StandardCharsets.UTF_8).use { reader ->
            val builder = StringBuilder()
            val buffer = CharArray(8192)
            while (builder.length < MAX_EXTRACTED_CHARACTERS) {
                val read = reader.read(buffer, 0, minOf(buffer.size, MAX_EXTRACTED_CHARACTERS - builder.length))
                if (read < 0) break
                builder.append(buffer, 0, read)
            }
            builder.toString()
        }

    private fun xmlText(bytes: ByteArray): String = xmlTextNodesFromDocument(parseXml(bytes), "t")

    private fun xmlTextNodes(bytes: ByteArray, localName: String): List<String> =
        xmlTextNodesFromDocument(parseXml(bytes), localName)

    private fun xmlTextNodesFromDocument(document: org.w3c.dom.Document, localName: String): List<String> {
        val nodes = document.getElementsByTagNameNS("*", localName)
        return (0 until nodes.length).mapNotNull { index ->
            nodes.item(index)?.textContent?.trim()?.takeIf { it.isNotEmpty() }
        }
    }

    private fun parseXml(bytes: ByteArray): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "")
            setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "")
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    companion object {
        const val MAX_FILE_BYTES = 50L * 1024L * 1024L
        const val MAX_EXTRACTED_CHARACTERS = 1_000_000
        private const val MAX_XML_ENTRY_BYTES = 4 * 1024 * 1024
        private const val PDF_FULL_PAGE_LIMIT = 10
        private const val PDF_SAMPLE_PAGES = 5
        private val TEXT_EXTENSIONS = setOf("txt", "md", "csv", "json", "xml", "html", "htm", "log", "yaml", "yml")
    }
}
