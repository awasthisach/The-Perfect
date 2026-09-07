package com.vvf.smartmanager.plugin.clouddrivers

import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.plugin.spi.CloudDriverSPI
import com.vvf.smartmanager.core.plugin.spi.CloudUploadResult

/**
 * Modular Cloud Driver Plugin Implementation for OneDrive, Dropbox, NextCloud, S3, NAS.
 */
class GenericCloudDriverImpl(
    override val driverId: String = "generic.cloud.driver",
    override val displayName: String = "Cloud Storage Provider",
    override val iconResName: String = "ic_cloud"
) : CloudDriverSPI {
    override suspend fun authenticate(): Boolean = false
    override suspend fun listRemoteFiles(remotePath: String): List<FileItem> = emptyList()
    override suspend fun uploadFile(localFile: FileItem, remoteDirectory: String): CloudUploadResult? = null
    override suspend fun downloadFile(remoteFile: FileItem, localDestination: String): Boolean = false
    override suspend fun getQuotaUsage(): Pair<Long, Long> = Pair(0L, 0L)
}
