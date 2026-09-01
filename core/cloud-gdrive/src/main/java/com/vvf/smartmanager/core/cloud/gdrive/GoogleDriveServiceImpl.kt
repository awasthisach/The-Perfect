package com.vvf.smartmanager.core.cloud.gdrive

import android.content.Context
import com.vvf.smartmanager.core.model.CloudAccount
import com.vvf.smartmanager.core.model.CloudProviderType
import com.vvf.smartmanager.core.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
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
 */
class GoogleDriveServiceImpl(
    private val context: Context,
    private val driveApi: DriveApi = DriveNetwork.createApi()
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

    fun setAccessToken(token: String?) {
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

    private fun bearer(): String {
        val t = accessToken
        if (t.isNullOrBlank()) {
            throw IllegalStateException(
                "Google Drive not authenticated. Complete OAuth / Credential Manager and call setAccessToken()."
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
                val metadataBody = metadataJson.toRequestBody("application/json; charset=UTF-8".toMediaType())
                val mediaType = (localFile.mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream")
                    .toMediaType()
                val fileBody = file.asRequestBody(mediaType)
                val part = MultipartBody.Part.createFormData("file", file.name, fileBody)
                val uploaded = driveApi.uploadFile(bearer(), metadataBody, part)
                val id = uploaded.id
                    ?: return@withContext Result.failure(IllegalStateException("Upload succeeded but no file id returned"))

                val localMd5 = calculateMd5(file)
                val remoteMd5 = uploaded.md5Checksum
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
                if (uploaded.size?.toLongOrNull() != file.length()) {
                    return@withContext Result.failure(
                        IllegalStateException("Drive upload size mismatch for $id")
                    )
                }

                currentAccount = currentAccount.copy(
                    usedBytes = currentAccount.usedBytes + file.length(),
                    lastSyncTimestamp = System.currentTimeMillis()
                )
                Result.success(id)
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
        if (folderReference.isBlank() || folderReference == "root") return "root"
        if (looksLikeDriveId(folderReference)) return folderReference

        val escapedName = folderReference.replace("\\", "\\\\").replace("'", "\\'")
        val query = "name = '$escapedName' and mimeType = 'application/vnd.google-apps.folder' and trashed = false"
        val existing = driveApi.listFiles(bearer(), query = query, pageSize = 10).files.firstOrNull()
        if (existing?.id != null) return existing.id

        val metadata = """{"name":"$folderReference","mimeType":"application/vnd.google-apps.folder","parents":["root"]}"""
            .toRequestBody("application/json; charset=UTF-8".toMediaType())
        return driveApi.createFolder(bearer(), metadata).id
            ?: throw IllegalStateException("Drive folder creation returned no id for '$folderReference'")
    }

    private fun looksLikeDriveId(value: String): Boolean =
        value.length >= 10 && !value.contains(' ') && !value.contains('/')

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
