package com.vvf.smartmanager.core.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Durable sync/index cursor. Singleton row id=1 survives process death and WorkManager retries.
 */
@Entity(tableName = "drive_sync_state")
data class DriveSyncStateEntity(
    @PrimaryKey
    val id: Int = 1,
    val accountEmail: String? = null,
    val changeStartPageToken: String? = null,
    val changePageToken: String? = null,
    val fullListPageToken: String? = null,
    val fullListFilesSeen: Int = 0,
    val indexingCursor: String? = null,
    val indexingInProgress: Boolean = false,
    val listingIncomplete: Boolean = false,
    val lastSyncAtMs: Long? = null,
    val lastIndexAtMs: Long? = null,
    val lastError: String? = null
)
