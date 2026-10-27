package com.vvf.smartmanager.core.database

import androidx.room.Database
import androidx.room.RoomDatabase
import com.vvf.smartmanager.core.database.dao.CloudSyncDao
import com.vvf.smartmanager.core.database.dao.FileDao
import com.vvf.smartmanager.core.database.dao.SearchFtsDao
import com.vvf.smartmanager.core.database.dao.VaultDao
import com.vvf.smartmanager.core.database.model.CloudSyncEntity
import com.vvf.smartmanager.core.database.model.FileFtsEntity
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import com.vvf.smartmanager.core.database.model.VaultItemEntity

/**
 * Test-only schema representing the pre-v2 encrypted Room database.
 * The production v1 schema had no vault_journal table; MIGRATION_1_2 adds it.
 */
@Database(
    entities = [
        FileMetadataEntity::class,
        FileFtsEntity::class,
        VaultItemEntity::class,
        CloudSyncEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class LegacyV1Database : RoomDatabase() {
    abstract fun fileDao(): FileDao
    abstract fun searchFtsDao(): SearchFtsDao
    abstract fun vaultDao(): VaultDao
    abstract fun cloudSyncDao(): CloudSyncDao
}
