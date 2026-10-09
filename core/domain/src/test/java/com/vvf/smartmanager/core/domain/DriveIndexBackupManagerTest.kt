package com.vvf.smartmanager.core.domain

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vvf.smartmanager.core.database.VVFDatabase
import com.vvf.smartmanager.core.database.model.DriveIndexFileEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DriveIndexBackupManagerTest {
    private lateinit var context: Context
    private lateinit var database: VVFDatabase
    private lateinit var targetDatabase: VVFDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = VVFDatabase.buildInMemoryDatabase(context)
        targetDatabase = VVFDatabase.buildInMemoryDatabase(context)
    }

    @After
    fun tearDown() {
        database.close()
        targetDatabase.close()
    }

    @Test
    fun exportExcludesCredentialsVectorsAndPinnedPathsAndCanImport() = runBlocking {
        val dao = database.driveIndexDao()
        dao.upsertFile(
            DriveIndexFileEntity(
                driveFileId = "1AbCdEfGhIjKlMnOpQrStUvWxYz_12345",
                name = "meeting-notes.txt",
                mimeType = "text/plain",
                sizeBytes = 32L,
                modifiedTimeMs = 100L,
                extractedText = "agenda decisions action items",
                extractionSource = "NATIVE_TEXT",
                contentSha256 = "a".repeat(64),
                embeddingVector = byteArrayOf(1, 2, 3),
                embeddingModel = "test-model",
                embeddingVersion = 1,
                embeddingDimension = 3,
                pinnedPath = "/private/pinned/secret.bin",
                pinnedAtMs = 100L,
                pinnedModifiedTimeMs = 100L,
                indexStatus = "TEXT_INDEXED"
            )
        )
        val exporter = DriveIndexBackupManager(dao, { "user@example.com" }, { false })
        val output = ByteArrayOutputStream()
        val summary = exporter.exportTo(output).getOrThrow()
        val json = output.toString(Charsets.UTF_8.name())

        assertEquals(1, summary.fileCount)
        assertTrue(json.contains("meeting-notes.txt"))
        assertTrue(json.contains("agenda decisions action items"))
        assertFalse(json.contains("embeddingVector"))
        assertFalse(json.contains("test-model"))
        assertFalse(json.contains("pinnedPath"))
        assertFalse(json.contains("accessToken"))
        assertFalse(json.contains("idToken"))
        assertFalse(json.contains("apiKey"))

        val importer = DriveIndexBackupManager(targetDatabase.driveIndexDao(), { "user@example.com" }, { false })
        importer.importFrom(ByteArrayInputStream(output.toByteArray())).getOrThrow()
        val restored = targetDatabase.driveIndexDao().getByDriveId("1AbCdEfGhIjKlMnOpQrStUvWxYz_12345")
        assertEquals("agenda decisions action items", restored?.extractedText)
        assertNull(restored?.embeddingVector)
        assertNull(restored?.pinnedPath)
    }

    @Test
    fun importRejectsBackupForAnotherGoogleAccount() = runBlocking {
        val exporter = DriveIndexBackupManager(database.driveIndexDao(), { "first@example.com" }, { false })
        val output = ByteArrayOutputStream()
        exporter.exportTo(output).getOrThrow()

        val importer = DriveIndexBackupManager(targetDatabase.driveIndexDao(), { "second@example.com" }, { false })
        assertTrue(importer.importFrom(ByteArrayInputStream(output.toByteArray())).isFailure)
    }
}
