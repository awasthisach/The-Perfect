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
    suspend fun listAllDriveFiles(maxFiles: Int = 20_000): Result<DriveIndexSnapshot> = Result.failure(UnsupportedOperationException("Drive listing is not implemented"))
    suspend fun getDriveChanges(startPageToken: String, maxPages: Int = 200): Result<DriveChangeSnapshot> = Result.failure(UnsupportedOperationException("Drive changes are not implemented"))
    suspend fun getDriveStartPageToken(): Result<String> = Result.failure(UnsupportedOperationException("Drive change cursor is not implemented"))
    suspend fun moveFile(fileId: String, destinationFolderId: String): Result<Boolean> = Result.failure(UnsupportedOperationException("Drive move is not implemented"))
    suspend fun setStarred(fileId: String, starred: Boolean): Result<Boolean> = Result.failure(UnsupportedOperationException("Drive star is not implemented"))
    suspend fun createDriveFolder(name: String, parentFolderId: String = "root"): Result<String> = Result.failure(UnsupportedOperationException("Drive folder creation is not implemented"))
}
data class DriveIndexSnapshot(val files: List<FileItem>, val capped: Boolean, val nextPageToken: String?)
data class DriveChangeSnapshot(val changes: List<DriveChangeDto>, val nextPageToken: String?, val newStartPageToken: String?)
