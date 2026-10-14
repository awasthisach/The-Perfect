package com.vvf.smartmanager

import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipFile

/**
 * Small, dependency-free extractor for OOXML text parts. It does not execute macros,
 * resolve external entities, or trust document-provided URLs. Inputs and extracted text
 * are bounded because Drive files are untrusted data.
 */
internal object DriveOfficeTextExtractor {
    private const val MAX_ENTRY_BYTES = 2 * 1024 * 1024
    private const val MAX_TOTAL_BYTES = 20 * 1024 * 1024
    private const val MAX_TEXT_CHARS = 250_000

    fun extract(file: File, mimeType: String): String {
        require(file.isFile && file.length() in 1..MAX_TOTAL_BYTES.toLong()) {
            "Office document exceeds the 20 MiB extraction limit"
        }
        ZipFile(file).use { zip ->
            val entries = zip.entries().asSequence()
                .filter { !it.isDirectory && it.name.endsWith(".xml", ignoreCase = true) }
                .toList()
            var totalBytes = 0
            fun readEntry(name: String): String? {
                val entry = zip.getEntry(name) ?: return null
                require(!entry.isDirectory && (entry.size < 0 || entry.size <= MAX_ENTRY_BYTES)) {
                    "Office XML part exceeds the extraction limit"
                }
                val bytes = zip.getInputStream(entry).use { input ->
                    val out = ByteArrayOutputStream()
                    val buffer = ByteArray(8192)
                    var read = 0
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        read += count
                        totalBytes += count
                        require(read <= MAX_ENTRY_BYTES && totalBytes <= MAX_TOTAL_BYTES) {
                            "Office document expands beyond the extraction limit"
                        }
                        out.write(buffer, 0, count)
                    }
                    out.toByteArray()
                }
                return bytes.toString(Charsets.UTF_8)
            }

            val text = when (mimeType.lowercase()) {
                DOCX -> {
                    val names = entries.map { it.name }.filter {
                        it == "word/document.xml" ||
                            it.matches(Regex("word/(header|footer)\\d+\\.xml")) ||
                            it == "word/footnotes.xml" || it == "word/endnotes.xml"
                    }.sorted()
                    names.mapNotNull(::readEntry).joinToString("\n") { extractTextNodes(it, "w:t") }
                }
                PPTX -> entries.map { it.name }
                    .filter { it.matches(Regex("ppt/slides/slide\\d+\\.xml")) }
                    .sortedBy { name -> Regex("slide(\\d+)\\.xml").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE }
                    .mapNotNull(::readEntry)
                    .joinToString("\n") { extractTextNodes(it, "a:t") }
                XLSX -> {
                    val sharedStringsXml = readEntry("xl/sharedStrings.xml")
                    val sharedStrings = sharedStringsXml?.let { xml ->
                        Regex("<si\\b[^>]*>(.*?)</si>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                            .findAll(xml)
                            .map { extractTextNodes(it.groupValues[1], "t") }
                            .toList()
                    }.orEmpty()
                    entries.map { it.name }
                        .filter { it.matches(Regex("xl/worksheets/sheet\\d+\\.xml")) }
                        .sortedBy { name -> Regex("sheet(\\d+)\\.xml").find(name)?.groupValues?.get(1)?.toIntOrNull() ?: Int.MAX_VALUE }
                        .mapNotNull(::readEntry)
                        .joinToString("\n") { xml -> extractSpreadsheetRows(xml, sharedStrings) }
                }
                else -> throw IllegalArgumentException("Unsupported Office Open XML type")
            }
            return text.trim().take(MAX_TEXT_CHARS)
        }
    }

    private fun extractTextNodes(xml: String, tag: String): String =
        Regex("<$tag\\b[^>]*>(.*?)</$tag>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(xml)
            .joinToString(" ") { decodeXmlEntities(it.groupValues[1]) }

    private fun extractSpreadsheetRows(xml: String, sharedStrings: List<String>): String {
        val rows = Regex("<row\\b[^>]*>(.*?)</row>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
            .findAll(xml)
            .map { row ->
                Regex("<c\\b([^>]*)>(.*?)</c>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                    .findAll(row.groupValues[1])
                    .joinToString("\t") { cell ->
                        val attributes = cell.groupValues[1]
                        val body = cell.groupValues[2]
                        val raw = Regex("<v\\b[^>]*>(.*?)</v>", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
                            .find(body)?.groupValues?.get(1)?.let(::decodeXmlEntities).orEmpty()
                        if (Regex("""\bt=["']s["']""").containsMatchIn(attributes)) {
                            sharedStrings.getOrNull(raw.toIntOrNull() ?: -1).orEmpty()
                        } else if (Regex("""\bt=["']inlineStr["']""").containsMatchIn(attributes)) {
                            extractTextNodes(body, "t")
                        } else raw
                    }
            }
        return rows.joinToString("\n")
    }

    private fun decodeXmlEntities(value: String): String =
        value.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&amp;", "&")

    const val DOCX = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    const val XLSX = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
    const val PPTX = "application/vnd.openxmlformats-officedocument.presentationml.presentation"
}
