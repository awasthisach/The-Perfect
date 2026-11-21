package com.vvf.smartmanager.core.database

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import com.vvf.smartmanager.core.database.dao.CloudSyncDao
import com.vvf.smartmanager.core.database.dao.FileDao
import com.vvf.smartmanager.core.database.dao.SearchFtsDao
import com.vvf.smartmanager.core.database.dao.SourceFileRecordDao
import com.vvf.smartmanager.core.database.dao.VaultDao
import com.vvf.smartmanager.core.database.dao.VaultJournalDao
import com.vvf.smartmanager.core.database.model.CloudSyncEntity
import com.vvf.smartmanager.core.database.model.FileFtsEntity
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import com.vvf.smartmanager.core.database.model.SourceFileRecordEntity
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
        SourceFileRecordEntity::class
    ],
    version = 5,
    exportSchema = true
)
abstract class VVFDatabase : RoomDatabase() {

    abstract fun fileDao(): FileDao
    abstract fun searchFtsDao(): SearchFtsDao
    abstract fun vaultDao(): VaultDao
    abstract fun vaultJournalDao(): VaultJournalDao
    abstract fun cloudSyncDao(): CloudSyncDao
    abstract fun sourceFileRecordDao(): SourceFileRecordDao

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

        /**
         * Move worker retry bookkeeping out of user tags. Existing exact internal marker tokens
         * are migrated; similarly named user tags are left untouched.
         */
        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "ALTER TABLE file_metadata ADD COLUMN ocrRetryAfterEpochMillis INTEGER"
                )
                db.execSQL(
                    "ALTER TABLE file_metadata ADD COLUMN ocrRetryAttempt INTEGER NOT NULL DEFAULT 0"
                )

                db.query(
                    "SELECT id, tags FROM file_metadata " +
                        "WHERE instr(',' || replace(lower(tags), ' ', '') || ',', ',ocr_retry_after:') > 0"
                ).use { cursor ->
                    val update = db.compileStatement(
                        "UPDATE file_metadata SET tags = ?, ocrRetryAfterEpochMillis = ?, " +
                            "ocrRetryAttempt = ? WHERE id = ?"
                    )
                    while (cursor.moveToNext()) {
                        val parsed = parseLegacyOcrRetryMetadata(cursor.getString(1).orEmpty())
                        if (parsed.hadInternalMarker) {
                            update.clearBindings()
                            update.bindString(1, parsed.visibleTags)
                            if (parsed.retryAfterEpochMillis == null) {
                                update.bindNull(2)
                            } else {
                                update.bindLong(2, parsed.retryAfterEpochMillis)
                            }
                            update.bindLong(3, parsed.retryAttempt.toLong())
                            update.bindLong(4, cursor.getLong(0))
                            update.executeUpdateDelete()
                        }
                    }
                }
            }
        }

        /**
         * Adds a source-neutral catalog without rewriting legacy path-keyed file_metadata rows.
         * Existing indexed files remain intact; source adapters can populate this table gradually.
         */
        val MIGRATION_4_5: Migration = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE file_metadata ADD COLUMN ocrText TEXT NOT NULL DEFAULT ''")
                db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_file_fts_BEFORE_UPDATE")
                db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_file_fts_BEFORE_DELETE")
                db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_file_fts_AFTER_UPDATE")
                db.execSQL("DROP TRIGGER IF EXISTS room_fts_content_sync_file_fts_AFTER_INSERT")
                db.execSQL("DROP TABLE IF EXISTS file_fts")
                db.execSQL("CREATE VIRTUAL TABLE file_fts USING FTS4(name TEXT NOT NULL, path TEXT NOT NULL, tags TEXT NOT NULL, mimeType TEXT NOT NULL, ocrText TEXT NOT NULL, content=`file_metadata`)")
                db.execSQL("CREATE TRIGGER room_fts_content_sync_file_fts_BEFORE_UPDATE BEFORE UPDATE ON file_metadata BEGIN DELETE FROM file_fts WHERE docid=OLD.rowid; END")
                db.execSQL("CREATE TRIGGER room_fts_content_sync_file_fts_BEFORE_DELETE BEFORE DELETE ON file_metadata BEGIN DELETE FROM file_fts WHERE docid=OLD.rowid; END")
                db.execSQL("CREATE TRIGGER room_fts_content_sync_file_fts_AFTER_UPDATE AFTER UPDATE ON file_metadata BEGIN INSERT INTO file_fts(docid,name,path,tags,mimeType,ocrText) VALUES(NEW.rowid,NEW.name,NEW.path,NEW.tags,NEW.mimeType,NEW.ocrText); END")
                db.execSQL("CREATE TRIGGER room_fts_content_sync_file_fts_AFTER_INSERT AFTER INSERT ON file_metadata BEGIN INSERT INTO file_fts(docid,name,path,tags,mimeType,ocrText) VALUES(NEW.rowid,NEW.name,NEW.path,NEW.tags,NEW.mimeType,NEW.ocrText); END")
                db.execSQL("INSERT INTO file_fts(file_fts) VALUES('rebuild')")
            }
        }

        val MIGRATION_3_4: Migration = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `source_file_records` (
                        `identityKey` TEXT NOT NULL,
                        `sourceType` TEXT NOT NULL,
                        `sourceInstanceId` TEXT NOT NULL,
                        `sourceFileId` TEXT NOT NULL,
                        `displayName` TEXT NOT NULL,
                        `sizeBytes` INTEGER NOT NULL,
                        `mimeType` TEXT,
                        `modifiedAtEpochMillis` INTEGER NOT NULL,
                        `accessState` TEXT NOT NULL,
                        `sha256` TEXT,
                        PRIMARY KEY(`identityKey`)
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_source_file_records_sourceType_sourceInstanceId` " +
                        "ON `source_file_records` (`sourceType`, `sourceInstanceId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_source_file_records_accessState` " +
                        "ON `source_file_records` (`accessState`)"
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS `index_source_file_records_sourceType_sourceInstanceId_sourceFileId` " +
                        "ON `source_file_records` (`sourceType`, `sourceInstanceId`, `sourceFileId`)"
                )
            }
        }

        /** Builds an encrypted SQLCipher Room database using the decrypted Keystore passphrase. */
        fun buildEncryptedDatabase(context: Context, passphrase: ByteArray): VVFDatabase {
            require(passphrase.size == 32) { "SQLCipher database passphrase must be exactly 32 bytes" }
            // SupportOpenHelperFactory retains the supplied array and opens lazily. Keep an owned
            // copy so callers can wipe their input immediately after building Room without
            // corrupting the key before the first database operation.
            val openHelperFactory = WipingPassphraseOpenHelperFactory(passphrase)

            return Room.databaseBuilder(
                context.applicationContext,
                VVFDatabase::class.java,
                DATABASE_NAME
            )
                .openHelperFactory(openHelperFactory)
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5)
                .build()
        }

        private class WipingPassphraseOpenHelperFactory(passphrase: ByteArray) :
            SupportSQLiteOpenHelper.Factory {
            private val ownedPassphrase = passphrase.copyOf()
            private val delegate = SupportOpenHelperFactory(ownedPassphrase)

            override fun create(
                configuration: SupportSQLiteOpenHelper.Configuration
            ): SupportSQLiteOpenHelper {
                val helper = delegate.create(configuration)
                return object : SupportSQLiteOpenHelper by helper {
                    override fun close() {
                        try {
                            helper.close()
                        } finally {
                            ownedPassphrase.fill(0)
                        }
                    }
                }
            }
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
