package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.domain.backup.ArchiveService
import com.vvf.smartmanager.core.domain.cloud.DurableUploadContract
import com.vvf.smartmanager.core.domain.restore.RestorePipeline
import com.vvf.smartmanager.core.domain.restore.RestoreResult
import com.vvf.smartmanager.core.model.CloudAccount
import com.vvf.smartmanager.core.model.CloudBackupInfo
import com.vvf.smartmanager.core.model.CloudProviderType
import com.vvf.smartmanager.core.model.CloudSyncItem
import com.vvf.smartmanager.core.model.CloudSyncStatus
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.plugin.spi.CloudDriverSPI
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.UUID

/**
 * Unified Cloud Core & Plugin Driver Domain UseCase.
 * Handles authentication, multi-provider syncing, automated database/vault snapshots,
 * and quota analytics across Google Drive (Core) and modular plugins.
 */
class CloudSyncUseCase(
    private val googleDriveService: GoogleDriveService,
    private val pluginDrivers: Map<CloudProviderType, CloudDriverSPI> = emptyMap(),
    private val archiveService: ArchiveService? = null,
    private val restorePipeline: RestorePipeline? = null
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
                Result.success(driver.authenticate())
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
                        autoSyncEnabled = false
                    )
                },
                onFailure = {
                    CloudAccount(providerType = CloudProviderType.GOOGLE_DRIVE, isConnected = false)
                }
            )
        } else {
            val driver = pluginDrivers[providerType]
            if (driver != null) {
                val quota = driver.getStorageQuota()
                CloudAccount(
                    providerType = providerType,
                    displayName = driver.displayName,
                    isConnected = true,
                    usedBytes = quota.first,
                    totalBytes = quota.second,
                    autoSyncEnabled = false
                )
            } else {
                CloudAccount(providerType = providerType, isConnected = false)
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
        val service = archiveService
        if (service == null) {
            _syncState.value = CloudSyncStatus.ERROR
            return Result.failure(
                UnsupportedOperationException(
                    "Cloud backup is unavailable: no real archive/export pipeline is configured for ${providerType.displayName}."
                )
            )
        }

        _syncState.value = CloudSyncStatus.SYNCING
        return service.createArchive(
            includeVault = includeVault,
            includeDatabase = includeDatabase
        ).fold(
            onSuccess = { artifact ->
                try {
                    val fileItem = FileItem(
                        path = artifact.file.absolutePath,
                        name = artifact.file.name,
                        sizeBytes = artifact.file.length(),
                        lastModified = artifact.file.lastModified(),
                        isDirectory = false,
                        mimeType = "application/octet-stream"
                    )
                    val uploadResult = if (providerType == CloudProviderType.GOOGLE_DRIVE) {
                        googleDriveService.uploadFile(fileItem, "VVF_Backups")
                    } else {
                        val driver = pluginDrivers[providerType]
                            ?: return@fold Result.failure(IllegalStateException("Provider plugin not found"))
                        if (driver.uploadFile(fileItem, "root")) {
                            Result.success(artifact.backupInfo.backupId)
                        } else {
                            Result.failure(IllegalStateException("${driver.displayName} rejected the backup upload"))
                        }
                    }
                    uploadResult.fold(
                        onSuccess = { remoteId ->
                            try {
                                val durableId = DurableUploadContract.requireDurableRemoteId(remoteId)
                                _syncState.value = CloudSyncStatus.SUCCESS
                                Result.success(artifact.backupInfo.copy(backupId = durableId))
                            } catch (error: IllegalArgumentException) {
                                _syncState.value = CloudSyncStatus.ERROR
                                Result.failure(error)
                            }
                        },
                        onFailure = { error ->
                            _syncState.value = CloudSyncStatus.ERROR
                            Result.failure(error)
                        }
                    )
                } catch (error: Throwable) {
                    if (error is CancellationException) throw error
                    _syncState.value = CloudSyncStatus.ERROR
                    Result.failure(error)
                } finally {
                    artifact.file.delete()
                }
            },
            onFailure = { error ->
                _syncState.value = CloudSyncStatus.ERROR
                Result.failure(error)
            }
        )
    }

    suspend fun restoreCloudBackup(backupInfo: CloudBackupInfo): Result<Boolean> {
        val pipeline = restorePipeline
        if (pipeline == null) {
            _syncState.value = CloudSyncStatus.ERROR
            return Result.failure(
                UnsupportedOperationException(
                    "Cloud restore is unavailable: no FailClosedRestorePipeline is configured for ${backupInfo.backupName}."
                )
            )
        }
        if (backupInfo.backupId.isBlank()) {
            _syncState.value = CloudSyncStatus.ERROR
            return Result.failure(IllegalArgumentException("Backup id is blank; refusing restore"))
        }
        _syncState.value = CloudSyncStatus.DOWNLOADING
        return pipeline.restore(backupInfo.backupId, backupInfo.checksumSha256).fold(
            onSuccess = { result: RestoreResult ->
                if (result.success) {
                    _syncState.value = CloudSyncStatus.SUCCESS
                    Result.success(true)
                } else {
                    _syncState.value = CloudSyncStatus.ERROR
                    Result.failure(IllegalStateException(result.message.ifBlank { "Restore completed without success" }))
                }
            },
            onFailure = { error ->
                _syncState.value = CloudSyncStatus.ERROR
                Result.failure(error)
            }
        )
    }

    suspend fun syncFileToCloud(
        fileItem: FileItem,
        providerType: CloudProviderType = CloudProviderType.GOOGLE_DRIVE
    ): Result<CloudSyncItem> {
        _syncState.value = CloudSyncStatus.UPLOADING
        val itemId = UUID.randomUUID().toString()
        return try {
            val rawRemoteId = if (providerType == CloudProviderType.GOOGLE_DRIVE) {
                googleDriveService.uploadFile(fileItem).getOrElse { throw it }
            } else {
                val driver = pluginDrivers[providerType]
                    ?: throw IllegalStateException("Provider plugin not found")
                if (!driver.uploadFile(fileItem, "root")) {
                    throw IllegalStateException("${driver.displayName} rejected the upload")
                }
                "${providerType.name.lowercase()}:$itemId"
            }
            val remoteId = DurableUploadContract.requireDurableRemoteId(rawRemoteId)
            val syncItem = CloudSyncItem(
                id = itemId,
                fileName = fileItem.name,
                localPath = fileItem.path,
                remotePath = remoteId,
                fileSize = fileItem.sizeBytes,
                status = CloudSyncStatus.SUCCESS,
                progress = 1f
            )
            _syncQueue.value = _syncQueue.value + syncItem
            _syncState.value = CloudSyncStatus.SUCCESS
            Result.success(syncItem)
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            val failed = CloudSyncItem(
                id = itemId,
                fileName = fileItem.name,
                localPath = fileItem.path,
                remotePath = "",
                fileSize = fileItem.sizeBytes,
                status = CloudSyncStatus.ERROR,
                errorMessage = error.message ?: "Cloud upload failed"
            )
            _syncQueue.value = _syncQueue.value + failed
            _syncState.value = CloudSyncStatus.ERROR
            Result.failure(error)
        }
    }
}
