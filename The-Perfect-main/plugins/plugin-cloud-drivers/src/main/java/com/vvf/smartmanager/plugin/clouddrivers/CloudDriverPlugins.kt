package com.vvf.smartmanager.plugin.clouddrivers

import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.plugin.spi.CloudDriverSPI

/**
 * Provider drivers are deliberately fail-closed until a real provider SDK/API implementation
 * is wired in. Returning synthetic files, quotas, or successful transfers would make the UI
 * report data loss/sync success that never happened.
 */
private abstract class UnimplementedCloudDriver(
    final override val driverId: String,
    final override val displayName: String,
    final override val iconResName: String
) : CloudDriverSPI {
    override suspend fun authenticate(): Boolean = false

    override suspend fun listRemoteFiles(remotePath: String): List<FileItem> = emptyList()

    override suspend fun uploadFile(localFile: FileItem, remoteDirectory: String): Boolean = false

    override suspend fun downloadFile(remoteFile: FileItem, localDestination: String): Boolean = false

    override suspend fun getQuotaUsage(): Pair<Long, Long> = 0L to 0L
}

class OneDriveDriverImpl : UnimplementedCloudDriver(
    driverId = "plugin.cloud.onedrive",
    displayName = "Microsoft OneDrive",
    iconResName = "ic_onedrive"
)

class DropboxDriverImpl : UnimplementedCloudDriver(
    driverId = "plugin.cloud.dropbox",
    displayName = "Dropbox",
    iconResName = "ic_dropbox"
)

class NextCloudDriverImpl : UnimplementedCloudDriver(
    driverId = "plugin.cloud.nextcloud",
    displayName = "NextCloud (Self-Hosted)",
    iconResName = "ic_nextcloud"
)

class S3StorageDriverImpl : UnimplementedCloudDriver(
    driverId = "plugin.cloud.s3",
    displayName = "AWS S3 / S3-Compatible",
    iconResName = "ic_s3"
)

class LocalNasDriverImpl : UnimplementedCloudDriver(
    driverId = "plugin.cloud.nas",
    displayName = "Local NAS (WebDAV/SMB)",
    iconResName = "ic_nas"
)
