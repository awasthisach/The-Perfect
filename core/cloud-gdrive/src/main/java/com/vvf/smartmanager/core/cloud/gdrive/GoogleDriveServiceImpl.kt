package com.vvf.smartmanager.core.cloud.gdrive

import android.content.Context
import com.vvf.smartmanager.core.model.CloudAccount
import com.vvf.smartmanager.core.model.CloudBackupInfo
import com.vvf.smartmanager.core.model.CloudProviderType
import com.vvf.smartmanager.core.model.FileItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * Google Drive v3 REST implementation.
 *
 * Production path: call [setAccessToken] after OAuth / Credential Manager, then use list/upload/download.
 * Without a token, operations fail with a clear error (no simulated cloud data).
 *
 * Access tokens typically expire in ~1 hour. This client tracks issue time and fails closed with a
 * re-auth message when the token is likely expired. True refresh-token rotation needs an auth code flow.
 */
class GoogleDriveServiceImpl(
    private val context: Context,
    private val driveApi: DriveApi = DriveNetwork.createApi()
) : GoogleDriveService {

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var tokenIssuedAtMs: Long = 0L

    private var currentAccount: CloudAccount = CloudAccount(
        providerType = CloudProviderType.GOOGLE_DRIVE,
        accountEmail = "",
        displayName = "Google Drive",
        isConnected = false,
        usedBytes = 0L,
        totalBytes = 0L
    )

    private var generation = 0L

    @Synchronized
    override fun tokenGeneration(): Long = generation

    @Synchronized
    override fun publishAccessToken(token: String, generation: Long): Boolean {
        if (this.generation != generation) return false
        setAccessToken(token)
        return true
    }

    @Synchronized
    override fun setAccessToken(token: String?) {
        generation++
        accessToken = token
        tokenIssuedAtMs = if (token.isNullOrBlank()) 0L else System.currentTimeMillis()
        DriveNetwork.setDefaultAccessToken(token)
        if (token.isNullOrBlank()) {
            currentAccount = currentAccount.copy(
                isConnected = false,
                accountEmail = "",
                displayName = "Google Drive"
            )
        }
    }

    private fun bearer(): String {
        val t = accessToken
        if (t.isNullOrBlank()) {
            throw IllegalStateException(
                "Google Drive not authenticated. Complete OAuth / Credential Manager and call setAccessToken()."
            )
        }
        val ageMs = System.currentTimeMillis() - tokenIssuedAtMs
        if (tokenIssuedAtMs > 0L && ageMs > 55L * 60L * 1000L) {
            throw IllegalStateException(
                "Google Drive access token is likely expired (age=${ageMs / 1000}s). Please sign in again."
            )
        }
        return "Bearer $t"
    }

    override suspend fun authenticate(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (accessToken.isNullOrBlank()) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "No access token. Wire Google Sign-In / Credential Manager and call setAccessToken(token)."
                    )
                )
            }
            val about = driveApi.about(bearer())
            val usage = about.storageQuota?.usage?.toLongOrNull() ?: 0L
            val limit = about.storageQuota?.limit?.toLongOrNull() ?: 0L
            currentAccount = currentAccount.copy(
                isConnected = true,
                usedBytes = usage,
                totalBytes = limit,
                lastSyncTimestamp = System.currentTimeMillis()
            )
            Result.success(true)
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            currentAccount = currentAccount.copy(isConnected = false)
            Result.failure(e)
        }
    }

    override suspend fun listDriveFiles(folderId: String): Result<List<FileItem>> = withContext(Dispatchers.IO) {
        try {
            val parent = folderId.ifBlank { "root" }
            val q = "'$parent' in parents and trashed = false"
            val response = driveApi.listFiles(bearer = bearer(), query = q)
            val items = response.files.map(::toFileItem)
            currentAccount = currentAccount.copy(
                isConnected = true,
                lastSyncTimestamp = System.currentTimeMillis()
            )
            Result.success(items)
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listAllDriveFiles(maxFiles: Int): Result<DriveIndexSnapshot> = withContext(Dispatchers.IO) {
        try {
            require(maxFiles in 1..20_000) { "maxFiles must be between 1 and 20,000" }
            val all = ArrayList<FileItem>()
            var pageToken: String? = null
            var next: String? = null
            do {
                val response = driveApi.listFiles(
                    bearer = bearer(), query = "trashed = false",
                    pageSize = minOf(1000, maxFiles - all.size).coerceAtLeast(1), pageToken = pageToken
                )
                all.addAll(response.files.map(::toFileItem))
                next = response.nextPageToken
                pageToken = next
            } while (!next.isNullOrBlank() && all.size < maxFiles)
            Result.success(DriveIndexSnapshot(all.take(maxFiles), !next.isNullOrBlank(), next))
        } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun listDriveFilesPage(pageToken: String?, pageSize: Int): Result<DriveIndexPage> = withContext(Dispatchers.IO) {
        try {
            require(pageSize in 1..1000) { "pageSize must be between 1 and 1,000" }
            val response = driveApi.listFiles(
                bearer = bearer(),
                query = "trashed = false",
                pageSize = pageSize,
                pageToken = pageToken
            )
            currentAccount = currentAccount.copy(
                isConnected = true,
                lastSyncTimestamp = System.currentTimeMillis()
            )
            Result.success(DriveIndexPage(response.files.map(::toFileItem), response.nextPageToken))
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDriveStartPageToken(): Result<String> = withContext(Dispatchers.IO) {
        try {
            Result.success(driveApi.getStartPageToken(bearer()).startPageToken)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDriveChanges(startPageToken: String, maxPages: Int): Result<DriveChangeSnapshot> = withContext(Dispatchers.IO) {
        try {
            require(startPageToken.isNotBlank()) { "A saved Drive changes cursor is required" }
            require(maxPages in 1..200) { "maxPages must be between 1 and 200" }
            val all = ArrayList<DriveChangeDto>()
            var page = startPageToken
            var next: String? = null
            var newStart: String? = null
            var count = 0
            do {
                val response = driveApi.listChanges(bearer(), page, pageSize = 100)
                all.addAll(response.changes)
                next = response.nextPageToken
                newStart = response.newStartPageToken ?: newStart
                if (next.isNullOrBlank()) break
                page = next
                count++
            } while (count < maxPages)
            Result.success(DriveChangeSnapshot(all, next, newStart))
        } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun syncDriveSnapshot(startPageToken: String?): Result<DriveSyncSnapshot> = withContext(Dispatchers.IO) {
        try {
            if (startPageToken.isNullOrBlank()) {
                // Capture cursor before listing so changes during the list can be replayed.
                val initialCursor = driveApi.getStartPageToken(bearer()).startPageToken
                val page = listDriveFilesPage(pageToken = null, pageSize = 1000).getOrThrow()
                Result.success(DriveSyncSnapshot(
                    fullListing = DriveIndexSnapshot(page.files, capped = false, nextPageToken = page.nextPageToken),
                    cursor = initialCursor,
                    fullResyncRequired = true,
                    capped = false
                ))
            } else {
                val changesResult = getDriveChanges(startPageToken)
                if (changesResult.isSuccess) {
                    val changes = changesResult.getOrThrow()
                    val nextCursor = changes.nextPageToken ?: changes.newStartPageToken ?: startPageToken
                    Result.success(DriveSyncSnapshot(
                        changes = changes,
                        cursor = nextCursor,
                        fullResyncRequired = false
                    ))
                } else {
                    // Invalid/expired cursor: recover with a bounded full listing.
                    val freshCursor = driveApi.getStartPageToken(bearer()).startPageToken
                    val page = listDriveFilesPage(pageToken = null, pageSize = 1000).getOrThrow()
                    Result.success(DriveSyncSnapshot(
                        fullListing = DriveIndexSnapshot(page.files, capped = false, nextPageToken = page.nextPageToken),
                        cursor = freshCursor,
                        fullResyncRequired = true,
                        capped = false
                    ))
                }
            }
        } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun moveFile(fileId: String, destinationFolderId: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            requireValidDriveId(fileId, "fileId")
            require(destinationFolderId == "root" || looksLikeDriveId(destinationFolderId)) { "Invalid destination folder ID" }
            val current = driveApi.getFile(bearer(), fileId)
            driveApi.updateFile(
                bearer(), fileId, "{}".toRequestBody("application/json; charset=UTF-8".toMediaType()),
                addParents = destinationFolderId,
                removeParents = current.parents.filter { it.isNotBlank() }.takeIf { it.isNotEmpty() }?.joinToString(",")
            )
            Result.success(true)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun setStarred(fileId: String, starred: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            requireValidDriveId(fileId, "fileId")
            driveApi.updateFile(bearer(), fileId, """{"starred":$starred}""".toRequestBody("application/json; charset=UTF-8".toMediaType()))
            Result.success(true)
        } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun createDriveFolder(name: String, parentFolderId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val cleanName = name.trim()
            require(cleanName.isNotEmpty() && cleanName.length <= 200) { "Folder name must contain 1–200 characters" }
            require(parentFolderId == "root" || looksLikeDriveId(parentFolderId)) { "Invalid parent folder ID" }
            val json = JSONObject()
                .put("name", cleanName)
                .put("mimeType", "application/vnd.google-apps.folder")
                .put("parents", JSONArray().put(parentFolderId))
                .toString()
            val created = driveApi.createFolder(bearer(), json.toRequestBody("application/json; charset=UTF-8".toMediaType()))
            Result.success(requireNotNull(created.id) { "Drive returned no folder ID" })
        } catch (e: CancellationException) { throw e } catch (e: Exception) { Result.failure(e) }
    }

    private fun toFileItem(dto: DriveFileDto): FileItem = FileItem(
        path = "gdrive://${dto.id.orEmpty()}",
        name = dto.name.orEmpty(),
        sizeBytes = dto.size?.toLongOrNull() ?: 0L,
        lastModified = parseDriveTime(dto.modifiedTime),
        isDirectory = dto.mimeType == "application/vnd.google-apps.folder",
        mimeType = dto.mimeType,
        isFavorite = dto.starred,
        md5Hash = dto.md5Checksum,
        localFileId = dto.id,
        canonicalUri = dto.webViewLink
    )

    private fun requireValidDriveId(value: String, label: String) {
        require(looksLikeDriveId(value)) { "Invalid Drive $label" }
    }

    override suspend fun uploadFile(localFile: FileItem, remoteFolderId: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val path = localFile.path
                val file = File(path)
                if (!file.exists() || !file.isFile) {
                    return@withContext Result.failure(IllegalArgumentException("Local file not found: $path"))
                }

                val parent = resolveFolderId(remoteFolderId)
                val localSha256 = calculateSha256(file)
                val metadataJson = JSONObject()
                    .put("name", file.name)
                    .put("parents", JSONArray().put(parent))
                    .put("appProperties", JSONObject().put("vvfSha256", localSha256))
                    .toString()
                val mediaType = (localFile.mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream")
                    .toMediaType()

                // Google requires the upload host and multipart/related, not Retrofit form-data.
                val related = MultipartBody.Builder()
                    .setType("multipart/related".toMediaType())
                    .addPart(
                        MultipartBody.Part.create(
                            metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType())
                        )
                    )
                    .addPart(MultipartBody.Part.create(file.asRequestBody(mediaType)))
                    .build()

                val request = Request.Builder()
                    .url(
                        "https://www.googleapis.com/upload/drive/v3/files" +
                            "?uploadType=multipart&fields=id,name,mimeType,size,md5Checksum,parents,modifiedTime,appProperties"
                    )
                    .header("Authorization", bearer())
                    .post(related)
                    .build()

                DriveNetwork.uploadClient().newCall(request).execute().use { response ->
                    val bodyText = response.body?.string().orEmpty()
                    if (!response.isSuccessful) {
                        val detail = bodyText.take(400).ifBlank { response.message }
                        return@withContext Result.failure(
                            IllegalStateException("Drive upload failed: HTTP ${response.code} — $detail")
                        )
                    }

                    val uploaded = try {
                        DriveUploadResponseParser.parse(bodyText)
                    } catch (e: CancellationException) { throw e } catch (e: Exception) {
                        return@withContext Result.failure(
                            IllegalStateException("Drive upload returned invalid JSON response", e)
                        )
                    }

                    val id = uploaded?.id?.takeIf { it.isNotBlank() }
                        ?: return@withContext Result.failure(
                            IllegalStateException("Upload succeeded but no file id returned: ${bodyText.take(200)}")
                        )
                    val remoteMd5 = uploaded.md5Checksum
                    val remoteSize = uploaded.size?.toLongOrNull()

                    val localMd5 = calculateMd5(file)
                    if (remoteMd5.isNullOrBlank()) {
                        return@withContext Result.failure(
                            IllegalStateException("Drive upload returned no integrity checksum for binary artifact $id")
                        )
                    }
                    if (!remoteMd5.equals(localMd5, ignoreCase = true)) {
                        return@withContext Result.failure(
                            IllegalStateException("Drive upload integrity mismatch for $id")
                        )
                    }
                    if (remoteSize == null) {
                        return@withContext Result.failure(
                            IllegalStateException("Drive upload returned no size for binary artifact $id")
                        )
                    }
                    if (remoteSize != file.length()) {
                        return@withContext Result.failure(
                            IllegalStateException("Drive upload size mismatch for $id")
                        )
                    }
                    val persistedSha256 = driveApi.getFileMetadata(bearer(), id)
                        .appProperties["vvfSha256"]
                        ?.trim()
                        ?.lowercase()
                    if (persistedSha256.isNullOrBlank() || persistedSha256 != localSha256) {
                        return@withContext Result.failure(
                            IllegalStateException("Drive upload SHA-256 metadata round-trip failed for $id")
                        )
                    }

                    currentAccount = currentAccount.copy(
                        usedBytes = currentAccount.usedBytes + file.length(),
                        lastSyncTimestamp = System.currentTimeMillis()
                    )
                    Result.success(id)
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun listDriveBackups(): Result<List<CloudBackupInfo>> = withContext(Dispatchers.IO) {
        try {
            val folderQuery = "name = 'VVF_Backups' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
            val folder = driveApi.listFiles(bearer(), query = folderQuery, pageSize = 10)
                .files.firstOrNull { !it.id.isNullOrBlank() }
                ?: return@withContext Result.success(emptyList())
            val folderId = folder.id!!
            val backups = mutableListOf<CloudBackupInfo>()
            var pageToken: String? = null
            var pageCount = 0
            do {
                val page = driveApi.listFiles(
                    bearer = bearer(),
                    query = "'$folderId' in parents and trashed = false",
                    pageSize = 100,
                    pageToken = pageToken,
                    orderBy = "modifiedTime desc"
                )
                page.files
                    .filter { !it.trashed && it.mimeType != "application/vnd.google-apps.folder" }
                    .filter { it.name.orEmpty().endsWith(".vvfbak", ignoreCase = true) }
                    .forEach { dto ->
                        val id = dto.id?.takeIf { looksLikeDriveId(it) } ?: return@forEach
                        val digest = dto.appProperties["vvfSha256"]?.trim()?.lowercase()
                            ?.takeIf { it.matches(Regex("^[a-f0-9]{64}$")) }
                        backups.add(
                            CloudBackupInfo(
                                backupId = id,
                                timestamp = parseDriveTime(dto.modifiedTime),
                                backupName = dto.name.orEmpty(),
                                backupSizeBytes = dto.size?.toLongOrNull() ?: 0L,
                                checksumSha256 = digest
                            )
                        )
                    }
                pageToken = page.nextPageToken
                pageCount++
                if (pageCount >= MAX_BACKUP_LIST_PAGES && pageToken != null) {
                    return@withContext Result.failure(
                        IllegalStateException("Backup listing exceeded the safe pagination limit; refusing to show an incomplete history")
                    )
                }
            } while (pageToken != null)
            Result.success(backups.sortedByDescending { it.timestamp })
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun getFileSha256(fileId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            require(fileId.isNotBlank() && looksLikeDriveId(fileId)) { "Invalid Drive file id" }
            val digest = driveApi.getFileMetadata(bearer(), fileId).appProperties["vvfSha256"]
                ?.trim()
                ?.lowercase()
                ?: throw IllegalStateException("Drive backup has no trusted SHA-256 metadata")
            require(digest.matches(Regex("^[a-f0-9]{64}$"))) { "Drive backup SHA-256 metadata is invalid" }
            Result.success(digest)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    override suspend fun downloadFile(fileId: String, destinationPath: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                val body = driveApi.downloadFile(bearer(), fileId)
                val dest = File(destinationPath)
                dest.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    dest.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Result.success(true)
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun downloadFileBounded(
        fileId: String,
        destinationPath: String,
        maxBytes: Long,
        mimeType: String?
    ): Result<Boolean> = withContext(Dispatchers.IO) {
        val destination = File(destinationPath)
        try {
            require(maxBytes in 1..(200L * 1024L * 1024L)) { "Download limit must be between 1 byte and 200 MiB" }
            requireValidDriveId(fileId, "fileId")
            val exportMime = when (mimeType?.lowercase()) {
                "application/vnd.google-apps.document",
                "application/vnd.google-apps.presentation" -> "application/pdf"
                "application/vnd.google-apps.spreadsheet" -> "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                else -> null
            }
            val body = if (exportMime != null) {
                driveApi.exportTextFile(bearer(), fileId, exportMime)
            } else {
                driveApi.downloadFile(bearer(), fileId)
            }
            body.use { response ->
                if (response.contentLength() > maxBytes) {
                    throw IllegalArgumentException("Drive file exceeds the configured download limit")
                }
                destination.parentFile?.mkdirs()
                response.byteStream().use { input ->
                    destination.outputStream().use { output ->
                        val buffer = ByteArray(8192)
                        var total = 0L
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > maxBytes) {
                                throw IllegalArgumentException("Drive response exceeded the configured download limit")
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                }
            }
            Result.success(true)
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            destination.delete()
            Result.failure(e)
        }
    }

    override suspend fun extractTextContent(fileId: String, mimeType: String?): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                requireValidDriveId(fileId, "fileId")
                val type = mimeType.orEmpty().lowercase()
                val exportMime = when (type) {
                    "application/vnd.google-apps.document",
                    "application/vnd.google-apps.presentation" -> "text/plain"
                    "application/vnd.google-apps.spreadsheet" -> "text/csv"
                    else -> null
                }
                val isPlainText = type.startsWith("text/") ||
                    type in setOf("application/json", "application/xml", "application/csv", "application/rtf")
                if (exportMime == null && !isPlainText) {
                    return@withContext Result.failure(
                        UnsupportedOperationException("This file type needs a dedicated parser; binary content was not decoded as text.")
                    )
                }
                val body = if (exportMime != null) {
                    driveApi.exportTextFile(bearer(), fileId, exportMime)
                } else {
                    driveApi.downloadFile(bearer(), fileId)
                }
                body.use { response ->
                    val maxBytes = 2 * 1024 * 1024
                    val declaredLength = response.contentLength()
                    if (declaredLength > maxBytes) {
                        return@withContext Result.failure(IllegalArgumentException("Document text exceeds the 2 MiB indexing limit"))
                    }
                    val output = java.io.ByteArrayOutputStream()
                    response.byteStream().use { input ->
                        val buffer = ByteArray(8192)
                        var total = 0
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            total += count
                            if (total > maxBytes) {
                                return@withContext Result.failure(IllegalArgumentException("Document text exceeds the 2 MiB indexing limit"))
                            }
                            output.write(buffer, 0, count)
                        }
                    }
                    val text = output.toString(Charsets.UTF_8.name())
                        .replace("\u0000", "")
                        .take(250_000)
                    Result.success(text)
                }
            } catch (e: CancellationException) { throw e } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getStorageQuota(): Result<Pair<Long, Long>> = withContext(Dispatchers.IO) {
        try {
            val about = driveApi.about(bearer())
            val usage = about.storageQuota?.usage?.toLongOrNull() ?: 0L
            val limit = about.storageQuota?.limit?.toLongOrNull() ?: 0L
            currentAccount = currentAccount.copy(
                isConnected = true,
                usedBytes = usage,
                totalBytes = limit
            )
            Result.success(Pair(usage, limit))
        } catch (e: CancellationException) { throw e } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getAccountInfo(): CloudAccount = currentAccount

    fun disconnect() {
        setAccessToken(null)
        currentAccount = CloudAccount(
            providerType = CloudProviderType.GOOGLE_DRIVE,
            isConnected = false,
            usedBytes = 0L,
            totalBytes = 0L
        )
    }

    /**
     * Resolve a remote folder reference to a Drive folder id.
     *
     * Human folder names (e.g. "VVF_Backups") must be looked up / created.
     * Only strings that look like real Drive resource ids are used as-is.
     * The previous heuristic (length >= 10) misclassified "VVF_Backups" as a fileId
     * and produced HTTP 404: File not found: VVF_Backups.
     */
    private suspend fun resolveFolderId(folderReference: String): String {
        val ref = folderReference.trim().trimEnd('.')
        if (ref.isBlank() || ref.equals("root", ignoreCase = true)) return "root"
        if (looksLikeDriveId(ref)) return ref

        val escapedName = ref.replace("\\", "\\\\").replace("'", "\\'")
        val query = "name = '$escapedName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val existing = driveApi.listFiles(bearer(), query = query, pageSize = 10).files.firstOrNull()
        if (existing?.id != null) return existing.id

        val metadata = JSONObject()
            .put("name", ref)
            .put("mimeType", "application/vnd.google-apps.folder")
            .put("parents", JSONArray().put("root"))
            .toString()
            .toRequestBody("application/json; charset=UTF-8".toMediaType())
        return driveApi.createFolder(bearer(), metadata).id
            ?: throw IllegalStateException("Drive folder creation returned no id for '$ref'")
    }

    /**
     * Drive resource ids are typically ~25–44 characters (often ~33).
     * Short readable names like "VVF_Backups" must NOT be treated as ids.
     */
    private fun looksLikeDriveId(value: String): Boolean {
        if (value.length < 20 || value.length > 128) return false
        if (value.contains(' ') || value.contains('/') || value.contains('.')) return false
        return value.all { it.isLetterOrDigit() || it == '-' || it == '_' }
    }

    private companion object {
        const val MAX_BACKUP_LIST_PAGES = 20
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun calculateMd5(file: File): String {
        val digest = MessageDigest.getInstance("MD5")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun parseDriveTime(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        return try {
            val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
                timeZone = TimeZone.getTimeZone("UTC")
            }
            fmt.parse(iso)?.time ?: 0L
        } catch (_: Exception) {
            try {
                val fmt = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
                    timeZone = TimeZone.getTimeZone("UTC")
                }
                fmt.parse(iso)?.time ?: 0L
            } catch (_: Exception) {
                0L
            }
        }
    }
}
