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
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

class GoogleDriveServiceImpl(
    private val context: Context,
    private val driveApi: DriveApi = DriveNetwork.createApi()
) : GoogleDriveService {
    @Volatile private var accessToken: String? = null
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
            currentAccount = currentAccount.copy(isConnected = false, accountEmail = "", displayName = "Google Drive")
        }
    }

    private fun bearer(): String {
        val token = accessToken
        require(!token.isNullOrBlank()) { "Google Drive not authenticated. Complete OAuth / Credential Manager and call setAccessToken()." }
        return "Bearer $token"
    }

    private fun approvedLocalRoots(): List<File> = buildList {
        add(context.filesDir.canonicalFile)
        context.cacheDir?.let { add(it.canonicalFile) }
        context.getExternalFilesDirs(null).filterNotNull().forEach { add(it.canonicalFile) }
        context.getExternalCacheDirs().filterNotNull().forEach { add(it.canonicalFile) }
    }

    private fun requireApprovedLocalFile(path: String): File {
        require(path.isNotBlank()) { "Local path cannot be blank" }
        val file = File(path).canonicalFile
        require(file.isFile) { "Local file not found: $path" }
        val allowed = approvedLocalRoots().any { root ->
            file.path == root.path || file.path.startsWith(root.path + File.separator)
        }
        require(allowed) { "Access denied: local file is outside app-approved storage roots" }
        return file
    }

    private fun requireApprovedDownloadDestination(path: String): File {
        require(path.isNotBlank()) { "Destination path cannot be blank" }
        val destination = File(path).canonicalFile
        val allowed = approvedLocalRoots().any { root ->
            destination.path == root.path || destination.path.startsWith(root.path + File.separator)
        }
        require(allowed) { "Access denied: download destination is outside app-approved storage roots" }
        return destination
    }

    override suspend fun authenticate(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (accessToken.isNullOrBlank()) return@withContext Result.failure(IllegalStateException("No access token. Wire Google Sign-In / Credential Manager and call setAccessToken(token)."))
            val about = driveApi.about(bearer())
            val usage = about.storageQuota?.usage?.toLongOrNull() ?: 0L
            val limit = about.storageQuota?.limit?.toLongOrNull() ?: 0L
            currentAccount = currentAccount.copy(isConnected = true, usedBytes = usage, totalBytes = limit, lastSyncTimestamp = System.currentTimeMillis())
            Result.success(true)
        } catch (e: Exception) {
            currentAccount = currentAccount.copy(isConnected = false)
            Result.failure(e)
        }
    }

    override suspend fun listDriveFiles(folderId: String): Result<List<FileItem>> = withContext(Dispatchers.IO) {
        try {
            val parent = folderId.ifBlank { "root" }
            val response = driveApi.listFiles(bearer = bearer(), query = "'$parent' in parents and trashed = false")
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
            currentAccount = currentAccount.copy(isConnected = true, lastSyncTimestamp = System.currentTimeMillis())
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun uploadFile(localFile: FileItem, remoteFolderId: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            val file = requireApprovedLocalFile(localFile.path)
            val parent = remoteFolderId.ifBlank { "root" }
            val safeName = file.name.replace("\\", "\\\\").replace("\"", "\\\"")
            val metadataBody = """{"name":"$safeName","parents":["$parent"]}""".toRequestBody("application/json; charset=UTF-8".toMediaType())
            val mediaType = (localFile.mimeType?.takeIf { it.isNotBlank() } ?: "application/octet-stream").toMediaType()
            val part = MultipartBody.Part.createFormData("file", file.name, file.asRequestBody(mediaType))
            val uploaded = driveApi.uploadFile(bearer(), metadataBody, part)
            val id = uploaded.id ?: return@withContext Result.failure(IllegalStateException("Upload succeeded but no file id returned"))
            currentAccount = currentAccount.copy(usedBytes = currentAccount.usedBytes + file.length(), lastSyncTimestamp = System.currentTimeMillis())
            Result.success(id)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun downloadFile(fileId: String, destinationPath: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            require(fileId.isNotBlank()) { "Drive file id cannot be blank" }
            val body = driveApi.downloadFile(bearer(), fileId)
            val destination = requireApprovedDownloadDestination(destinationPath)
            destination.parentFile?.mkdirs()
            body.byteStream().use { input -> destination.outputStream().use { output -> input.copyTo(output) } }
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
            currentAccount = currentAccount.copy(isConnected = true, usedBytes = usage, totalBytes = limit)
            Result.success(usage to limit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun getAccountInfo(): CloudAccount = currentAccount

    fun disconnect() {
        setAccessToken(null)
        currentAccount = CloudAccount(providerType = CloudProviderType.GOOGLE_DRIVE, isConnected = false, usedBytes = 0L, totalBytes = 0L)
    }

    private fun parseDriveTime(iso: String?): Long {
        if (iso.isNullOrBlank()) return 0L
        return try {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(iso)?.time ?: 0L
        } catch (_: Exception) {
            try {
                SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply { timeZone = TimeZone.getTimeZone("UTC") }.parse(iso)?.time ?: 0L
            } catch (_: Exception) { 0L }
        }
    }
}
