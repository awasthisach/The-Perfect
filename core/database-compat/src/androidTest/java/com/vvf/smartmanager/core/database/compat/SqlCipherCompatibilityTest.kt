package com.vvf.smartmanager.core.database.compat

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import net.zetetic.database.sqlcipher.SQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
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
        val context = ApplicationProvider.getApplicationContext<Context>()
        databaseFile = File.createTempFile("vvf-sqlcipher-compat-", ".db", context.cacheDir)
        databaseFile.delete()
        context.assets.open("legacy-4.5.4.db").use { input ->
            databaseFile.outputStream().use { output -> input.copyTo(output) }
        }
        assertTrue("Legacy SQLCipher 4.5.4 fixture was not copied", databaseFile.exists())
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
    fun sqlCipher_4_5_4_database_is_readable_by_current_sqlcipher_android() {
        System.loadLibrary("sqlcipher")
        val database = SQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            passphrase,
            null,
            SQLiteDatabase.OPEN_READONLY,
            null,
            null
        )
        try {
            database.rawQuery("SELECT value FROM compatibility_test WHERE id = 1", null).use {
                assertTrue("Compatibility row was not found", it.moveToFirst())
                assertEquals("legacy-4.5.4", it.getString(0))
            }
        } finally {
            database.close()
        }
    }

    @Test
    fun wrongPassphraseFailsClosed() {
        System.loadLibrary("sqlcipher")
        val wrong = "definitely-not-the-fixture-passphrase".toByteArray(Charsets.UTF_8)
        try {
            try {
                SQLiteDatabase.openDatabase(
                    databaseFile.absolutePath,
                    wrong,
                    null,
                    SQLiteDatabase.OPEN_READONLY,
                    null,
                    null
                ).close()
                fail("Expected SQLCipher to reject the wrong passphrase")
            } catch (expected: Exception) {
                assertTrue(
                    "Unexpected exception type: ${expected.javaClass.name}",
                    expected.message?.contains("file is not a database", ignoreCase = true) == true ||
                        expected.message?.contains("file is encrypted", ignoreCase = true) == true ||
                        expected is net.zetetic.database.sqlcipher.SQLiteException ||
                        expected.cause is net.zetetic.database.sqlcipher.SQLiteException
                )
            }
        } finally {
            wrong.fill(0)
        }
    }
}
