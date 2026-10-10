package com.vvf.smartmanager.core.cloud.gdrive

import com.vvf.smartmanager.core.model.FileItem

/** Google Drive synchronization and explicitly user-confirmed mutations. */
interface GoogleDriveService {
    fun setAccessToken(token: String?) = Unit
    suspend fun authenticate(): Result<Boolean>
    suspend fun listDriveFiles(folderId: String = "root"): Result<List<FileItem>>
    suspend fun uploadFile(localFile: FileItem, remoteFolderId: String = "root"): Result<String>
    suspend fun downloadFile(fileId: String, destinationPath: String): Result<Boolean>
    suspend fun getStorageQuota(): Result<Pair<Long, Long>>
    suspend fun listAllDriveFiles(maxFiles: Int = 20_000): Result<DriveIndexSnapshot>
    suspend fun getDriveChanges(startPageToken: String, maxPages: Int = 200): Result<DriveChangeSnapshot>
    suspend fun getDriveStartPageToken(): Result<String>
    suspend fun moveFile(fileId: String, destinationFolderId: String): Result<Boolean>
    suspend fun setStarred(fileId: String, starred: Boolean): Result<Boolean>
    suspend fun createDriveFolder(name: String, parentFolderId: String = "root"): Result<String>
}
data class DriveIndexSnapshot(val files: List<FileItem>, val capped: Boolean, val nextPageToken: String?)
data class DriveChangeSnapshot(val changes: List<DriveChangeDto>, val nextPageToken: String?, val newStartPageToken: String?)
