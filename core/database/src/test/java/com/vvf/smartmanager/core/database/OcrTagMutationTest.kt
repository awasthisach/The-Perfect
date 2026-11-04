package com.vvf.smartmanager.core.database

import androidx.test.core.app.ApplicationProvider
import com.vvf.smartmanager.core.database.dao.FileDao
import com.vvf.smartmanager.core.database.dao.SearchFtsDao
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class OcrTagMutationTest {
    private lateinit var database: VVFDatabase
    private lateinit var fileDao: FileDao
    private lateinit var searchFtsDao: SearchFtsDao

    @Before
    fun setup() {
        database = VVFDatabase.buildInMemoryDatabase(ApplicationProvider.getApplicationContext())
        fileDao = database.fileDao()
        searchFtsDao = database.searchFtsDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun addingUserTagKeepsExistingRetryMarker() = runBlocking {
        seed("invoice,$RETRY")
        assertFalse(fileDao.addTagPreservingLatestMetadata(fallback(), " Finance "))
        assertEquals(setOf("invoice", RETRY, "finance"), currentTags())
    }

    @Test
    fun removingUserTagKeepsRetryMarkerAndOtherTags() = runBlocking {
        seed("invoice,finance,$RETRY")
        fileDao.removeTagPreservingLatestMetadata(PATH, " INVOICE ")
        assertEquals(setOf("finance", RETRY), currentTags())
    }

    @Test
    fun retryUpdateAfterUserMutationKeepsUserTags() = runBlocking {
        seed("invoice")
        fileDao.addTagPreservingLatestMetadata(fallback(), "finance")
        fileDao.setOcrRetryMarker(PATH, RETRY)
        assertEquals(setOf("invoice", "finance", RETRY), currentTags())
    }

    @Test
    fun clearingRetryMarkerKeepsCompletionAndUserEdits() = runBlocking {
        seed("invoice,$RETRY")
        fileDao.addTagPreservingLatestMetadata(fallback(), "ocr")
        fileDao.removeTagPreservingLatestMetadata(PATH, "invoice")
        fileDao.setOcrRetryMarker(PATH, null)
        assertEquals(setOf("ocr"), currentTags())
    }

    @Test
    fun existingMetadataWinsOverStaleFallback() = runBlocking {
        fileDao.insertOrUpdate(
            fallback("invoice,$RETRY").copy(
                sizeBytes = 4096L, isFavorite = true,
                modifiedDate = 2000L, md5Hash = "current-hash", operationState = "IN_PROGRESS"
            )
        )
        val before = requireNotNull(fileDao.getByPath(PATH))
        val stale = fallback("stale-tag").copy(name = "stale-name", sizeBytes = 0L)
        assertFalse(fileDao.addTagPreservingLatestMetadata(stale, "finance"))
        val after = requireNotNull(fileDao.getByPath(PATH))
        assertEquals(before.copy(tags = after.tags), after)
        assertEquals(setOf("invoice", RETRY, "finance"), currentTags())
    }

    @Test
    fun missingFileIsCreatedOnceAndLaterTagsAreMerged() = runBlocking {
        assertTrue(fileDao.addTagPreservingLatestMetadata(fallback("base"), "finance"))
        val created = requireNotNull(fileDao.getByPath(PATH))
        assertTrue(created.id > 0L)
        assertFalse(fileDao.addTagPreservingLatestMetadata(fallback("stale-tag"), "review"))
        assertEquals(created.id, requireNotNull(fileDao.getByPath(PATH)).id)
        assertEquals(setOf("base", "finance", "review"), currentTags())
    }

    @Test
    fun concurrentNewPathAddsCreateOneRowWithoutDroppingTags() = runBlocking {
        val created = withTimeout(15000L) {
            coroutineScope {
                (0 until 16).map { i ->
                    async(Dispatchers.IO) {
                        fileDao.addTagPreservingLatestMetadata(fallback(), "new-$i")
                    }
                }.awaitAll()
            }
        }
        assertEquals(1, created.count { it })
        assertEquals((0 until 16).map { "new-$it" }.toSet(), currentTags())
    }

    @Test
    fun removingTagFromMissingFileDoesNotCreateRow() = runBlocking {
        fileDao.removeTagPreservingLatestMetadata(PATH, "finance")
        assertNull(fileDao.getByPath(PATH))
    }

    @Test
    fun concurrentUserTagsAndRetryUpdatesPreserveAllTags() = runBlocking {
        seed("seed")
        withTimeout(15000L) {
            coroutineScope {
                val edits = (0 until 32).map { i ->
                    async(Dispatchers.IO) {
                        fileDao.addTagPreservingLatestMetadata(fallback(), "user-$i")
                    }
                }
                val retries = launch(Dispatchers.IO) {
                    repeat(20) { i ->
                        fileDao.setOcrRetryMarker(PATH, "ocr_retry_after:${2000000000000L + i}:$i")
                    }
                }
                edits.awaitAll()
                retries.join()
            }
        }
        val expected = (0 until 32).map { "user-$it" }.toSet() +
            setOf("seed", "ocr_retry_after:2000000000019:19")
        assertEquals(expected, currentTags())
    }

    @Test
    fun concurrentAddsAndRemovalsDoNotRestoreRemovedTags() = runBlocking {
        val obsolete = (0 until 24).map { "old-$it" }
        seed((obsolete + RETRY).joinToString(","))
        withTimeout(15000L) {
            coroutineScope {
                val removals = obsolete.map { tag ->
                    async(Dispatchers.IO) { fileDao.removeTagPreservingLatestMetadata(PATH, tag) }
                }
                val additions = (0 until 24).map { i ->
                    async(Dispatchers.IO) {
                        fileDao.addTagPreservingLatestMetadata(fallback(), "new-$i")
                    }
                }
                removals.awaitAll()
                additions.awaitAll()
            }
        }
        assertEquals((0 until 24).map { "new-$it" }.toSet() + RETRY, currentTags())
    }

    @Test
    fun atomicTagChangesKeepFtsIndexInSync() = runBlocking {
        seed("seed")
        fileDao.addTagPreservingLatestMetadata(fallback(), "finance")
        assertEquals(listOf(PATH), searchFtsDao.searchFilesFts("finance").first().map { it.path })
        fileDao.removeTagPreservingLatestMetadata(PATH, "finance")
        assertTrue(searchFtsDao.searchFilesFts("finance").first().isEmpty())
    }

    private suspend fun seed(tags: String) {
        fileDao.insertOrUpdate(fallback(tags))
    }

    private suspend fun currentTags(): Set<String> =
        requireNotNull(fileDao.getByPath(PATH)).tags.split(',')
            .map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private fun fallback(tags: String = "") = FileMetadataEntity(
        path = PATH, name = "document.png", parentPath = "/test", sizeBytes = 100L,
        mimeType = "image/png", isDirectory = false, modifiedDate = 1000L, tags = tags
    )

    companion object {
        private const val PATH = "/test/document.png"
        private const val RETRY = "ocr_retry_after:2000000000000:1"
    }
}
