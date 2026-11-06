package com.vvf.smartmanager.core.cloud.gdrive

import android.content.Context
import com.vvf.smartmanager.core.model.CloudAccount
import com.vvf.smartmanager.core.model.CloudProviderType
import com.vvf.smartmanager.core.model.FileItem
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.io.IOException
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
    private val driveApi: DriveApi = DriveNetwork.createApi(),
    private val accessTokenProvider: DriveAccessTokenProvider = GoogleAuthorizationDriveAccessTokenProvider(context)
) : GoogleDriveService {

    @Volatile
    private var accessToken: String? = null

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
        DriveNetwork.setDefaultAccessToken(token)
        if (token.isNullOrBlank()) {
            currentAccount = currentAccount.copy(
                isConnected = false,
                accountEmail = "",
                displayName = "Google Drive"
            )
        }
    }

    private suspend fun bearer(): String {
        // AuthorizationClient owns the cached short-lived token and refresh lifecycle.
        // A pending consent action is surfaced as re-authentication required.
        val token = accessTokenProvider.getAccessToken()
        accessToken = token
        DriveNetwork.setDefaultAccessToken(token)
        return "Bearer $token"
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
            val q = "'$parent' in parents and trashed = false"
            val items = mutableListOf<FileItem>()
            val seenPageTokens = mutableSetOf<String>()
            var pageToken: String? = null
            var pageTokenRestarts = 0

            // Google documents that a rejected page token must be discarded and pagination
            // restarted from page one. Bound the restart to avoid an unending retry loop.
            while (true) {
                try {
                    do {
                        currentCoroutineContext().ensureActive()
                        val response = driveApi.listFiles(
                            bearer = bearer(),
                            query = q,
                            pageToken = pageToken
                        )
                        if (response.incompleteSearch) {
                            throw IOException("Google Drive reported incompleteSearch; refusing a partial listing")
                        }
                        items += response.files.map { dto ->
                            FileItem(
                                path = "gdrive://${dto.id.orEmpty()}",
                                name = dto.name.orEmpty(),
                                sizeBytes = dto.size?.toLongOrNull() ?: 0L,
                                lastModified = parseDriveTime(dto.modifiedTime),
                                isDirectory = dto.mimeType == "application/vnd.google-apps.folder",
                                mimeType = dto.mimeType
                            )
                        }

                        val nextPageToken = response.nextPageToken?.takeIf(String::isNotBlank)
                        if (nextPageToken != null && !seenPageTokens.add(nextPageToken)) {
                            throw IOException("Google Drive returned a repeated page token; refusing an incomplete listing")
                        }
                        pageToken = nextPageToken
                    } while (pageToken != null)
                    break
                } catch (error: retrofit2.HttpException) {
                    if (pageToken != null && pageTokenRestarts == 0 && error.isRejectedDrivePageToken()) {
                        pageTokenRestarts++
                        items.clear()
                        seenPageTokens.clear()
                        pageToken = null
                        continue
                    }
                    throw error
                }
            }

            currentAccount = currentAccount.copy(
                isConnected = true,
                lastSyncTimestamp = System.currentTimeMillis()
            )
            Result.success(items)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            // A failed later page must not be reported as a successful partial directory listing.
            Result.failure(e)
        }
    }

    private fun retrofit2.HttpException.isRejectedDrivePageToken(): Boolean {
        if (code() != 400) return false
        val errorBody = runCatching { response()?.errorBody()?.string().orEmpty() }.getOrDefault("")
        val detail = "${message()} $errorBody"
        return detail.contains("invalidPageToken", ignoreCase = true) ||
            detail.contains("pageToken", ignoreCase = true) ||
            detail.contains("page token", ignoreCase = true)
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
                val body = driveApi.downloadFile(bearer(), fileId)
                val dest = File(destinationPath)
                dest.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    dest.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Result.success(true)
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
                totalBytes = limit
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

        val safeFolderName = ref.replace("\\", "\\\\").replace("\"", "\\\"")
        val metadata = """{"name":"$safeFolderName","mimeType":"application/vnd.google-apps.folder","parents":["root"]}"""
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
