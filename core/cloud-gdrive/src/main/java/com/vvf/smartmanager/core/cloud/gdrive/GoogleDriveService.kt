package com.vvf.smartmanager.core.cloud.gdrive

import com.vvf.smartmanager.core.model.FileItem

/** Google Drive synchronization and explicitly user-confirmed mutations. */
interface GoogleDriveService {
    fun setAccessToken(token: String?) = Unit
    suspend fun authenticate(): Result<Boolean>
    suspend fun listDriveFiles(folderId: String = "root"): Result<List<FileItem>>
    suspend fun uploadFile(localFile: FileItem, remoteFolderId: String = "root"): Result<String>
    suspend fun downloadFile(fileId: String, destinationPath: String): Result<Boolean>
    suspend fun downloadFileBounded(fileId: String, destinationPath: String, maxBytes: Long, mimeType: String? = null): Result<Boolean> = Result.failure(UnsupportedOperationException("Bounded download is not implemented"))
    /** Returns bounded plain text for supported text files and Google Workspace exports. */
    suspend fun extractTextContent(fileId: String, mimeType: String?): Result<String> = Result.failure(UnsupportedOperationException("Text extraction is not implemented"))
    suspend fun getStorageQuota(): Result<Pair<Long, Long>>
    suspend fun listAllDriveFiles(maxFiles: Int = 20_000): Result<DriveIndexSnapshot> = Result.failure(UnsupportedOperationException("Drive listing is not implemented"))
    suspend fun getDriveChanges(startPageToken: String, maxPages: Int = 200): Result<DriveChangeSnapshot> = Result.failure(UnsupportedOperationException("Drive changes are not implemented"))
    suspend fun getDriveStartPageToken(): Result<String> = Result.failure(UnsupportedOperationException("Drive change cursor is not implemented"))
    suspend fun syncDriveSnapshot(startPageToken: String?): Result<DriveSyncSnapshot> = Result.failure(UnsupportedOperationException("Drive sync is not implemented"))
    suspend fun moveFile(fileId: String, destinationFolderId: String): Result<Boolean> = Result.failure(UnsupportedOperationException("Drive move is not implemented"))
    suspend fun setStarred(fileId: String, starred: Boolean): Result<Boolean> = Result.failure(UnsupportedOperationException("Drive star is not implemented"))
    suspend fun createDriveFolder(name: String, parentFolderId: String = "root"): Result<String> = Result.failure(UnsupportedOperationException("Drive folder creation is not implemented"))
}
data class DriveIndexSnapshot(val files: List<FileItem>, val capped: Boolean, val nextPageToken: String?)
data class DriveChangeSnapshot(val changes: List<DriveChangeDto>, val nextPageToken: String?, val newStartPageToken: String?)

data class DriveSyncSnapshot(
    val fullListing: DriveIndexSnapshot? = null,
    val changes: DriveChangeSnapshot? = null,
    val cursor: String,
    val fullResyncRequired: Boolean,
    val capped: Boolean = false
)
