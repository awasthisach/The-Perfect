package com.vvf.smartmanager.core.cloud.gdrive

import android.content.Context
import com.vvf.smartmanager.core.model.CloudAccount
import com.vvf.smartmanager.core.model.CloudProviderType
import com.vvf.smartmanager.core.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.security.MessageDigest

/**
 * Google Drive implementation. Call [setAccessToken] after OAuth / Credential Manager, then use list/upload/download.
 * Without a token, operations fail with a clear error (no simulated cloud data).
 *
 * Access tokens are short-lived (~1h). This client tracks issue time and refuses likely-expired tokens
 * with a clear re-auth error. Full refresh-token rotation requires a backend or Authorization Code flow
 * (Credential Manager ID tokens alone do not yield long-lived refresh tokens).
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
        val token = accessToken
        if (token.isNullOrBlank()) {
            throw IllegalStateException("Google Drive is not signed in (missing access token)")
        }
        val ageMs = System.currentTimeMillis() - tokenIssuedAtMs
        // Google access tokens typically expire in ~3600s; refresh slightly early.
        if (tokenIssuedAtMs > 0L && ageMs > 55L * 60L * 1000L) {
            throw IllegalStateException(
                "Google Drive access token is likely expired (age=${ageMs / 1000}s). Please sign in again."
            )
        }
        return token
    }

    override suspend fun authenticate(): Result<Boolean> = withContext(Dispatchers.IO) {
        runCatching {
            bearer()
            true
        }
    }

    override suspend fun getStorageQuota(): Result<Pair<Long, Long>> = withContext(Dispatchers.IO) {
        runCatching {
            val about = driveApi.getAbout("storageQuota", bearer())
            val used = about.storageQuota?.usage?.toLongOrNull() ?: 0L
            val total = about.storageQuota?.limit?.toLongOrNull() ?: 0L
            used to total
        }
    }

    override suspend fun listDriveFiles(folderId: String): Result<List<FileItem>> = withContext(Dispatchers.IO) {
        runCatching {
            val q = if (folderId.isBlank() || folderId == "root") {
                "'root' in parents and trashed=false"
            } else {
                "'$folderId' in parents and trashed=false"
            }
            val response = driveApi.listFiles(
                query = q,
                fields = "files(id,name,mimeType,size,modifiedTime)",
                pageSize = 100,
                authorization = bearer()
            )
            response.files.orEmpty().map { f ->
                FileItem(
                    path = f.id.orEmpty(),
                    name = f.name.orEmpty(),
                    sizeBytes = f.size?.toLongOrNull() ?: 0L,
                    lastModified = 0L,
                    isDirectory = f.mimeType == "application/vnd.google-apps.folder",
                    mimeType = f.mimeType
                )
            }
        }
    }

    override suspend fun uploadFile(fileItem: FileItem, remoteFolder: String): Result<String> =
        withContext(Dispatchers.IO) {
            runCatching {
                val file = File(fileItem.path)
                require(file.isFile) { "Local file missing: ${fileItem.path}" }
                val metadataJson =
                    """{"name":"${file.name.replace("\"", "\\\"")}","parents":["${remoteFolder.ifBlank { "root" }}"]}"""
                val metadataBody = metadataJson.toRequestBody("application/json; charset=utf-8".toMediaTypeOrNull())
                val fileBody = file.asRequestBody(
                    (fileItem.mimeType ?: "application/octet-stream").toMediaTypeOrNull()
                )
                val part = MultipartBody.Part.createFormData("file", file.name, fileBody)
                val uploaded = driveApi.uploadMultipart(
                    metadata = metadataBody,
                    file = part,
                    authorization = bearer()
                )
                val id = uploaded.id?.takeIf { it.isNotBlank() }
                    ?: throw IllegalStateException("Drive upload returned empty file id")
                val localMd5 = calculateMd5(file)
                val remoteMd5 = uploaded.md5Checksum
                if (remoteMd5.isNullOrBlank()) {
                    throw IllegalStateException("Drive upload returned no integrity checksum for binary artifact $id")
                }
                if (!remoteMd5.equals(localMd5, ignoreCase = true)) {
                    throw IllegalStateException("Drive upload integrity mismatch for $id")
                }
                id
            }
        }

    override suspend fun downloadFile(remoteFileId: String, localDestination: String): Result<File> =
        withContext(Dispatchers.IO) {
            runCatching {
                val body = driveApi.downloadFile(remoteFileId, bearer())
                val out = File(localDestination)
                out.parentFile?.mkdirs()
                body.byteStream().use { input ->
                    out.outputStream().use { output -> input.copyTo(output) }
                }
                out
            }
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
}
