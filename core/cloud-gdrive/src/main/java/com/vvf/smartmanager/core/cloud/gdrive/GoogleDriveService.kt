package com.vvf.smartmanager.core.cloud.gdrive

import com.vvf.smartmanager.core.model.CloudBackupInfo
import com.vvf.smartmanager.core.model.FileItem

data class CloudBackupListing(
    val backups: List<CloudBackupInfo>,
    val legacyBackupCount: Int
)

/**
 * Service contract for Google Drive core synchronization using REST API and Credential Manager.
 */
interface GoogleDriveService {
    /** Retains the most recent interactive token; API calls re-authorize through AuthorizationClient. */
    fun setAccessToken(token: String?) = Unit

    suspend fun authenticate(): Result<Boolean>
    suspend fun listDriveFiles(folderId: String = "root"): Result<List<FileItem>>
    suspend fun uploadFile(localFile: FileItem, remoteFolderId: String = "root"): Result<String>
    /** Uploads a backup and persists its trusted SHA-256 in provider metadata. */
    suspend fun uploadBackup(
        localFile: FileItem,
        remoteFolderId: String = "root",
        checksumSha256: String
    ): Result<String>
    /** Lists only backups with a valid durable checksum; legacy entries are excluded. */
    suspend fun listCloudBackups(folderId: String = "VVF_Backups"): Result<CloudBackupListing> =
        Result.failure(UnsupportedOperationException("Durable Drive backup listing is unavailable"))
    suspend fun downloadFile(fileId: String, destinationPath: String): Result<Boolean>
    suspend fun disconnect(): Result<Unit>
    suspend fun getStorageQuota(): Result<Pair<Long, Long>>
}
