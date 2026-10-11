package com.vvf.smartmanager

import android.content.Context
import android.net.Uri
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.InputStream

/**
 * User-directed JSON backup/restore of the local Drive search index only.
 * OAuth/Firebase tokens, API keys, local paths and file bytes are never exported.
 */
class DriveIndexBackupManager(private val context: Context) {
    suspend fun export(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val app = context.applicationContext as VVFApplication
            val fullContentConsent = app.isDriveFullContentConsentEnabled()
            val rows = app.database.fileDao().getDriveIndexRows()
            val exportText: (FileMetadataEntity) -> String = { row ->
                if (fullContentConsent || !requiresFullContentConsent(row.mimeType, row.name)) row.contentText else ""
            }
            require(rows.size <= MAX_FILES) { "Drive index exceeds the 20,000-file backup limit" }
            require(rows.sumOf { exportText(it).length.toLong() } <= MAX_TOTAL_TEXT_CHARS) {
                "Extracted text exceeds the JSON backup safety limit; clear or reduce the index first"
            }
            val files = JSONArray()
            rows.forEach { row ->
                val id = row.path.removePrefix(DRIVE_PREFIX)
                require(isValidDriveId(id)) { "Invalid Drive ID in local index; export aborted" }
                files.put(
                    JSONObject()
                        .put("id", id)
                        .put("name", row.name)
                        .put("mimeType", row.mimeType)
                        .put("sizeBytes", row.sizeBytes)
                        .put("modifiedDate", row.modifiedDate)
                        .put("md5Hash", row.md5Hash ?: JSONObject.NULL)
                        .put("isDirectory", row.isDirectory)
                        .put("isFavorite", row.isFavorite)
                        .put("contentText", exportText(row))
                        .put("canonicalUri", row.canonicalUri ?: JSONObject.NULL)
                )
            }
            val accountEmail = context.getSharedPreferences("drive_search_index", Context.MODE_PRIVATE)
                .getString("account_email", "")
                .orEmpty()
            val root = JSONObject()
                .put("format", FORMAT_NAME)
                .put("schemaVersion", SCHEMA_VERSION)
                .put("exportedAt", System.currentTimeMillis())
                .put("accountEmail", accountEmail)
                .put("files", files)
            val bytes = root.toString().toByteArray(Charsets.UTF_8)
            require(bytes.size <= MAX_BACKUP_BYTES) { "JSON backup exceeds the 50 MiB limit" }
            val output = context.contentResolver.openOutputStream(uri, "wt")
                ?: throw IllegalArgumentException("Could not open the selected backup destination")
            output.use { it.write(bytes); it.flush() }
            Result.success(rows.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun import(uri: Uri): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val input = context.contentResolver.openInputStream(uri)
                ?: throw IllegalArgumentException("Could not open the selected index backup")
            val bytes = input.use { readBounded(it, MAX_BACKUP_BYTES) }
            val root = JSONObject(bytes.toString(Charsets.UTF_8))
            require(root.optString("format") == FORMAT_NAME) { "This is not a Drive Semantic Search index backup" }
            require(root.optInt("schemaVersion", -1) == SCHEMA_VERSION) { "Unsupported index backup version" }
            val backupEmail = root.optString("accountEmail", "").trim().lowercase()
            val indexPrefs = context.getSharedPreferences("drive_search_index", Context.MODE_PRIVATE)
            val currentEmail = indexPrefs.getString("account_email", null)?.trim()?.lowercase()
            if (!currentEmail.isNullOrBlank()) {
                require(backupEmail == currentEmail) { "This index backup belongs to a different Google account" }
            }
            val files = root.optJSONArray("files")
                ?: throw IllegalArgumentException("Backup is missing the files list")
            require(files.length() <= MAX_FILES) { "Backup exceeds the 20,000-file safety limit" }

            val app = context.applicationContext as VVFApplication
            val fullContentConsent = app.isDriveFullContentConsentEnabled()
            val rows = ArrayList<FileMetadataEntity>(files.length())
            var totalTextChars = 0L
            for (index in 0 until files.length()) {
                val item = files.optJSONObject(index)
                    ?: throw IllegalArgumentException("Backup contains an invalid file record")
                val id = item.optString("id")
                require(isValidDriveId(id)) { "Backup contains an invalid Drive ID" }
                val name = item.optString("name").trim()
                require(name.isNotEmpty() && name.length <= 500) { "Backup contains an invalid file name" }
                val mime = item.optString("mimeType", "application/octet-stream")
                    .take(200)
                    .ifBlank { "application/octet-stream" }
                val size = item.optLong("sizeBytes", -1L)
                val modified = item.optLong("modifiedDate", -1L)
                require(size >= 0L && modified >= 0L) { "Backup contains invalid file metadata" }
                val text = item.optString("contentText", "").replace("\u0000", "")
                require(text.length <= MAX_TEXT_PER_FILE_CHARS) { "Backup contains an oversized document text entry" }
                totalTextChars += text.length
                require(totalTextChars <= MAX_TOTAL_TEXT_CHARS) { "Backup contains too much extracted text" }
                val canonical = item.opt("canonicalUri") as? String
                val safeCanonical = canonical?.takeIf { raw ->
                    runCatching {
                        val parsed = Uri.parse(raw)
                        parsed.scheme == "https" && parsed.host in setOf("drive.google.com", "docs.google.com")
                    }.getOrDefault(false)
                }
                val md5 = (item.opt("md5Hash") as? String)?.takeIf { it.length <= 128 }
                rows += FileMetadataEntity(
                    path = DRIVE_PREFIX + id,
                    name = name,
                    parentPath = "gdrive://root",
                    sizeBytes = size,
                    mimeType = mime,
                    isDirectory = item.optBoolean("isDirectory", false),
                    modifiedDate = modified,
                    isFavorite = item.optBoolean("isFavorite", false),
                    isTrash = false,
                    tags = "google-drive",
                    md5Hash = md5,
                    contentText = if (fullContentConsent || !requiresFullContentConsent(mime, name)) text else "",
                    canonicalUri = safeCanonical
                )
            }

            app.database.fileDao().replaceDriveIndexRows(rows)
            DriveOfflineManager(context).clearAllLocalCopies()
            app.database.searchFtsDao().rebuildFtsIndex()
            // Imported data is a local snapshot, not proof of a current remote changes cursor.
            val prefs = context.getSharedPreferences("drive_search_index", Context.MODE_PRIVATE)
            prefs.edit()
                .apply {
                    if (currentEmail.isNullOrBlank() && backupEmail.isNotBlank()) putString("account_email", backupEmail)
                }
                .remove("changes_cursor")
                .apply()
            app.publishDriveIndexStatus(
                "IMPORTED_LOCAL_SNAPSHOT",
                rows.size,
                false,
                "Imported local index; run Drive sync to reconcile with the remote account"
            )
            Result.success(rows.size)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun readBounded(input: InputStream, maxBytes: Int): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= maxBytes) { "Index backup exceeds the 50 MiB import limit" }
            output.write(buffer, 0, count)
        }
        return output.toByteArray()
    }

    private fun requiresFullContentConsent(mimeType: String, name: String): Boolean {
        val mime = mimeType.lowercase()
        val extension = name.substringAfterLast('.', "").lowercase()
        return mime == "application/pdf" ||
            mime.startsWith("image/") ||
            mime in setOf(
                DriveOfficeTextExtractor.DOCX,
                DriveOfficeTextExtractor.XLSX,
                DriveOfficeTextExtractor.PPTX
            ) ||
            extension in setOf("pdf", "jpg", "jpeg", "png", "webp", "bmp", "heic", "docx", "xlsx", "pptx")
    }

    private fun isValidDriveId(value: String): Boolean =
        value.length in 1..200 && value.matches(Regex("[A-Za-z0-9_-]+"))

    companion object {
        private const val FORMAT_NAME = "drive-semantic-search-index"
        private const val SCHEMA_VERSION = 1
        private const val DRIVE_PREFIX = "gdrive://"
        private const val MAX_FILES = 20_000
        private const val MAX_BACKUP_BYTES = 50 * 1024 * 1024
        private const val MAX_TEXT_PER_FILE_CHARS = 250_000
        private const val MAX_TOTAL_TEXT_CHARS = 12_000_000L
    }
}
