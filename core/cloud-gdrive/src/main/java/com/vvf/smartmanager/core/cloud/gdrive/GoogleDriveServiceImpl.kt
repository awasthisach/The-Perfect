package com.vvf.smartmanager.core.cloud.gdrive

import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.auth.FirebaseAuth
import com.vvf.smartmanager.core.model.CloudAccount
import com.vvf.smartmanager.core.model.CloudProviderType
import com.vvf.smartmanager.core.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody
import retrofit2.HttpException
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.UUID
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
 * Access tokens are kept in memory. When missing/expired, this client may refresh silently only if
 * an existing Google Sign-In account and Firebase session are present and their emails match.
 */
class GoogleDriveServiceImpl(
    private val context: Context,
    private val driveApi: DriveApi = DriveNetwork.createApi()
) : GoogleDriveService {

    companion object {
        private const val DRIVE_PAGE_SIZE = 1000
        private const val MAX_LISTED_FILES = 20_000
        private const val MAX_INDEX_FILE_BYTES = 50L * 1024L * 1024L
        private const val MAX_PINNED_FILE_BYTES = 200L * 1024L * 1024L
        private const val MAX_PAGE_TOKEN_LENGTH = 4096
    }

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

    override fun setAccessToken(token: String?) {
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
        val googleAccount = GoogleSignIn.getLastSignedInAccount(context)
        val firebaseEmail = runCatching { FirebaseAuth.getInstance().currentUser?.email }.getOrNull()
        if (!DriveSessionPolicy.accountsMatch(googleAccount?.email, firebaseEmail)) {
            setAccessToken(null)
            throw IllegalStateException("Google Drive and Firebase sessions are not aligned. Sign in again.")
        }

        val currentToken = accessToken?.takeIf { it.isNotBlank() }
        val now = System.currentTimeMillis()
        if (currentToken != null && !DriveSessionPolicy.isLikelyExpired(tokenIssuedAtMs, now)) {
            return "Bearer $currentToken"
        }

        // Silent refresh is allowed only for an existing, aligned Google + Firebase session.
        val account = googleAccount?.account
            ?: throw IllegalStateException("Google Drive session expired. Please sign in again.")
        val refreshed = try {
            GoogleAuthUtil.getToken(context, account, "oauth2:https://www.googleapis.com/auth/drive")
        } catch (_: Exception) {
            setAccessToken(null)
            throw IllegalStateException("Google Drive token refresh failed. Sign in again.")
        }
        if (refreshed.isBlank()) {
            setAccessToken(null)
            throw IllegalStateException("Google Drive token refresh returned an empty token. Sign in again.")
        }
        setAccessToken(refreshed)
        return "Bearer $refreshed"
    }

    override suspend fun authenticate(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val about = driveApi.about(bearer())
            val usage = about.storageQuota?.usage?.toLongOrNull() ?: 0L
            val limit = about.storageQuota?.limit?.toLongOrNull() ?: 0L
            currentAccount = currentAccount.copy(
                isConnected = true,
                usedBytes = usage,
                totalBytes = limit,
                accountEmail = about.user?.emailAddress.orEmpty(),
                displayName = about.user?.displayName?.takeIf { it.isNotBlank() } ?: "Google Drive",
                lastSyncTimestamp = System.currentTimeMillis()
            )
            Result.success(true)
        } catch (e: Exception) {
            currentAccount = currentAccount.copy(isConnected = false)
            Result.failure(e)
        }
    }

    override suspend fun listDriveFiles(folderId: String): Result<List<FileItem>> = withContext(Dispatchers.IO) {
        try {
            val parent = folderId.ifBlank { "root" }
            require(DriveIdValidator.isValidParentId(parent)) { "Invalid Drive folder id." }
            val query = "'$parent' in parents and trashed = false"
            val items = mutableListOf<FileItem>()
            var pageToken: String? = null
            do {
                val response = driveApi.listFiles(
                    bearer = bearer(),
                    query = query,
                    pageSize = DRIVE_PAGE_SIZE,
                    pageToken = pageToken
                )
                items += response.files.take(MAX_LISTED_FILES - items.size).map(::toFileItem)
                pageToken = response.nextPageToken
                if (items.size >= MAX_LISTED_FILES) break
            } while (pageToken != null)
            currentAccount = currentAccount.copy(isConnected = true, lastSyncTimestamp = System.currentTimeMillis())
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun listDriveMetadataPage(
        pageToken: String?,
        pageSize: Int
    ): Result<DriveMetadataPage> = withContext(Dispatchers.IO) {
        try {
            require(pageSize in 1..50) { "Drive metadata batch size must be between 1 and 50." }
            require(pageToken == null || (pageToken.length <= MAX_PAGE_TOKEN_LENGTH && pageToken.none { it.isISOControl() })) {
                "Invalid Drive listing cursor."
            }
            val response = driveApi.listFiles(
                bearer = bearer(),
                query = "trashed = false",
                pageSize = pageSize,
                pageToken = pageToken
            )
            Result.success(
                DriveMetadataPage(
                    files = response.files.map(::toMetadataRecord),
                    nextPageToken = response.nextPageToken
                )
            )
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Drive metadata page failed. Retry the sync."))
        }
    }

    override suspend fun listAllDriveFiles(): Result<DriveFileListing> = withContext(Dispatchers.IO) {
        try {
            val items = mutableListOf<FileItem>()
            var pageToken: String? = null
            do {
                val response = driveApi.listFiles(
                    bearer = bearer(),
                    query = "trashed = false",
                    pageSize = DRIVE_PAGE_SIZE,
                    pageToken = pageToken
                )
                items += response.files.take(MAX_LISTED_FILES - items.size).map(::toFileItem)
                pageToken = response.nextPageToken
                if (items.size >= MAX_LISTED_FILES) break
            } while (pageToken != null)
            currentAccount = currentAccount.copy(isConnected = true, lastSyncTimestamp = System.currentTimeMillis())
            Result.success(DriveFileListing(items, incomplete = pageToken != null, nextPageToken = pageToken))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getStartPageToken(): Result<String> = withContext(Dispatchers.IO) {
        try {
            val token = driveApi.getStartPageToken(bearer()).startPageToken
                ?.takeIf { it.isNotBlank() && it.length <= MAX_PAGE_TOKEN_LENGTH }
                ?: throw IllegalStateException("Drive did not return a valid change cursor.")
            Result.success(token)
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Could not start incremental Drive sync. Try a full-list sync."))
        }
    }

    override suspend fun listChanges(pageToken: String): Result<DriveChangePage> = withContext(Dispatchers.IO) {
        try {
            require(pageToken.isNotBlank() && pageToken.length <= MAX_PAGE_TOKEN_LENGTH && pageToken.none { it.isISOControl() }) {
                "Invalid Drive change cursor."
            }
            val response = driveApi.listChanges(bearer(), pageToken = pageToken, pageSize = DRIVE_PAGE_SIZE)
            val changes = response.changes.map { change ->
                val id = (change.fileId ?: change.file?.id)
                    ?.takeIf(DriveIdValidator::isValidFileId)
                    ?: throw IllegalStateException("Drive returned a change without a valid file id.")
                DriveChange(
                    fileId = id,
                    removed = change.removed,
                    file = change.file?.let(::toMetadataRecord)
                )
            }
            Result.success(
                DriveChangePage(
                    changes = changes,
                    nextPageToken = response.nextPageToken,
                    newStartPageToken = response.newStartPageToken
                )
            )
        } catch (e: HttpException) {
            if (e.code() == 410 || e.code() == 400) {
                Result.failure(DriveChangeTokenInvalidException())
            } else {
                Result.failure(IllegalStateException("Drive incremental sync failed. Retry the sync."))
            }
        } catch (_: Exception) {
            Result.failure(IllegalStateException("Drive incremental sync failed. Retry the sync."))
        }
    }

    private fun toMetadataRecord(dto: DriveFileDto): DriveMetadataRecord {
        val id = dto.id?.takeIf(DriveIdValidator::isValidFileId)
            ?: throw IllegalStateException("Drive returned a file without a valid resource id.")
        return DriveMetadataRecord(
            fileId = id,
            name = dto.name.orEmpty(),
            mimeType = dto.mimeType.orEmpty(),
            sizeBytes = dto.size?.toLongOrNull() ?: 0L,
            modifiedTimeMs = parseDriveTime(dto.modifiedTime),
            parentIds = dto.parents.filter(DriveIdValidator::isValidParentId),
            webViewLink = dto.webViewLink,
            starred = dto.starred == true,
            trashed = dto.trashed
        )
    }

    private fun toFileItem(dto: DriveFileDto): FileItem {
        val id = dto.id?.takeIf(DriveIdValidator::isValidFileId)
            ?: throw IllegalStateException("Drive returned a file without a valid resource id.")
        return FileItem(
            path = "gdrive://$id",
            name = dto.name.orEmpty(),
            sizeBytes = dto.size?.toLongOrNull() ?: 0L,
            lastModified = parseDriveTime(dto.modifiedTime),
            isDirectory = dto.mimeType == "application/vnd.google-apps.folder",
            mimeType = dto.mimeType
        )
    }

    override suspend fun uploadFile(localFile: FileItem, remoteFolderId: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val path = localFile.path
                val file = File(path)
                if (!file.exists() || !file.isFile) {
                    return@withContext Result.failure(IllegalArgumentException("Local file not found: $path"))
                }

                require(DriveIdValidator.isValidParentId(remoteFolderId)) { "Invalid destination folder id." }
                val parent = remoteFolderId
                val safeName = file.name.replace("\\", "\\\\").replace("\"", "\\\"")
                val metadataJson = """{"name":"$safeName","parents":["$parent"]}"""
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
                            "?uploadType=multipart&fields=id,name,mimeType,size,md5Checksum,parents,modifiedTime"
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
                    } catch (e: Exception) {
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

                    currentAccount = currentAccount.copy(
                        usedBytes = currentAccount.usedBytes + file.length(),
                        lastSyncTimestamp = System.currentTimeMillis()
                    )
                    Result.success(id)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun downloadFile(fileId: String, destinationPath: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                require(DriveIdValidator.isValidFileId(fileId)) { "Invalid Drive file id." }
                writeBodyAtomically(
                    body = driveApi.downloadFile(bearer(), fileId),
                    destinationPath = destinationPath,
                    maxBytes = MAX_PINNED_FILE_BYTES
                )
                Result.success(true)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun downloadForIndexing(
        fileId: String,
        mimeType: String,
        destinationPath: String
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            require(DriveIdValidator.isValidFileId(fileId)) { "Invalid Drive file id." }
            val type = mimeType.lowercase()
            val (body, extractedMimeType) = when (type) {
                "application/vnd.google-apps.document" ->
                    driveApi.exportFile(bearer(), fileId, "text/plain") to "text/plain"
                "application/vnd.google-apps.spreadsheet" ->
                    driveApi.exportFile(bearer(), fileId, "text/csv") to "text/csv"
                "application/vnd.google-apps.presentation" ->
                    driveApi.exportFile(bearer(), fileId, "text/plain") to "text/plain"
                else -> driveApi.downloadFile(bearer(), fileId) to mimeType
            }
            writeBodyAtomically(body, destinationPath, MAX_INDEX_FILE_BYTES)
            Result.success(extractedMimeType)
        } catch (e: Exception) {
            Result.failure(IllegalStateException("Could not safely download this file for local indexing."))
        }
    }

    private fun writeBodyAtomically(body: ResponseBody, destinationPath: String, maxBytes: Long) {
        val dest = File(destinationPath).canonicalFile
        val privateRoot = context.filesDir.canonicalFile
        val cacheRoot = context.cacheDir.canonicalFile
        val inPrivateFiles = dest.path.startsWith(privateRoot.path + File.separator)
        val inPrivateCache = dest.path.startsWith(cacheRoot.path + File.separator)
        require(inPrivateFiles || inPrivateCache) {
            "Downloaded file bytes must remain in app-private storage."
        }
        require(body.contentLength() < 0L || body.contentLength() <= maxBytes) {
            "Downloaded file exceeds the configured size limit."
        }
        dest.parentFile?.mkdirs()
        val tempFile = File(dest.parentFile, dest.name + "." + UUID.randomUUID() + ".part")
        try {
            body.byteStream().use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read < 0) break
                        total += read
                        require(total <= maxBytes) { "Downloaded file exceeds the configured size limit." }
                        output.write(buffer, 0, read)
                    }
                    output.flush()
                    output.fd.sync()
                }
            }
            Files.move(
                tempFile.toPath(),
                dest.toPath(),
                StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING
            )
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }
    }

    override suspend fun createFolder(name: String, parentFolderId: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val cleanName = name.trim()
                require(cleanName.isNotEmpty() && cleanName.length <= 255 && cleanName.none { it.isISOControl() }) {
                    "Folder name must contain 1–255 printable characters."
                }
                require(DriveIdValidator.isValidParentId(parentFolderId)) { "Invalid destination folder id." }
                val escapedName = cleanName.replace("\\", "\\\\").replace("\"", "\\\"")
                val escapedParent = parentFolderId
                val metadata = """{"name":"$escapedName","mimeType":"application/vnd.google-apps.folder","parents":["$escapedParent"]}"""
                    .toRequestBody("application/json; charset=UTF-8".toMediaType())
                val id = driveApi.createFolder(bearer(), metadata).id
                    ?.takeIf(DriveIdValidator::isValidFileId)
                    ?: throw IllegalStateException("Drive did not return a valid folder id.")
                Result.success(id)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun moveFile(fileId: String, targetFolderId: String): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                require(DriveIdValidator.isValidFileId(fileId)) { "Invalid Drive file id." }
                require(DriveIdValidator.isValidParentId(targetFolderId)) { "Invalid destination folder id." }
                val current = driveApi.getFile(bearer(), fileId)
                val parents = current.parents.filter(DriveIdValidator::isValidFileId)
                require(parents.isNotEmpty()) { "Drive did not return the file's current parent ids." }
                val body = "{}".toRequestBody("application/json; charset=UTF-8".toMediaType())
                val updated = driveApi.updateFile(
                    bearer = bearer(),
                    fileId = fileId,
                    metadata = body,
                    addParents = targetFolderId,
                    removeParents = parents.joinToString(",")
                )
                Result.success(updated.id == fileId)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun starFile(fileId: String, starred: Boolean): Result<Boolean> =
        withContext(Dispatchers.IO) {
            try {
                require(DriveIdValidator.isValidFileId(fileId)) { "Invalid Drive file id." }
                val metadata = """{"starred":$starred}"""
                    .toRequestBody("application/json; charset=UTF-8".toMediaType())
                val updated = driveApi.updateFile(bearer(), fileId, metadata)
                Result.success(updated.id == fileId && updated.starred == starred)
            } catch (e: Exception) {
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
                totalBytes = limit,
                accountEmail = about.user?.emailAddress.orEmpty(),
                displayName = about.user?.displayName?.takeIf { it.isNotBlank() } ?: "Google Drive"
            )
            Result.success(Pair(usage, limit))
        } catch (e: Exception) {
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
