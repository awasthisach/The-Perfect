package com.vvf.smartmanager.core.cloud.gdrive

import com.vvf.smartmanager.core.model.FileItem

/**
 * Service contract for Google Drive core synchronization using REST API and Credential Manager.
 */
data class DriveFileListing(
    val files: List<FileItem>,
    val incomplete: Boolean,
    val nextPageToken: String?
)

interface GoogleDriveService {
    /** Stores a token produced by the activity-owned Google sign-in flow. */
    fun setAccessToken(token: String?) = Unit

    suspend fun authenticate(): Result<Boolean>
    suspend fun listDriveFiles(folderId: String = "root"): Result<List<FileItem>>
    suspend fun listAllDriveFiles(): Result<DriveFileListing> =
        Result.failure(UnsupportedOperationException("Full Drive listing is not implemented by this provider."))
    suspend fun createFolder(name: String, parentFolderId: String = "root"): Result<String> =
        Result.failure(UnsupportedOperationException("Folder creation is not implemented by this provider."))
    suspend fun moveFile(fileId: String, targetFolderId: String): Result<Boolean> =
        Result.failure(UnsupportedOperationException("Moving files is not implemented by this provider."))
    suspend fun starFile(fileId: String, starred: Boolean): Result<Boolean> =
        Result.failure(UnsupportedOperationException("Star updates are not implemented by this provider."))
    suspend fun uploadFile(localFile: FileItem, remoteFolderId: String = "root"): Result<String>
    suspend fun downloadFile(fileId: String, destinationPath: String): Result<Boolean>
    suspend fun getStorageQuota(): Result<Pair<Long, Long>>
}
