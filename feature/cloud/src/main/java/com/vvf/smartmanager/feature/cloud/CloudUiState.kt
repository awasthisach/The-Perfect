package com.vvf.smartmanager.feature.cloud

import com.vvf.smartmanager.core.model.CloudAccount
import com.vvf.smartmanager.core.model.CloudBackupInfo
import com.vvf.smartmanager.core.model.CloudProviderType
import com.vvf.smartmanager.core.model.CloudSyncItem
import com.vvf.smartmanager.core.model.CloudSyncStatus
import com.vvf.smartmanager.core.model.FileItem

/**
 * UI State for Cloud Management screen.
 */
data class CloudUiState(
    val selectedProvider: CloudProviderType = CloudProviderType.GOOGLE_DRIVE,
    val accounts: Map<CloudProviderType, CloudAccount> = emptyMap(),
    val remoteFiles: List<FileItem> = emptyList(),
    val syncQueue: List<CloudSyncItem> = emptyList(),
    val syncStatus: CloudSyncStatus = CloudSyncStatus.IDLE,
    val isLoading: Boolean = false,
    val isBackingUp: Boolean = false,
    val includeVaultInBackup: Boolean = false,
    val backupHistory: List<CloudBackupInfo> = emptyList(),
    val statusMessage: String? = null
)
