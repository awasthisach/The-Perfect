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
import java.io.FileOutputStream

@RunWith(AndroidJUnit4::class)
class SqlCipherCompatibilityTest {
    private lateinit var databaseFile: File
    private val passphrase = "vvf-sqlcipher-compatibility-test".toByteArray(Charsets.UTF_8)

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        val context = ApplicationProvider.getApplicationContext<Context>()
        databaseFile = File.createTempFile("vvf-sqlcipher-compat-", ".db", context.cacheDir)
        context.assets.open("legacy-4.5.4.db").use { input ->
            FileOutputStream(databaseFile, false).use { output -> input.copyTo(output) }
        }
        assertTrue("SQLCipher 4.5.4 fixture asset is missing or empty", databaseFile.isFile && databaseFile.length() > 0)
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
    fun legacy_4_5_4Database_isReadableWithItsOriginalPassphrase() {
        SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            passphrase,
            null,
            SQLiteDatabase.OPEN_READONLY,
            null,
            null
        ).use { database ->
            database.rawQuery("SELECT value FROM compatibility_test WHERE id = 1", null).use { cursor ->
                assertTrue("Legacy compatibility row was not found", cursor.moveToFirst())
                assertEquals("legacy-4.5.4", cursor.getString(0))
            }
        }
    }

    @Test
    fun legacy_4_5_4Database_rejectsAnIncorrectPassphrase() {
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
                    cursor.moveToFirst()
                }
            }
        } catch (_: Exception) {
            rejected = true
        } finally {
            wrongPassphrase.fill(0)
        }
        assertTrue("Legacy database unexpectedly opened/read with an incorrect passphrase", rejected)
    }
}
