package com.vvf.smartmanager.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.vvf.smartmanager.core.database.dao.CloudSyncDao
import com.vvf.smartmanager.core.database.dao.FileDao
import com.vvf.smartmanager.core.database.dao.SearchFtsDao
import com.vvf.smartmanager.core.database.dao.VaultDao
import com.vvf.smartmanager.core.database.dao.VaultJournalDao
import com.vvf.smartmanager.core.database.model.CloudSyncEntity
import com.vvf.smartmanager.core.database.model.FileFtsEntity
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import com.vvf.smartmanager.core.database.model.VaultItemEntity
import com.vvf.smartmanager.core.database.model.VaultJournalEntity
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

/**
 * High-performance, SQLCipher-encrypted Room Database.
 */
@Database(
    entities = [
        FileMetadataEntity::class,
        FileFtsEntity::class,
        VaultItemEntity::class,
        VaultJournalEntity::class,
        CloudSyncEntity::class
    ],
    // 1→2: vault journal; 2→3: extracted text/canonical URI; 3→4: offline pin metadata.
    version = 4,
    exportSchema = true
)
abstract class VVFDatabase : RoomDatabase() {

    abstract fun fileDao(): FileDao
    abstract fun searchFtsDao(): SearchFtsDao
    abstract fun vaultDao(): VaultDao
    abstract fun vaultJournalDao(): VaultJournalDao
    abstract fun cloudSyncDao(): CloudSyncDao

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

        /** Adds extracted text without discarding the user's existing local index. */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `file_metadata` ADD COLUMN `contentText` TEXT NOT NULL DEFAULT ''")
                db.execSQL("ALTER TABLE `file_metadata` ADD COLUMN `canonicalUri` TEXT")
                db.execSQL("DROP TABLE IF EXISTS `file_fts`")
                db.execSQL(
                    "CREATE VIRTUAL TABLE IF NOT EXISTS `file_fts` USING FTS4(" +
                        "`name`, `path`, `tags`, `mimeType`, `contentText`, content='file_metadata')"
                )
                db.execSQL("INSERT INTO `file_fts`(`file_fts`) VALUES('rebuild')")
            }
        }

        /** Adds offline pin metadata without discarding the user's existing index. */
        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `file_metadata` ADD COLUMN `offlinePinned` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `file_metadata` ADD COLUMN `offlineLocalPath` TEXT")
                db.execSQL("ALTER TABLE `file_metadata` ADD COLUMN `offlinePinnedAt` INTEGER")
                db.execSQL("ALTER TABLE `file_metadata` ADD COLUMN `offlineBytes` INTEGER NOT NULL DEFAULT 0")
            }
        }

        /** Builds an encrypted SQLCipher Room database using the Keystore passphrase. */
        fun buildEncryptedDatabase(context: Context, passphrase: ByteArray): VVFDatabase {
            val openHelperFactory = SupportOpenHelperFactory(passphrase)
            return Room.databaseBuilder(
                context.applicationContext,
                VVFDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(openHelperFactory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4)
                .build()
        }

        /** Builds an in-memory database for tests. */
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
