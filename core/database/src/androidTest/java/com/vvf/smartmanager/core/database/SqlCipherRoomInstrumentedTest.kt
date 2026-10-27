package com.vvf.smartmanager.core.database

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import kotlinx.coroutines.runBlocking
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SqlCipherRoomInstrumentedTest {
    private lateinit var context: Context
    private lateinit var databaseFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        System.loadLibrary("sqlcipher")
        databaseFile = context.getDatabasePath(VVFDatabase.DATABASE_NAME)
        deleteDatabaseArtifacts()
    }

    @After
    fun tearDown() {
        deleteDatabaseArtifacts()
    }

    @Test
    fun roomOpensAfterCallerWipesInputKeyAndPersistsEncryptedRows() = runBlocking {
        val callerPassphrase = ByteArray(32) { (it + 1).toByte() }
        val reopenPassphrase = callerPassphrase.copyOf()
        val path = "/phase4/sqlcipher-room-persistence.txt"
        val database = VVFDatabase.buildEncryptedDatabase(context, callerPassphrase)
        // Mirrors VVFApplication: the caller wipes its transient key buffer immediately after
        // constructing Room. The helper must own a separate copy until the database is closed.
        callerPassphrase.fill(0)

        try {
            database.openHelper.writableDatabase.query("PRAGMA cipher_version").use { cursor ->
                assertTrue("SQLCipher native core did not report a version", cursor.moveToFirst())
                assertFalse("SQLCipher version must not be blank", cursor.getString(0).isNullOrBlank())
            }
            database.fileDao().insertOrUpdate(
                FileMetadataEntity(
                    path = path,
                    name = "sqlcipher-room-persistence.txt",
                    parentPath = "/phase4",
                    sizeBytes = 42L,
                    mimeType = "text/plain",
                    isDirectory = false,
                    modifiedDate = 1L
                )
            )
        } finally {
            database.close()
        }

        try {
            assertTrue("Encrypted database file was not created", databaseFile.isFile)
            val header = databaseFile.inputStream().use { input -> ByteArray(16).also { input.read(it) } }
            assertFalse(
                "Database file must not expose the plaintext SQLite header",
                header.contentEquals("SQLite format 3\u0000".toByteArray(Charsets.US_ASCII))
            )

            val reopened = VVFDatabase.buildEncryptedDatabase(context, reopenPassphrase)
            try {
                val row = reopened.fileDao().getByPath(path)
                assertEquals("sqlcipher-room-persistence.txt", row?.name)
                assertEquals(42L, row?.sizeBytes)
            } finally {
                reopened.close()
            }
        } finally {
            reopenPassphrase.fill(0)
        }
    }

    @Test
    fun wrongPassphraseCannotReadEncryptedRoomDatabase() {
        val correctPassphrase = ByteArray(32) { (it + 17).toByte() }
        val database = VVFDatabase.buildEncryptedDatabase(context, correctPassphrase)
        try {
            database.openHelper.writableDatabase.query("PRAGMA cipher_version").use {
                assertTrue(it.moveToFirst())
            }
        } finally {
            database.close()
            correctPassphrase.fill(0)
        }

        val wrongPassphrase = ByteArray(32) { (it + 91).toByte() }
        val wrongDatabase = VVFDatabase.buildEncryptedDatabase(context, wrongPassphrase)
        wrongPassphrase.fill(0)
        var rejected = false
        try {
            wrongDatabase.openHelper.writableDatabase.query("SELECT count(*) FROM sqlite_master").use {
                it.moveToFirst()
            }
        } catch (_: Exception) {
            rejected = true
        } finally {
            wrongDatabase.close()
        }
        assertTrue("Wrong passphrase must fail closed instead of opening the encrypted DB", rejected)
    }

    @Test
    fun encryptedRoomMigrationFromV1PreservesRowsAndAddsVaultJournal() = runBlocking {
        val passphrase = ByteArray(32) { (it + 31).toByte() }
        val legacyPassphrase = passphrase.copyOf()
        val path = "/phase4/legacy-room-row.txt"
        val legacy = Room.databaseBuilder(
            context,
            LegacyV1Database::class.java,
            VVFDatabase.DATABASE_NAME
        )
            .openHelperFactory(SupportOpenHelperFactory(legacyPassphrase))
            .build()

        try {
            legacy.openHelper.writableDatabase.query("PRAGMA cipher_version").use {
                assertTrue("Legacy DB must be encrypted with SQLCipher", it.moveToFirst())
            }
            // The legacy helper is now keyed and open; it no longer needs the transient key bytes.
            legacyPassphrase.fill(0)
            legacy.fileDao().insertOrUpdate(
                FileMetadataEntity(
                    path = path,
                    name = "legacy-room-row.txt",
                    parentPath = "/phase4",
                    sizeBytes = 73L,
                    mimeType = "text/plain",
                    isDirectory = false,
                    modifiedDate = 2L
                )
            )
        } finally {
            legacy.close()
        }

        val migrated = VVFDatabase.buildEncryptedDatabase(context, passphrase)
        passphrase.fill(0)
        try {
            val row = migrated.fileDao().getByPath(path)
            assertEquals("legacy-room-row.txt", row?.name)
            assertEquals(73L, row?.sizeBytes)
            migrated.openHelper.writableDatabase.query(
                "SELECT name FROM sqlite_master WHERE type = 'table' AND name = 'vault_journal'"
            ).use { cursor ->
                assertTrue("Migration 1->2 must create vault_journal", cursor.moveToFirst())
            }
        } finally {
            migrated.close()
        }
    }

    private fun deleteDatabaseArtifacts() {
        SQLiteDatabase.deleteDatabase(databaseFile)
        File(databaseFile.path + "-wal").delete()
        File(databaseFile.path + "-shm").delete()
        databaseFile.delete()
    }
}
