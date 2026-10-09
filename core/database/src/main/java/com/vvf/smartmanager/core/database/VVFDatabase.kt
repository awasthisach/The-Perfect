package com.vvf.smartmanager.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vvf.smartmanager.core.database.dao.CloudSyncDao
import com.vvf.smartmanager.core.database.dao.DriveIndexDao
import com.vvf.smartmanager.core.database.dao.FileDao
import com.vvf.smartmanager.core.database.dao.SearchFtsDao
import com.vvf.smartmanager.core.database.dao.VaultDao
import com.vvf.smartmanager.core.database.dao.VaultJournalDao
import com.vvf.smartmanager.core.database.model.CloudSyncEntity
import com.vvf.smartmanager.core.database.model.DriveIndexFileEntity
import com.vvf.smartmanager.core.database.model.DriveSyncStateEntity
import com.vvf.smartmanager.core.database.model.FileFtsEntity
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import com.vvf.smartmanager.core.database.model.VaultItemEntity
import com.vvf.smartmanager.core.database.model.VaultJournalEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * High-performance, SQLCipher-encrypted Room Database for VVF Smart Manager.
 */
@Database(
    entities = [
        FileMetadataEntity::class,
        FileFtsEntity::class,
        VaultItemEntity::class,
        VaultJournalEntity::class,
        CloudSyncEntity::class,
        DriveIndexFileEntity::class,
        DriveSyncStateEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class VVFDatabase : RoomDatabase() {

    abstract fun fileDao(): FileDao
    abstract fun searchFtsDao(): SearchFtsDao
    abstract fun vaultDao(): VaultDao
    abstract fun vaultJournalDao(): VaultJournalDao
    abstract fun cloudSyncDao(): CloudSyncDao
    abstract fun driveIndexDao(): DriveIndexDao

    companion object {
        const val DATABASE_NAME = "vvf_smart_manager_enc.db"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `vault_journal` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `operationType` TEXT NOT NULL,
                        `originalPath` TEXT NOT NULL,
                        `vaultPath` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `createdAt` INTEGER NOT NULL,
                        `updatedAt` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_index_files` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        `driveFileId` TEXT NOT NULL,
                        `name` TEXT NOT NULL,
                        `mimeType` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `modifiedTimeMs` INTEGER NOT NULL,
                        `parentIdsCsv` TEXT NOT NULL,
                        `webViewLink` TEXT,
                        `starred` INTEGER NOT NULL,
                        `extractedText` TEXT NOT NULL,
                        `extractionSource` TEXT,
                        `contentSha256` TEXT,
                        `embeddingModel` TEXT,
                        `embeddingVersion` INTEGER,
                        `embeddingDimension` INTEGER,
                        `embeddingVector` BLOB,
                        `pinnedPath` TEXT,
                        `pinnedAtMs` INTEGER,
                        `pinnedModifiedTimeMs` INTEGER,
                        `indexStatus` TEXT NOT NULL,
                        `indexAttempts` INTEGER NOT NULL,
                        `lastIndexedAtMs` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_drive_index_files_driveFileId` ON `drive_index_files` (`driveFileId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_index_files_name` ON `drive_index_files` (`name`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_index_files_mimeType` ON `drive_index_files` (`mimeType`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_index_files_sizeBytes` ON `drive_index_files` (`sizeBytes`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_index_files_modifiedTimeMs` ON `drive_index_files` (`modifiedTimeMs`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_drive_index_files_pinnedAtMs` ON `drive_index_files` (`pinnedAtMs`)")
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `drive_sync_state` (
                        `id` INTEGER NOT NULL PRIMARY KEY,
                        `changeStartPageToken` TEXT,
                        `changePageToken` TEXT,
                        `fullListPageToken` TEXT,
                        `fullListFilesSeen` INTEGER NOT NULL,
                        `indexingCursor` TEXT,
                        `indexingInProgress` INTEGER NOT NULL,
                        `listingIncomplete` INTEGER NOT NULL,
                        `lastSyncAtMs` INTEGER,
                        `lastIndexAtMs` INTEGER,
                        `lastError` TEXT
                    )
                    """.trimIndent()
                )
            }
        }

        /** Builds an encrypted SQLCipher Room database using the decrypted Keystore passphrase. */
        fun buildEncryptedDatabase(context: Context, passphrase: ByteArray): VVFDatabase {
            val openHelperFactory = SupportOpenHelperFactory(passphrase)

            return Room.databaseBuilder(
                context.applicationContext,
                VVFDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(openHelperFactory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
        }

        /** In-memory database builder for tests. */
        fun buildInMemoryDatabase(context: Context): VVFDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                VVFDatabase::class.java
            )
                .allowMainThreadQueries()
                .build()
        }
    }
}
