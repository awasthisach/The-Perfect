package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.model.CloudAccount
import com.vvf.smartmanager.core.model.CloudBackupInfo
import com.vvf.smartmanager.core.model.CloudProviderType
import com.vvf.smartmanager.core.model.CloudSyncItem
import com.vvf.smartmanager.core.model.CloudSyncStatus
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.plugin.spi.CloudDriverSPI
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Unified Cloud Core & Plugin Driver Domain UseCase.
 * Handles authentication, multi-provider syncing, automated database/vault snapshots,
 * and quota analytics across Google Drive (Core) and modular plugins (OneDrive, Dropbox, S3, NextCloud, NAS).
 */
class CloudSyncUseCase(
    private val googleDriveService: GoogleDriveService,
    private val pluginDrivers: Map<CloudProviderType, CloudDriverSPI> = emptyMap()
) {

    private val _syncState = MutableStateFlow(CloudSyncStatus.IDLE)
    val syncState = _syncState.asStateFlow()

    private val _syncQueue = MutableStateFlow<List<CloudSyncItem>>(emptyList())
    val syncQueue = _syncQueue.asStateFlow()

    suspend fun authenticateProvider(providerType: CloudProviderType): Result<Boolean> {
        return if (providerType == CloudProviderType.GOOGLE_DRIVE) {
            googleDriveService.authenticate()
        } else {
            val driver = pluginDrivers[providerType]
            if (driver != null) {
                val success = driver.authenticate()
                Result.success(success)
            } else {
                Result.failure(IllegalArgumentException("Plugin for ${providerType.displayName} is not installed or enabled."))
            }
        }
    }

    suspend fun getAccount(providerType: CloudProviderType): CloudAccount {
        return if (providerType == CloudProviderType.GOOGLE_DRIVE) {
            googleDriveService.getStorageQuota().fold(
                onSuccess = { quota ->
                    CloudAccount(
                        providerType = CloudProviderType.GOOGLE_DRIVE,
                        displayName = "Google Drive",
                        isConnected = true,
                        usedBytes = quota.first,
                        totalBytes = quota.second,
                        autoSyncEnabled = true
                    )
                },
                onFailure = {
                    CloudAccount(
                        providerType = CloudProviderType.GOOGLE_DRIVE,
                        displayName = "Google Drive",
                        isConnected = false
                    )
                }
            )
        } else {
            val driver = pluginDrivers[providerType]
            if (driver != null) {
                runCatching { driver.getQuotaUsage() }.fold(
                    onSuccess = { quota ->
                        CloudAccount(
                            providerType = providerType,
                            displayName = driver.displayName,
                            isConnected = true,
                            usedBytes = quota.first,
                            totalBytes = quota.second,
                            autoSyncEnabled = false
                        )
                    },
                    onFailure = {
                        CloudAccount(
                            providerType = providerType,
                            displayName = driver.displayName,
                            isConnected = false
                        )
                    }
                )
            } else {
                CloudAccount(
                    providerType = providerType,
                    displayName = providerType.displayName,
                    isConnected = false,
                    usedBytes = 0L,
                    totalBytes = 0L
                )
            }
        }
    }

    suspend fun listRemoteFiles(providerType: CloudProviderType, path: String = "root"): Result<List<FileItem>> {
        return if (providerType == CloudProviderType.GOOGLE_DRIVE) {
            googleDriveService.listDriveFiles(path)
        } else {
            val driver = pluginDrivers[providerType]
            if (driver != null) {
                Result.success(driver.listRemoteFiles(path))
            } else {
                Result.failure(IllegalStateException("Provider plugin not found"))
            }
        }
    }

    suspend fun createCloudBackup(
        providerType: CloudProviderType,
        includeVault: Boolean = false,
        includeDatabase: Boolean = true
    ): Result<CloudBackupInfo> {
        _syncState.value = CloudSyncStatus.ERROR
        return Result.failure(
            UnsupportedOperationException(
                "Cloud backup is unavailable: no real archive/export pipeline is configured for ${providerType.displayName}."
            )
        )
    }

    suspend fun restoreCloudBackup(backupInfo: CloudBackupInfo): Result<Boolean> {
        _syncState.value = CloudSyncStatus.ERROR
        return Result.failure(
            UnsupportedOperationException(
                "Cloud restore is unavailable until archive download and verified local restore are implemented for ${backupInfo.backupName}."
            )
        )
    }

    suspend fun syncFileToCloud(
        fileItem: FileItem,
        providerType: CloudProviderType = CloudProviderType.GOOGLE_DRIVE
    ): Result<CloudSyncItem> {
        _syncState.value = CloudSyncStatus.UPLOADING
        val itemId = UUID.randomUUID().toString()
        return try {
            val remoteId = if (providerType == CloudProviderType.GOOGLE_DRIVE) {
                googleDriveService.uploadFile(fileItem).getOrElse { throw it }
            } else {
                val driver = pluginDrivers[providerType]
                    ?: throw IllegalStateException("Provider plugin not found")
                if (!driver.uploadFile(fileItem, "root")) {
                    throw IllegalStateException("${driver.displayName} rejected the upload")
                }
                "${providerType.name.lowercase()}:$itemId"
            }
            val syncItem = CloudSyncItem(
                id = itemId,
                fileName = fileItem.name,
                localPath = fileItem.path,
                remotePath = remoteId,
                fileSize = fileItem.sizeBytes,
                status = CloudSyncStatus.SUCCESS,
                progress = 1.0f
            )
            _syncQueue.value = listOf(syncItem) + _syncQueue.value
            _syncState.value = CloudSyncStatus.SUCCESS
            Result.success(syncItem)
        } catch (error: Throwable) {
            val failedItem = CloudSyncItem(
                id = itemId,
                fileName = fileItem.name,
                localPath = fileItem.path,
                remotePath = "",
                fileSize = fileItem.sizeBytes,
                status = CloudSyncStatus.ERROR,
                errorMessage = error.message ?: "Cloud upload failed"
            )
            _syncQueue.value = listOf(failedItem) + _syncQueue.value
            _syncState.value = CloudSyncStatus.ERROR
            Result.failure(error)
        }
    }
}
