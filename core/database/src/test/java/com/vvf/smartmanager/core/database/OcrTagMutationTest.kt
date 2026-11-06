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
    fun addingUserTagKeepsDedicatedRetryState() = runBlocking {
        seed(tags = "invoice", retryAt = 2_000L, attempt = 2)
        assertFalse(fileDao.addTagPreservingLatestMetadata(fallback(), " Finance "))
        assertEquals(setOf("invoice", "finance"), currentTags())
        assertRetryState(2_000L, 2)
    }

    @Test
    fun removingUserTagKeepsDedicatedRetryState() = runBlocking {
        seed(tags = "invoice,finance", retryAt = 2_000L, attempt = 3)
        fileDao.removeTagPreservingLatestMetadata(PATH, " INVOICE ")
        assertEquals(setOf("finance"), currentTags())
        assertRetryState(2_000L, 3)
    }

    @Test
    fun retryUpdateDoesNotModifyUserTags() = runBlocking {
        seed(tags = "invoice")
        fileDao.addTagPreservingLatestMetadata(fallback(), "finance")
        fileDao.setOcrRetryState(PATH, 3_000L, 4)
        assertEquals(setOf("invoice", "finance"), currentTags())
        assertRetryState(3_000L, 4)
    }

    @Test
    fun clearingRetryStateKeepsCompletionAndUserTags() = runBlocking {
        seed(tags = "invoice,ocr,finance", retryAt = 4_000L, attempt = 5)
        fileDao.removeTagPreservingLatestMetadata(PATH, "invoice")
        fileDao.setOcrRetryState(PATH, null, 0)
        assertEquals(setOf("ocr", "finance"), currentTags())
        assertRetryState(null, 0)
    }

    @Test
    fun existingMetadataWinsOverStaleFallback() = runBlocking {
        fileDao.insertOrUpdate(
            fallback("invoice").copy(
                sizeBytes = 4096L,
                isFavorite = true,
                modifiedDate = 2000L,
                md5Hash = "current-hash",
                operationState = "IN_PROGRESS",
                ocrRetryAfterEpochMillis = 8_000L,
                ocrRetryAttempt = 6
            )
        )
        val before = requireNotNull(fileDao.getByPath(PATH))
        val stale = fallback("stale-tag").copy(name = "stale-name", sizeBytes = 0L)
        assertFalse(fileDao.addTagPreservingLatestMetadata(stale, "finance"))
        val after = requireNotNull(fileDao.getByPath(PATH))
        assertEquals(before.copy(tags = after.tags), after)
        assertEquals(setOf("invoice", "finance"), currentTags())
        assertRetryState(8_000L, 6)
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
    fun concurrentUserTagsAndRetryUpdatesPreserveBothKindsOfState() = runBlocking {
        seed(tags = "seed")
        withTimeout(15000L) {
            coroutineScope {
                val edits = (0 until 32).map { i ->
                    async(Dispatchers.IO) {
                        fileDao.addTagPreservingLatestMetadata(fallback(), "user-$i")
                    }
                }
                edits.awaitAll()
            }
        }
        fileDao.setOcrRetryState(PATH, 9_000L, 7)
        val expected = (0 until 32).map { "user-$it" }.toSet() + "seed"
        assertEquals(expected, currentTags())
        assertRetryState(9_000L, 7)
    }

    @Test
    fun concurrentAddsAndRemovalsDoNotRestoreRemovedTags() = runBlocking {
        val obsolete = (0 until 24).map { "old-$it" }
        seed(tags = obsolete.joinToString(","))
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
        assertEquals((0 until 24).map { "new-$it" }.toSet(), currentTags())
    }

    @Test
    fun atomicTagChangesKeepFtsIndexInSync() = runBlocking {
        seed(tags = "seed")
        fileDao.addTagPreservingLatestMetadata(fallback(), "finance")
        assertEquals(listOf(PATH), searchFtsDao.searchFilesFts("finance").first().map { it.path })
        fileDao.removeTagPreservingLatestMetadata(PATH, "finance")
        assertTrue(searchFtsDao.searchFilesFts("finance").first().isEmpty())
    }

    @Test
    fun internalRetryStateIsNotSearchableButSimilarUserTagRemainsSearchable() = runBlocking {
        seed(tags = "myocr_retry_after:9999999999999,invoice", retryAt = 1_000L, attempt = 2)

        assertTrue(searchFtsDao.searchFilesFts("ocr_retry_after").first().isEmpty())
        assertTrue(searchFtsDao.searchFilesFallback("1000").first().isEmpty())
        assertEquals(listOf(PATH), searchFtsDao.searchByTag("myocr_retry_after").first().map { it.path })
        assertEquals(listOf(PATH), searchFtsDao.searchFilesFallback("9999999999999").first().map { it.path })
        assertEquals(setOf("myocr_retry_after:9999999999999", "invoice"), currentTags())
    }

    @Test
    fun exactPrefixUserTagsRemainSearchableAndVisible() = runBlocking {
        val exactUserTag = "ocr_retry_after:9999999999999"
        seed(tags = "$exactUserTag,invoice", retryAt = 1_000L, attempt = 2)

        assertEquals(listOf(PATH), searchFtsDao.searchByTag(exactUserTag).first().map { it.path })
        assertEquals(setOf(exactUserTag, "invoice"), currentTags())
        assertRetryState(1_000L, 2)
    }

    @Test
    fun legacyMigrationParserRemovesOnlyExactInternalMarker() {
        val parsed = parseLegacyOcrRetryMetadata("invoice, ocr_retry_after:1234:3, myocr_retry_after:9999, finance")
        assertEquals("invoice,myocr_retry_after:9999,finance", parsed.visibleTags)
        assertEquals(1234L, parsed.retryAfterEpochMillis)
        assertEquals(3, parsed.retryAttempt)
        assertTrue(parsed.hadInternalMarker)

        val userOnly = parseLegacyOcrRetryMetadata("myocr_retry_after:9999,finance")
        assertEquals("myocr_retry_after:9999,finance", userOnly.visibleTags)
        assertNull(userOnly.retryAfterEpochMillis)
        assertEquals(0, userOnly.retryAttempt)
        assertFalse(userOnly.hadInternalMarker)

        val partialPrefixUserTags = parseLegacyOcrRetryMetadata(
            "ocr_retry_after:9999,ocr_retry_after:invalid:4,finance"
        )
        assertEquals("ocr_retry_after:9999,ocr_retry_after:invalid:4,finance", partialPrefixUserTags.visibleTags)
        assertNull(partialPrefixUserTags.retryAfterEpochMillis)
        assertEquals(0, partialPrefixUserTags.retryAttempt)
        assertFalse(partialPrefixUserTags.hadInternalMarker)
    }

    private suspend fun seed(
        tags: String,
        retryAt: Long? = null,
        attempt: Int = 0
    ) {
        fileDao.insertOrUpdate(
            fallback(tags).copy(
                ocrRetryAfterEpochMillis = retryAt,
                ocrRetryAttempt = attempt
            )
        )
    }

    private suspend fun currentTags(): Set<String> =
        requireNotNull(fileDao.getByPath(PATH)).tags.split(',')
            .map { it.trim() }.filter { it.isNotEmpty() }.toSet()

    private suspend fun assertRetryState(expectedAt: Long?, expectedAttempt: Int) {
        val entity = requireNotNull(fileDao.getByPath(PATH))
        assertEquals(expectedAt, entity.ocrRetryAfterEpochMillis)
        assertEquals(expectedAttempt, entity.ocrRetryAttempt)
    }

    private fun fallback(tags: String = "") = FileMetadataEntity(
        path = PATH,
        name = "document.png",
        parentPath = "/test",
        sizeBytes = 100L,
        mimeType = "image/png",
        isDirectory = false,
        modifiedDate = 1000L,
        tags = tags
    )

    companion object {
        private const val PATH = "/test/document.png"
    }
}
