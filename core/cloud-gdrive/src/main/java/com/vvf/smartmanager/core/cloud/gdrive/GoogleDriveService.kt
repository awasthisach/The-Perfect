package com.vvf.smartmanager.core.cloud.gdrive

import com.vvf.smartmanager.core.model.FileItem

/**
 * Service contract for Google Drive core synchronization using REST API and Credential Manager.
 */
interface GoogleDriveService {
    /** Stores a token produced by the activity-owned Google sign-in flow. */
    fun setAccessToken(token: String?) = Unit

    suspend fun authenticate(): Result<Boolean>
    suspend fun listDriveFiles(folderId: String = "root"): Result<List<FileItem>>
    suspend fun uploadFile(localFile: FileItem, remoteFolderId: String = "root"): Result<String>
    suspend fun downloadFile(fileId: String, destinationPath: String): Result<Boolean>
    /** Returns the SHA-256 recorded in this app's Drive appProperties for a backup file. */
    suspend fun getFileSha256(fileId: String): Result<String> = Result.failure(
        UnsupportedOperationException("Provider does not expose a trusted backup SHA-256")
    )
    suspend fun getStorageQuota(): Result<Pair<Long, Long>>
}
