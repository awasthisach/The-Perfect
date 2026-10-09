package com.vvf.smartmanager.core.database.dao

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vvf.smartmanager.core.database.VVFDatabase
import com.vvf.smartmanager.core.database.model.DriveIndexFileEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DriveIndexDaoTest {
    private lateinit var database: VVFDatabase
    private lateinit var dao: DriveIndexDao

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = VVFDatabase.buildInMemoryDatabase(context)
        dao = database.driveIndexDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private suspend fun add(id: String, name: String, size: Long, status: String = "METADATA_ONLY") {
        dao.upsertFile(
            DriveIndexFileEntity(
                driveFileId = id,
                name = name,
                mimeType = "application/pdf",
                sizeBytes = size,
                modifiedTimeMs = 1L,
                indexStatus = status
            )
        )
    }

    @Test
    fun changedPinnedFileIsReindexedAndPinStalenessIsPreservedUntilRepinnedOrUnpinned() = runBlocking {
        val id = "PinnedFileIdentifier1234567890"
        dao.upsertFile(
            DriveIndexFileEntity(
                driveFileId = id,
                name = "changed.pdf",
                mimeType = "application/pdf",
                sizeBytes = 100L,
                modifiedTimeMs = 200L,
                extractedText = "",
                pinnedPath = "/private/offline/old.pdf",
                pinnedAtMs = 10L,
                indexStatus = "METADATA_CHANGED_PIN_STALE"
            )
        )

        assertEquals(id, dao.getFilesNeedingText().single().driveFileId)
        dao.updateExtractedText(id, "new remote text", "NATIVE_TEXT", "TEXT_INDEXED", 300L)
        assertEquals("TEXT_INDEXED_PIN_STALE", dao.getByDriveId(id)?.indexStatus)

        dao.unpin(id)
        assertEquals("TEXT_INDEXED", dao.getByDriveId(id)?.indexStatus)
    }

    @Test
    fun duplicateGroupsRequireSameNameAndSizeAndExcludeRemovedOrEmptyFiles() = runBlocking {
        add("file-id-duplicate-00000001", "report.pdf", 100L)
        add("file-id-duplicate-00000002", "report.pdf", 100L)
        add("file-id-same-size-000000003", "other.pdf", 100L)
        add("file-id-same-name-000000004", "report.pdf", 101L)
        add("file-id-removed-0000000005", "report.pdf", 100L, "REMOTE_REMOVED")
        add("file-id-zero-size-000000006", "empty.pdf", 0L)
        add("file-id-zero-size-000000007", "empty.pdf", 0L)

        val groups = dao.findNameAndSizeDuplicateGroups()

        assertEquals(1, groups.size)
        assertEquals("report.pdf", groups.single().name)
        assertEquals(100L, groups.single().sizeBytes)
        assertEquals(2, groups.single().count)
        val candidates = dao.getDuplicateGroupFiles("report.pdf", 100L)
        assertEquals(2, candidates.size)
        assertEquals(
            listOf("file-id-duplicate-00000001", "file-id-duplicate-00000002"),
            candidates.map { it.driveFileId }.sorted()
        )
    }
}
