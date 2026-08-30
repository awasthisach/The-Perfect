package com.vvf.smartmanager.core.cloud.gdrive

import com.vvf.smartmanager.core.model.FileItem

/**
 * Service contract for Google Drive core synchronization using REST API and Credential Manager.
 */
interface GoogleDriveService {
    suspend fun authenticate(): Result<Boolean>
    suspend fun listDriveFiles(folderId: String = "root"): Result<List<FileItem>>
    suspend fun uploadFile(localFile: FileItem, remoteFolderId: String = "root"): Result<String>
    suspend fun downloadFile(fileId: String, destinationPath: String): Result<Boolean>
    suspend fun getStorageQuota(): Result<Pair<Long, Long>>
}
