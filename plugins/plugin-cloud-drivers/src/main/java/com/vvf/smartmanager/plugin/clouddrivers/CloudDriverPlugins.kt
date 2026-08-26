package com.vvf.smartmanager.plugin.clouddrivers

import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.plugin.spi.CloudDriverSPI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Microsoft OneDrive Cloud Plugin Driver.
 */
class OneDriveDriverImpl : CloudDriverSPI {
    override val driverId: String = "plugin.cloud.onedrive"
    override val displayName: String = "Microsoft OneDrive"
    override val iconResName: String = "ic_onedrive"

    private var isAuth = false
    private var used = 1_500_000_000L
    private val total = 5_000_000_000L

    override suspend fun authenticate(): Boolean = withContext(Dispatchers.IO) {
        delay(400)
        isAuth = true
        true
    }

    override suspend fun listRemoteFiles(remotePath: String): List<FileItem> = withContext(Dispatchers.IO) {
        if (!isAuth) return@withContext emptyList()
        delay(300)
        listOf(
            FileItem(
                path = "onedrive://Documents/OfficeReport_2026.docx",
                name = "OfficeReport_2026.docx",
                sizeBytes = 1_200_000L,
                lastModified = System.currentTimeMillis() - 86400000L,
                isDirectory = false,
                mimeType = "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
            )
        )
    }

    override suspend fun uploadFile(localFile: FileItem, remoteDirectory: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuth) return@withContext false
        delay(400)
        used += localFile.sizeBytes
        true
    }

    override suspend fun downloadFile(remoteFile: FileItem, localDestination: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuth) return@withContext false
        delay(400)
        true
    }

    override suspend fun getQuotaUsage(): Pair<Long, Long> = Pair(used, total)
}

/**
 * Dropbox Cloud Plugin Driver.
 */
class DropboxDriverImpl : CloudDriverSPI {
    override val driverId: String = "plugin.cloud.dropbox"
    override val displayName: String = "Dropbox"
    override val iconResName: String = "ic_dropbox"

    private var isAuth = false
    private var used = 800_000_000L
    private val total = 2_000_000_000L

    override suspend fun authenticate(): Boolean = withContext(Dispatchers.IO) {
        delay(400)
        isAuth = true
        true
    }

    override suspend fun listRemoteFiles(remotePath: String): List<FileItem> = withContext(Dispatchers.IO) {
        if (!isAuth) return@withContext emptyList()
        delay(300)
        listOf(
            FileItem(
                path = "dropbox://Photos/Family_Vacation.jpg",
                name = "Family_Vacation.jpg",
                sizeBytes = 4_500_000L,
                lastModified = System.currentTimeMillis() - 172800000L,
                isDirectory = false,
                mimeType = "image/jpeg"
            )
        )
    }

    override suspend fun uploadFile(localFile: FileItem, remoteDirectory: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuth) return@withContext false
        delay(400)
        used += localFile.sizeBytes
        true
    }

    override suspend fun downloadFile(remoteFile: FileItem, localDestination: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuth) return@withContext false
        delay(400)
        true
    }

    override suspend fun getQuotaUsage(): Pair<Long, Long> = Pair(used, total)
}

/**
 * NextCloud Cloud Plugin Driver.
 */
class NextCloudDriverImpl : CloudDriverSPI {
    override val driverId: String = "plugin.cloud.nextcloud"
    override val displayName: String = "NextCloud (Self-Hosted)"
    override val iconResName: String = "ic_nextcloud"

    private var isAuth = false
    private var used = 12_000_000_000L
    private val total = 50_000_000_000L

    override suspend fun authenticate(): Boolean = withContext(Dispatchers.IO) {
        delay(400)
        isAuth = true
        true
    }

