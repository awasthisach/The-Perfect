package com.vvf.smartmanager.core.database.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entity for tracking cloud backup and synchronization states across providers.
 */
@Entity(
    tableName = "cloud_sync_records",
    indices = [
        Index(value = ["localPath", "provider"], unique = true),
        Index(value = ["status"]),
        Index(value = ["provider"])
    ]
)
data class CloudSyncEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val localPath: String,
    val remoteFileId: String?,
    val provider: String, // GDRIVE, ONEDRIVE, DROPBOX, NEXTCLOUD, S3, NAS
    val status: String, // SYNCED, PENDING, UPLOADING, ERROR
    val lastSyncedAt: Long = System.currentTimeMillis(),
    val errorMessage: String? = null
)
