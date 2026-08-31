package com.vvf.smartmanager.plugin.clouddrivers

import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.plugin.spi.CloudDriverSPI

/**
 * Cloud driver implementations intentionally fail closed until a provider-specific,
 * credentialed transport is supplied. They must never report synthetic authentication,
 * remote files, quota, uploads, or downloads as successful.
 */
private abstract class UnconfiguredCloudDriver : CloudDriverSPI {
    override suspend fun authenticate(): Boolean = false
    override suspend fun listRemoteFiles(remotePath: String): List<FileItem> = emptyList()
    override suspend fun uploadFile(localFile: FileItem, remoteDirectory: String): Boolean = false
    override suspend fun downloadFile(remoteFile: FileItem, localDestination: String): Boolean = false
    override suspend fun getQuotaUsage(): Pair<Long, Long> = 0L to 0L
}

class OneDriveDriverImpl : UnconfiguredCloudDriver() {
    override val driverId = "plugin.cloud.onedrive"
    override val displayName = "Microsoft OneDrive"
    override val iconResName = "ic_onedrive"
}

class DropboxDriverImpl : UnconfiguredCloudDriver() {
    override val driverId = "plugin.cloud.dropbox"
    override val displayName = "Dropbox"
    override val iconResName = "ic_dropbox"
}

class NextCloudDriverImpl : UnconfiguredCloudDriver() {
    override val driverId = "plugin.cloud.nextcloud"
    override val displayName = "NextCloud (Self-Hosted)"
    override val iconResName = "ic_nextcloud"
}

class S3StorageDriverImpl : UnconfiguredCloudDriver() {
    override val driverId = "plugin.cloud.s3"
    override val displayName = "AWS S3 / S3-Compatible"
    override val iconResName = "ic_s3"
}

class LocalNasDriverImpl : UnconfiguredCloudDriver() {
    override val driverId = "plugin.cloud.nas"
    override val displayName = "Local NAS (WebDAV/SMB)"
    override val iconResName = "ic_nas"
}
