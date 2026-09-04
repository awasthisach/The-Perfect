package com.vvf.smartmanager.plugin.clouddrivers

import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.plugin.spi.CloudDriverSPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SPI placeholders for non-Drive providers.
 *
 * These drivers intentionally do **not** simulate successful cloud I/O.
 * Production cloud backup/restore for Google Drive uses [com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveServiceImpl].
 * Real OneDrive/Dropbox/Nextcloud/S3/NAS integrations land in a later release.
 */
private abstract class UnavailableCloudDriver(
    override val driverId: String,
    override val displayName: String,
    override val iconResName: String
) : CloudDriverSPI {
    override suspend fun authenticate(): Boolean = withContext(Dispatchers.IO) { false }

    override suspend fun listRemoteFiles(remotePath: String): List<FileItem> =
        withContext(Dispatchers.IO) { emptyList() }

    override suspend fun uploadFile(localFile: FileItem, remoteDirectory: String): Boolean =
        withContext(Dispatchers.IO) { false }

    override suspend fun downloadFile(remoteFile: FileItem, localDestination: String): Boolean =
        withContext(Dispatchers.IO) { false }

    override suspend fun getQuotaUsage(): Pair<Long, Long> = 0L to 0L
}

class OneDriveDriverImpl : UnavailableCloudDriver(
    driverId = "plugin.cloud.onedrive",
    displayName = "Microsoft OneDrive (coming soon)",
    iconResName = "ic_onedrive"
)

class DropboxDriverImpl : UnavailableCloudDriver(
    driverId = "plugin.cloud.dropbox",
    displayName = "Dropbox (coming soon)",
    iconResName = "ic_dropbox"
)

class NextCloudDriverImpl : UnavailableCloudDriver(
    driverId = "plugin.cloud.nextcloud",
    displayName = "Nextcloud (coming soon)",
    iconResName = "ic_nextcloud"
)

class S3StorageDriverImpl : UnavailableCloudDriver(
    driverId = "plugin.cloud.s3",
    displayName = "Amazon S3 (coming soon)",
    iconResName = "ic_s3"
)

class LocalNasDriverImpl : UnavailableCloudDriver(
    driverId = "plugin.cloud.nas",
    displayName = "Local NAS (coming soon)",
    iconResName = "ic_nas"
)
