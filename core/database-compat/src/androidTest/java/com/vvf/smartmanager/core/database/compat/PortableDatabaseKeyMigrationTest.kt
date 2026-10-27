package com.vvf.smartmanager.core.database.compat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class PortableDatabaseKeyMigrationTest {
    @Test
    fun importedDatabasePassphraseIsRewrappedAndOpensSqlCipherDatabase() {
        System.loadLibrary("sqlcipher")
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = CryptoSecurityManager(context)
        val keyState = File(context.cacheDir, "portable-key-state-${System.nanoTime()}")
        val databaseFile = File(context.cacheDir, "portable-key-migration-${System.nanoTime()}.db")
        val originalPassphrase = crypto.getOrCreateDatabasePassphrase()
        val destinationPassphrase = ByteArray(32) { (it + 91).toByte() }
        crypto.snapshotDatabasePassphraseState(keyState)

        try {
            val database = SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                originalPassphrase,
                null,
                SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY,
                null,
                null
            )
            database.use {
                it.execSQL("CREATE TABLE migration_test (value TEXT NOT NULL)")
                it.execSQL("INSERT INTO migration_test(value) VALUES ('portable-key-ok')")
            }

            // Simulate a destination device's prior local key, then import the archive key.
            crypto.replaceDatabasePassphraseForRestore(destinationPassphrase)
            val wrongKeyRead = runCatching {
                val wrongKeyDatabase = SQLiteDatabase.openDatabase(
                    databaseFile.absolutePath,
                    crypto.getOrCreateDatabasePassphrase(),
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                    null,
                    null
                )
                wrongKeyDatabase.use {
                    it.rawQuery("SELECT value FROM migration_test", null).use { cursor ->
                        cursor.moveToFirst()
                    }
                }
            }.getOrDefault(false)
            assertFalse("Destination's previous key must not open the imported database", wrongKeyRead)

            crypto.replaceDatabasePassphraseForRestore(originalPassphrase)
            val restoredPassphrase = crypto.getOrCreateDatabasePassphrase()
            try {
                assertArrayEquals("Imported key must be wrapped under local Keystore", originalPassphrase, restoredPassphrase)
                val restoredDatabase = SQLiteDatabase.openDatabase(
                    databaseFile.absolutePath,
                    restoredPassphrase,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                    null,
                    null
                )
                restoredDatabase.use {
                    it.rawQuery("SELECT value FROM migration_test", null).use { cursor ->
                        assertTrue("Imported database must open with the rewrapped key", cursor.moveToFirst())
                        assertEquals("portable-key-ok", cursor.getString(0))
                    }
                }
            } finally {
                restoredPassphrase.fill(0)
            }
        } finally {
            crypto.restoreDatabasePassphraseState(keyState)
            originalPassphrase.fill(0)
            destinationPassphrase.fill(0)
            keyState.deleteRecursively()
            SQLiteDatabase.deleteDatabase(databaseFile)
            databaseFile.delete()
        }
    }
}
