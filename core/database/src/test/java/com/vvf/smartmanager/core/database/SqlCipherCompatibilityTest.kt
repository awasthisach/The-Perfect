package com.vvf.smartmanager.core.database

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import net.sqlcipher.database.SQLiteDatabase as LegacySQLiteDatabase
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import net.zetetic.database.sqlcipher.SQLiteDatabase as CurrentSQLiteDatabase

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SqlCipherCompatibilityTest {

    private lateinit var databaseFile: File
    private val passphrase = "vvf-sqlcipher-compatibility-test".toByteArray(Charsets.UTF_8)

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        databaseFile = File.createTempFile("vvf-sqlcipher-compat-", ".db", context.cacheDir)
        databaseFile.delete()

        LegacySQLiteDatabase.loadLibs(context)
        val legacyDatabase = LegacySQLiteDatabase.openOrCreateDatabase(
            databaseFile.absolutePath,
            passphrase,
            null,
            LegacySQLiteDatabase.CREATE_IF_NECESSARY,
            null,
            null
        )
        try {
            legacyDatabase.execSQL("CREATE TABLE compatibility_test (id INTEGER PRIMARY KEY, value TEXT NOT NULL)")
            legacyDatabase.execSQL("INSERT INTO compatibility_test (id, value) VALUES (1, 'legacy-4.5.4')")
        } finally {
            legacyDatabase.close()
        }

        assertTrue("Legacy SQLCipher database file was not created", databaseFile.exists())
    }

    @After
    fun tearDown() {
        if (::databaseFile.isInitialized) {
            CurrentSQLiteDatabase.deleteDatabase(databaseFile)
            databaseFile.delete()
        }
        passphrase.fill(0)
    }

    @Test
    fun sqlCipher_4_5_4_database_is_readable_by_4_5_6() {
        System.loadLibrary("sqlcipher")

        val migratedDatabase = CurrentSQLiteDatabase.openDatabase(
            databaseFile.absolutePath,
            passphrase,
            null,
            CurrentSQLiteDatabase.OPEN_READONLY,
            null,
            null
        )
        try {
            val cursor = migratedDatabase.rawQuery(
                "SELECT value FROM compatibility_test WHERE id = 1",
                null
            )
            cursor.use {
                assertTrue("Compatibility row was not found", it.moveToFirst())
                assertEquals("legacy-4.5.4", it.getString(0))
            }
        } finally {
            migratedDatabase.close()
        }
    }
}
