package com.vvf.smartmanager.core.domain

import android.content.Context
import com.vvf.smartmanager.core.database.VVFDatabase
import com.vvf.smartmanager.core.database.model.DriveIndexFileEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DriveIndexBackupManagerTest {
    private lateinit var sourceDb: VVFDatabase
    private lateinit var targetDb: VVFDatabase

    @Before
    fun setUp() {
        val context = RuntimeEnvironment.getApplication() as Context
        sourceDb = VVFDatabase.buildInMemoryDatabase(context)
        targetDb = VVFDatabase.buildInMemoryDatabase(context)
    }

    @After
    fun tearDown() {
        sourceDb.close()
        targetDb.close()
    }

    @Test
    fun exportImportRoundTripExcludesCredentialsAndPrivatePinPaths() = runBlocking {
        sourceDb.driveIndexDao().upsertFile(
            DriveIndexFileEntity(
                driveFileId = "Abcdefghijklmnopqrstuv12345",
                name = "minutes.txt",
                mimeType = "text/plain",
                sizeBytes = 20L,
                modifiedTimeMs = 100L,
                extractedText = "board meeting",
                extractionSource = "NATIVE_TEXT",
                contentSha256 = "a".repeat(64),
                pinnedPath = "/private/app/files/pinned/secret.txt",
                pinnedAtMs = 10L,
                indexStatus = "TEXT_INDEXED",
                lastIndexedAtMs = 101L
            )
        )

        val output = ByteArrayOutputStream()
        assertEquals(1, DriveIndexBackupManager(sourceDb).exportTo(output).getOrThrow())
        val json = output.toString(Charsets.UTF_8.name())
        assertFalse(json.contains("pinnedPath"))
        assertFalse(json.contains("/private/app/files"))
        assertFalse(json.contains("accessToken"))
        assertFalse(json.contains("firebaseIdToken"))
        assertFalse(json.contains("apiKey"))

        assertEquals(1, DriveIndexBackupManager(targetDb)
            .importFrom(ByteArrayInputStream(output.toByteArray())).getOrThrow())
        val restored = targetDb.driveIndexDao().getByDriveId("Abcdefghijklmnopqrstuv12345")!!
        assertEquals("board meeting", restored.extractedText)
        assertEquals("TEXT_INDEXED", restored.indexStatus)
        assertEquals(null, restored.pinnedPath)
        assertEquals(null, targetDb.driveIndexDao().getSyncState()?.changeStartPageToken)
    }

    @Test
    fun malformedImportFailsWithoutDeletingExistingIndex() = runBlocking {
        targetDb.driveIndexDao().upsertFile(
            DriveIndexFileEntity(
                driveFileId = "Zbcdefghijklmnopqrstuv12345",
                name = "keep.txt",
                mimeType = "text/plain",
                sizeBytes = 4L,
                modifiedTimeMs = 1L
            )
        )
        val result = DriveIndexBackupManager(targetDb).importFrom(
            ByteArrayInputStream("""{"schemaVersion":999,"createdAtMs":0,"files":[]}""".toByteArray())
        )
        assertTrue(result.isFailure)
        assertEquals(1, targetDb.driveIndexDao().getAllForBackup().size)
        assertEquals("keep.txt", targetDb.driveIndexDao().getByDriveId("Zbcdefghijklmnopqrstuv12345")?.name)
    }
}
