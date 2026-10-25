package com.vvf.smartmanager.core.database.compat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class SqlCipherCompatibilityTest {
    private lateinit var databaseFile: File
    private val passphrase = "vvf-sqlcipher-compatibility-test".toByteArray(Charsets.UTF_8)

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        val context = ApplicationProvider.getApplicationContext<Context>()
        databaseFile = File.createTempFile("vvf-sqlcipher-compat-", ".db", context.cacheDir)
        databaseFile.delete()

        // Build a real encrypted database once, then exercise reopen/key validation.
        // This is not a substitute for the missing SQLCipher 4.5.4 binary fixture.
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            passphrase,
            null,
            SQLiteDatabase.OPEN_READWRITE or SQLiteDatabase.CREATE_IF_NECESSARY,
            null,
            null
        ).use { database ->
            database.execSQL("CREATE TABLE compatibility_test (id INTEGER PRIMARY KEY, value TEXT NOT NULL)")
            database.execSQL("INSERT INTO compatibility_test (id, value) VALUES (1, 'correct-passphrase')")
        }
        assertTrue("Encrypted test database was not created", databaseFile.isFile && databaseFile.length() > 0)
    }

    @After
    fun tearDown() {
        if (::databaseFile.isInitialized) {
            SQLiteDatabase.deleteDatabase(databaseFile)
            databaseFile.delete()
        }
        passphrase.fill(0)
    }

    @Test
    fun encryptedDatabase_isReadableWithTheOriginalPassphrase() {
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            passphrase,
            null,
            SQLiteDatabase.OPEN_READONLY,
            null,
            null
        ).use { database ->
            database.rawQuery("SELECT value FROM compatibility_test WHERE id = 1", null).use { cursor ->
                assertTrue("Compatibility row was not found", cursor.moveToFirst())
                assertEquals("correct-passphrase", cursor.getString(0))
            }
        }
    }

    @Test
    fun encryptedDatabase_rejectsAnIncorrectPassphrase() {
        val wrongPassphrase = "definitely-the-wrong-passphrase".toByteArray(Charsets.UTF_8)
        var rejected = false
        try {
            SQLiteDatabase.openDatabase(
                databaseFile.absolutePath,
                wrongPassphrase,
                null,
                SQLiteDatabase.OPEN_READONLY,
                null,
                null
            ).use { database ->
                database.rawQuery("SELECT value FROM compatibility_test WHERE id = 1", null).use { cursor ->
                    // Some SQLCipher versions report the key failure on first query rather than open.
                    cursor.moveToFirst()
                }
            }
        } catch (_: Exception) {
            rejected = true
        } finally {
            wrongPassphrase.fill(0)
        }
        assertTrue("Database unexpectedly opened/read with an incorrect passphrase", rejected)
    }
}
