package com.vvf.smartmanager.core.domain

import android.content.Context
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
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
            PDFTextStripper().getText(document).take(MAX_EXTRACTED_CHARACTERS)
        }

    private fun extractWordDocument(file: File): String = ZipFile(file).use { zip ->
        val parts = zip.entries().asSequence()
            .take(MAX_ZIP_ENTRIES)
            .filter { entry ->
                !entry.isDirectory && (
                    entry.name == "word/document.xml" ||
                        entry.name.startsWith("word/header") && entry.name.endsWith(".xml") ||
                        entry.name.startsWith("word/footer") && entry.name.endsWith(".xml") ||
                        entry.name == "word/footnotes.xml" ||
                        entry.name == "word/endnotes.xml"
                    )
            }
            .take(MAX_OOXML_PARTS)
            .sortedBy { it.name }
            .toList()
        val output = StringBuilder()
        for (entry in parts) {
            if (output.length >= MAX_EXTRACTED_CHARACTERS) break
            val bytes = zip.getInputStream(entry).use { readZipEntryBounded(it) }
            output.append(xmlText(bytes).take(MAX_EXTRACTED_CHARACTERS - output.length)).append(' ')
        }
        output.toString().take(MAX_EXTRACTED_CHARACTERS)
    }

    private fun extractSpreadsheet(file: File): String = ZipFile(file).use { zip ->
        val sharedStrings = zip.getEntry("xl/sharedStrings.xml")?.let { entry ->
            xmlTextNodes(zip.getInputStream(entry).use { readZipEntryBounded(it) }, "t")
        }.orEmpty()
        val sheets = zip.entries().asSequence()
            .take(MAX_ZIP_ENTRIES)
            .filter { !it.isDirectory && Regex("xl/worksheets/sheet[0-9]+\\.xml").matches(it.name) }
            .take(MAX_OOXML_PARTS)
            .sortedBy { it.name }
            .toList()
        val output = StringBuilder()
        for (sheet in sheets) {
            if (output.length >= MAX_EXTRACTED_CHARACTERS) break
            val document = parseXml(zip.getInputStream(sheet).use { readZipEntryBounded(it) })
            val cells = document.getElementsByTagNameNS("*", "c")
            for (index in 0 until cells.length) {
                if (output.length >= MAX_EXTRACTED_CHARACTERS) break
                val cell = cells.item(index) as? org.w3c.dom.Element ?: continue
                val valueNode = cell.getElementsByTagNameNS("*", "v").item(0)
                val value = valueNode?.textContent?.trim().orEmpty()
                if (value.isBlank()) continue
                val resolved = if (cell.getAttribute("t") == "s") {
                    sharedStrings.getOrNull(value.toIntOrNull() ?: -1).orEmpty()
                } else value
                if (resolved.isNotBlank()) output.append(resolved.take(MAX_EXTRACTED_CHARACTERS - output.length)).append(' ')
            }
            for (value in xmlTextNodesFromDocument(document, "t")) {
                if (output.length >= MAX_EXTRACTED_CHARACTERS) break
                output.append(value.take(MAX_EXTRACTED_CHARACTERS - output.length)).append(' ')
            }
        }
        output.toString().take(MAX_EXTRACTED_CHARACTERS)
    }

    private fun extractPresentation(file: File): String = ZipFile(file).use { zip ->
        val slides = zip.entries().asSequence()
            .take(MAX_ZIP_ENTRIES)
            .filter { !it.isDirectory && Regex("ppt/slides/slide[0-9]+\\.xml").matches(it.name) }
            .take(MAX_OOXML_PARTS)
            .sortedBy { it.name }
            .toList()
        val output = StringBuilder()
        for (slide in slides) {
            if (output.length >= MAX_EXTRACTED_CHARACTERS) break
            val bytes = zip.getInputStream(slide).use { readZipEntryBounded(it) }
            output.append(xmlText(bytes).take(MAX_EXTRACTED_CHARACTERS - output.length)).append(' ')
        }
        output.toString().take(MAX_EXTRACTED_CHARACTERS)
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
        val output = mutableListOf<String>()
        var total = 0
        for (index in 0 until nodes.length) {
            if (total >= MAX_EXTRACTED_CHARACTERS) break
            val value = nodes.item(index)?.textContent?.trim().orEmpty()
            if (value.isBlank()) continue
            val bounded = value.take(MAX_EXTRACTED_CHARACTERS - total)
            output += bounded
            total += bounded.length
        }
        return output
    }

    private fun readZipEntryBounded(input: InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            total += read
            if (total > MAX_XML_ENTRY_BYTES) {
                throw DriveTextExtractionException("Office XML entry exceeds the extraction safety limit.")
            }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun parseXml(bytes: ByteArray): org.w3c.dom.Document {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
            setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
            setFeature("http://xml.org/sax/features/external-general-entities", false)
            setFeature("http://xml.org/sax/features/external-parameter-entities", false)
            runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "") }
            runCatching { setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "") }
        }
        return factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    }

    companion object {
        const val MAX_FILE_BYTES = 50L * 1024L * 1024L
        const val MAX_EXTRACTED_CHARACTERS = 1_000_000
        private const val MAX_XML_ENTRY_BYTES = 8 * 1024 * 1024
        private const val MAX_ZIP_ENTRIES = 10_000
        private const val MAX_OOXML_PARTS = 2_000
        private val TEXT_EXTENSIONS = setOf("txt", "md", "csv", "json", "xml", "html", "htm", "log", "yaml", "yml")
    }
}
