package com.vvf.smartmanager.core.model

/**
 * Representation of cloud storage providers (Google Drive core, OneDrive, Dropbox, NextCloud, S3, NAS).
 */
enum class CloudProviderType(val displayName: String, val isCore: Boolean) {
    GOOGLE_DRIVE("Google Drive", isCore = true),
    ONE_DRIVE("Microsoft OneDrive", isCore = false),
    DROPBOX("Dropbox", isCore = false),
    NEXTCLOUD("NextCloud", isCore = false),
    AWS_S3("Amazon S3 / S3-Compatible", isCore = false),
    LOCAL_NAS("Local NAS (WebDAV/SMB)", isCore = false)
}

/**
 * Connection and authorization state for a cloud provider.
 */
data class CloudAccount(
    val providerType: CloudProviderType,
    val accountEmail: String = "",
    val displayName: String = "",
    val isConnected: Boolean = false,
    val usedBytes: Long = 0L,
    val totalBytes: Long = 0L,
    val lastSyncTimestamp: Long = 0L,
    val autoSyncEnabled: Boolean = false,
    val syncOnWifiOnly: Boolean = true
) {
    val usedPercentage: Float
        get() = if (totalBytes > 0) (usedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else 0f
}

/**
 * Cloud Sync Action status.
 */
enum class CloudSyncStatus {
    IDLE,
    AUTHENTICATING,
    SYNCING,
    UPLOADING,
    DOWNLOADING,
    SUCCESS,
    ERROR,
    PAUSED_METERED
}

/**
 * Item queued or transferred during Cloud Sync.
 */
data class CloudSyncItem(
    val id: String,
    val fileName: String,
    val localPath: String,
    val remotePath: String,
    val fileSize: Long,
    val status: CloudSyncStatus = CloudSyncStatus.IDLE,
    val progress: Float = 0f,
    val errorMessage: String? = null
)

/**
 * Cloud Backup snapshot metadata.
 */
data class CloudBackupInfo(
    val backupId: String,
    val timestamp: Long,
    val backupName: String,
    val backupSizeBytes: Long,
    val includesVault: Boolean = false,
    val includesDatabase: Boolean = true,
    val includesPreferences: Boolean = true
)
