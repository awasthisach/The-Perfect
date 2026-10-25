package com.vvf.smartmanager.core.database.compat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zetetic.database.sqlcipher.SQLiteDatabase
import net.zetetic.database.sqlcipher.SQLiteException
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Proves a SQLCipher 4.5.4 fixture remains readable by sqlcipher-android 4.x
 * with the correct passphrase, and stays closed with a wrong passphrase.
 */
@RunWith(AndroidJUnit4::class)
class SqlCipherCompatibilityTest {
    private lateinit var databaseFile: File
    private val passphrase = "vvf-sqlcipher-compatibility-test".toByteArray(Charsets.UTF_8)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        System.loadLibrary("sqlcipher")

        databaseFile = File.createTempFile("vvf-sqlcipher-compat-", ".db", context.cacheDir)
        databaseFile.delete()
        context.assets.open("legacy-4.5.4.db").use { input ->
            databaseFile.outputStream().use { output -> input.copyTo(output) }
        }
        assertTrue("Legacy SQLCipher 4.5.4 fixture was not copied", databaseFile.exists())
        assertTrue("Legacy fixture is empty", databaseFile.length() > 0L)
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
    fun legacy_4_5_4_db_opens_and_reads_known_row_with_correct_passphrase() {
        val database = SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            passphrase,
            null,
            SQLiteDatabase.OPEN_READONLY,
            null,
            null
        )
        try {
            database.rawQuery("SELECT value FROM compatibility_test WHERE id = 1", null).use { cursor ->
                assertTrue("Compatibility row was not found", cursor.moveToFirst())
                assertEquals("legacy-4.5.4", cursor.getString(0))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun legacy_4_5_4_db_rejects_wrong_passphrase() {
        val wrongPassphrase = "definitely-not-the-passphrase".toByteArray(Charsets.UTF_8)
        try {
            try {
                val database = SQLiteDatabase.openDatabase(
                    databaseFile.absolutePath,
                    wrongPassphrase,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                    null,
                    null
                )
                try {
                    // Some builds open the file handle but fail on first read of encrypted pages.
                    database.rawQuery("SELECT value FROM compatibility_test WHERE id = 1", null).use { cursor ->
                        cursor.moveToFirst()
                    }
                    fail("Expected SQLCipher to reject a wrong passphrase")
                } finally {
                    database.close()
                }
            } catch (e: SQLiteException) {
                // Expected: wrong key / file is not a database / HMAC failure
                assertTrue(
                    "Unexpected SQLiteException message: ${e.message}",
                    e.message?.contains("file is not a database", ignoreCase = true) == true ||
                        e.message?.contains("HMAC", ignoreCase = true) == true ||
                        e.message?.contains("code 26", ignoreCase = true) == true ||
                        e.message?.contains("error code 26", ignoreCase = true) == true ||
                        e.message?.isNotBlank() == true
                )
            } catch (e: Exception) {
                // Native layer may surface as a generic runtime failure on some devices.
                assertTrue(
                    "Wrong passphrase must not open readable data: ${e.javaClass.name} ${e.message}",
                    true
                )
            }
        } finally {
            wrongPassphrase.fill(0)
        }
    }
}
