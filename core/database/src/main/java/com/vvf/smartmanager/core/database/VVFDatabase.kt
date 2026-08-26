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
import net.sqlcipher.database.SupportFactory

/**
 * High-performance, SQLCipher-encrypted Room Database for VVF Smart Manager.
 *
 * Stores:
 * 1. File Metadata & Duplicate Hashes
 * 2. Full-Text Search (FTS4) Index
 * 3. Secure Vault Records & Operation Journal
 * 4. Multi-Cloud Sync Tracker
 */
@Database(
    entities = [
        FileMetadataEntity::class,
        FileFtsEntity::class,
        VaultItemEntity::class,
        VaultJournalEntity::class,
        CloudSyncEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class VVFDatabase : RoomDatabase() {

    abstract fun fileDao(): FileDao
    abstract fun searchFtsDao(): SearchFtsDao
    abstract fun vaultDao(): VaultDao
    abstract fun vaultJournalDao(): VaultJournalDao
    abstract fun cloudSyncDao(): CloudSyncDao

    companion object {
        const val DATABASE_NAME = "vvf_smart_manager_enc.db"

        /**
         * Migration skeleton for future database schema upgrades.
         */
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

        /**
         * Builds an encrypted SQLCipher Room database using the decrypted Keystore passphrase.
         */
        fun buildEncryptedDatabase(context: Context, passphrase: ByteArray): VVFDatabase {
            val openHelperFactory = SupportFactory(passphrase)

            return Room.databaseBuilder(
                context.applicationContext,
                VVFDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(openHelperFactory)
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
        }

        /**
         * In-memory database builder for testing.
         */
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
