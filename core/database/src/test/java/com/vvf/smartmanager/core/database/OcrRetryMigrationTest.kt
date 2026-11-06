package com.vvf.smartmanager.core.database

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OcrRetryMigrationTest {
    private lateinit var helper: SupportSQLiteOpenHelper

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val configuration = SupportSQLiteOpenHelper.Configuration.builder(context)
            .name("ocr-retry-migration-test.db")
            .callback(object : SupportSQLiteOpenHelper.Callback(2) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    db.execSQL(
                        "CREATE TABLE file_metadata (id INTEGER PRIMARY KEY NOT NULL, tags TEXT NOT NULL)"
                    )
                    db.execSQL(
                        "INSERT INTO file_metadata(id, tags) VALUES " +
                            "(1, 'invoice,ocr_retry_after:1234:3,finance'), " +
                            "(2, 'myocr_retry_after:9999,invoice'), " +
                            "(3, 'ocr_retry_after:invalid:4,report'), (4, 'ocr_retry_after:9999,report')"
                    )
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
            })
            .build()
        helper = FrameworkSQLiteOpenHelperFactory().create(configuration)
    }

    @After
    fun tearDown() {
        helper.close()
    }

    @Test
    fun migrationMovesOnlyExactInternalMarkersIntoDedicatedColumns() {
        val db = helper.writableDatabase
        VVFDatabase.MIGRATION_2_3.migrate(db)

        db.query(
            "SELECT id, tags, ocrRetryAfterEpochMillis, ocrRetryAttempt FROM file_metadata ORDER BY id"
        ).use { cursor ->
            cursor.moveToFirst()
            assertEquals(1L, cursor.getLong(0))
            assertEquals("invoice,finance", cursor.getString(1))
            assertEquals(1234L, cursor.getLong(2))
            assertEquals(3, cursor.getInt(3))

            cursor.moveToNext()
            assertEquals(2L, cursor.getLong(0))
            assertEquals("myocr_retry_after:9999,invoice", cursor.getString(1))
            assertTrue(cursor.isNull(2))
            assertEquals(0, cursor.getInt(3))

            cursor.moveToNext()
            assertEquals(3L, cursor.getLong(0))
            assertEquals("ocr_retry_after:invalid:4,report", cursor.getString(1))
            assertTrue(cursor.isNull(2))
            assertEquals(0, cursor.getInt(3))

            cursor.moveToNext()
            assertEquals(4L, cursor.getLong(0))
            assertEquals("ocr_retry_after:9999,report", cursor.getString(1))
            assertTrue(cursor.isNull(2))
            assertEquals(0, cursor.getInt(3))
        }
    }
}
