package com.vvf.smartmanager.core.cloud.gdrive

import android.accounts.Account
import android.content.Context
import com.google.android.gms.auth.GoogleAuthUtil
import com.google.android.gms.auth.api.signin.GoogleSignIn
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
 * Access tokens typically expire in ~1 hour. Near expiry this client attempts a best-effort silent
 * refresh via [GoogleAuthUtil] using the last known account (or [GoogleSignIn] last account).
 * If silent refresh is impossible, it fails closed with a re-auth message.
 */
class GoogleDriveServiceImpl(
    private val context: Context,
    private val driveApi: DriveApi = DriveNetwork.createApi()
) : GoogleDriveService {

    private val sessionLock = Any()

    @Volatile
    private var accessToken: String? = null

    @Volatile
    private var tokenIssuedAtMs: Long = 0L

    /** Last Google account used for Drive scope; needed for silent refresh. */
    @Volatile
    private var lastAccount: Account? = null

    /** Bumped on every token/session change so in-flight refresh cannot commit after disconnect. */
    @Volatile
    private var sessionGeneration: Long = 0L

    private var currentAccount: CloudAccount = CloudAccount(
        providerType = CloudProviderType.GOOGLE_DRIVE,
        accountEmail = "",
        displayName = "Google Drive",
        isConnected = false,
        usedBytes = 0L,
        totalBytes = 0L
    )

    override fun setAccessToken(token: String?) {
        synchronized(sessionLock) {
            sessionGeneration++
            accessToken = token
            tokenIssuedAtMs = if (token.isNullOrBlank()) 0L else System.currentTimeMillis()
            DriveNetwork.setDefaultAccessToken(token)
            if (token.isNullOrBlank()) {
                lastAccount = null
                currentAccount = currentAccount.copy(
                    isConnected = false,
                    accountEmail = "",
                    displayName = "Google Drive"
                )
            }
        }
    }

    /**
     * Prefer this overload after interactive sign-in so silent refresh can work later.
     * Callers that only have a token still work: [trySilentRefresh] falls back to the
     * last GoogleSignIn account when [lastAccount] was never set.
     */
    fun setAccessToken(token: String?, account: Account?) {
        synchronized(sessionLock) {
            sessionGeneration++
            lastAccount = account
            accessToken = token
            tokenIssuedAtMs = if (token.isNullOrBlank()) 0L else System.currentTimeMillis()
            DriveNetwork.setDefaultAccessToken(token)
            if (token.isNullOrBlank()) {
                lastAccount = null
                currentAccount = currentAccount.copy(
                    isConnected = false,
                    accountEmail = "",
                    displayName = "Google Drive"
                )
            } else if (account != null) {
                currentAccount = currentAccount.copy(
                    isConnected = true,
                    accountEmail = account.name.orEmpty(),
                    displayName = account.name.orEmpty()
                )
            }
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
        // Attempt silent refresh a few minutes before the hard 55-min fail-closed threshold.
        if (tokenIssuedAtMs > 0L && ageMs > 50L * 60L * 1000L) {
            val refreshed = trySilentRefresh()
            if (refreshed != null) {
                return "Bearer $refreshed"
            }
            if (ageMs > 55L * 60L * 1000L) {
                throw IllegalStateException(
                    "Google Drive access token is likely expired (age=${ageMs / 1000}s) " +
                        "and silent refresh failed. Please sign in again."
                )
            }
        }
        return "Bearer $t"
    }

    /**
     * Best-effort silent refresh. Returns a new token on success, null if refresh is not possible.
     * Does not extend [tokenIssuedAtMs] when Google returns the same cached token.
     */
    private fun trySilentRefresh(): String? {
        val generationAtStart: Long
        val previousToken: String?
        val account: Account?
        synchronized(sessionLock) {
            generationAtStart = sessionGeneration
            previousToken = accessToken
            account = lastAccount
                ?: runCatching { GoogleSignIn.getLastSignedInAccount(context)?.account }.getOrNull()
        }
        if (account == null || previousToken.isNullOrBlank()) return null

        return try {
            val newToken = GoogleAuthUtil.getToken(
                context,
                account,
                "oauth2:${GoogleDriveAuth.DRIVE_FILE_SCOPE}"
            )
            if (newToken.isNullOrBlank()) return null
            // Same string as before is not a successful refresh — do not reset the age clock.
            if (newToken == previousToken) return null

            synchronized(sessionLock) {
                if (sessionGeneration != generationAtStart) {
                    // disconnect() / setAccessToken ran while we were refreshing
                    return null
                }
                accessToken = newToken
                tokenIssuedAtMs = System.currentTimeMillis()
                DriveNetwork.setDefaultAccessToken(newToken)
                lastAccount = account
            }
            newToken
        } catch (_: Exception) {
            null
        }
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
        } catch (e: Exception) {
            currentAccount = currentAccount.copy(isConnected = false)
            Result.failure(e)
        }
    }

    override suspend fun listDriveFiles(folderId: String): Result<List<FileItem>> = withContext(Dispatchers.IO) {
        try {
            val parent = folderId.ifBlank { "root" }
            val q = "'$parent' in parents and trashed = false"
            val response = driveApi.listFiles(bearer = bearer(), query = q)
            val items = response.files.map { dto ->
                FileItem(
                    path = "gdrive://${dto.id.orEmpty()}",
                    name = dto.name.orEmpty(),
                    sizeBytes = dto.size?.toLongOrNull() ?: 0L,
                    lastModified = parseDriveTime(dto.modifiedTime),
                    isDirectory = dto.mimeType == "application/vnd.google-apps.folder",
                    mimeType = dto.mimeType
                )
            }
            currentAccount = currentAccount.copy(
                isConnected = true,
                lastSyncTimestamp = System.currentTimeMillis()
            )
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
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