    override suspend fun listRemoteFiles(remotePath: String): List<FileItem> = withContext(Dispatchers.IO) {
        if (!isAuth) return@withContext emptyList()
        delay(300)
        listOf(
            FileItem(
                path = "nextcloud://Private/Nextcloud_Sync_Archive.zip",
                name = "Nextcloud_Sync_Archive.zip",
                sizeBytes = 28_000_000L,
                lastModified = System.currentTimeMillis() - 43200000L,
                isDirectory = false,
                mimeType = "application/zip"
            )
        )
    }

    override suspend fun uploadFile(localFile: FileItem, remoteDirectory: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuth) return@withContext false
        delay(400)
        used += localFile.sizeBytes
        true
    }

    override suspend fun downloadFile(remoteFile: FileItem, localDestination: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuth) return@withContext false
        delay(400)
        true
    }

    override suspend fun getQuotaUsage(): Pair<Long, Long> = Pair(used, total)
}

/**
 * Amazon S3 / MinIO / S3-Compatible Cloud Plugin Driver.
 */
class S3StorageDriverImpl : CloudDriverSPI {
    override val driverId: String = "plugin.cloud.s3"
    override val displayName: String = "AWS S3 / S3-Compatible"
    override val iconResName: String = "ic_s3"

    private var isAuth = false
    private var used = 35_000_000_000L
    private val total = 100_000_000_000L

    override suspend fun authenticate(): Boolean = withContext(Dispatchers.IO) {
        delay(400)
        isAuth = true
        true
    }

    override suspend fun listRemoteFiles(remotePath: String): List<FileItem> = withContext(Dispatchers.IO) {
        if (!isAuth) return@withContext emptyList()
        delay(300)
        listOf(
            FileItem(
                path = "s3://vvf-backup-bucket/system_logs_2026.log",
                name = "system_logs_2026.log",
                sizeBytes = 512_000L,
                lastModified = System.currentTimeMillis() - 1200000L,
                isDirectory = false,
                mimeType = "text/plain"
            )
        )
    }

    override suspend fun uploadFile(localFile: FileItem, remoteDirectory: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuth) return@withContext false
        delay(400)
        used += localFile.sizeBytes
        true
    }

    override suspend fun downloadFile(remoteFile: FileItem, localDestination: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuth) return@withContext false
        delay(400)
        true
    }

    override suspend fun getQuotaUsage(): Pair<Long, Long> = Pair(used, total)
}

/**
 * Local Network NAS (WebDAV / SMB) Cloud Plugin Driver.
 */
class LocalNasDriverImpl : CloudDriverSPI {
    override val driverId: String = "plugin.cloud.nas"
    override val displayName: String = "Local NAS (WebDAV/SMB)"
    override val iconResName: String = "ic_nas"

    private var isAuth = false
    private var used = 450_000_000_000L
    private val total = 2_000_000_000_000L // 2 TB NAS drive

    override suspend fun authenticate(): Boolean = withContext(Dispatchers.IO) {
        delay(300)
        isAuth = true
        true
    }

    override suspend fun listRemoteFiles(remotePath: String): List<FileItem> = withContext(Dispatchers.IO) {
        if (!isAuth) return@withContext emptyList()
        delay(200)
        listOf(
            FileItem(
                path = "smb://192.168.1.100/storage/Media_Library_Backup.iso",
                name = "Media_Library_Backup.iso",
                sizeBytes = 2_400_000_000L,
                lastModified = System.currentTimeMillis() - 864000000L,
                isDirectory = false,
                mimeType = "application/x-iso9660-image"
            )
        )
    }

    override suspend fun uploadFile(localFile: FileItem, remoteDirectory: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuth) return@withContext false
        delay(300)
        used += localFile.sizeBytes
        true
    }

    override suspend fun downloadFile(remoteFile: FileItem, localDestination: String): Boolean = withContext(Dispatchers.IO) {
        if (!isAuth) return@withContext false
        delay(300)
        true
    }

    override suspend fun getQuotaUsage(): Pair<Long, Long> = Pair(used, total)
}
