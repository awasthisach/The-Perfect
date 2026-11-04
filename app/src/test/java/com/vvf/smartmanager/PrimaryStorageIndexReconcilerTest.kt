package com.vvf.smartmanager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vvf.smartmanager.core.background.workers.FileIndexingOutcome
import com.vvf.smartmanager.core.data.storage.PrimaryStorageMutationLock
import com.vvf.smartmanager.core.data.storage.PrimaryStorageScanResult
import com.vvf.smartmanager.core.database.VVFDatabase
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import com.vvf.smartmanager.core.model.FileItem
import java.io.File
import java.util.UUID
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.withLock
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PrimaryStorageIndexReconcilerTest {
    private lateinit var context: Context
    private lateinit var database: VVFDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = VVFDatabase.buildInMemoryDatabase(context)
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun exactTenThousandItemScanCannotPruneStaleRowsEvenWhenMarkedComplete() = runBlocking {
        val stalePath = "/primary/stale.txt"
        database.fileDao().insertOrUpdate(entity(stalePath))
        val cappedItems = List(PRIMARY_STORAGE_INDEX_LIMIT) { index ->
            item("/primary/scan-$index.txt")
        }
        var warning: String? = null

        val result = reconcilePrimaryStorageIndex(
            scan = PrimaryStorageScanResult(cappedItems, isComplete = true),
            fileDao = database.fileDao(),
            searchFtsDao = database.searchFtsDao(),
            isCurrentFile = { false },
            onWarning = { warning = it }
        )

        assertEquals(FileIndexingOutcome.Completed(0), result)
        assertNotNull(database.fileDao().getByPath(stalePath))
        assertNotNull(warning)
    }

    @Test
    fun completeBelowCapScanPrunesMissingRowsAndIndexesCurrentFiles() = runBlocking {
        val stalePath = "/primary/stale.txt"
        val currentPath = "/primary/current.txt"
        database.fileDao().insertOrUpdate(entity(stalePath))

        val result = reconcilePrimaryStorageIndex(
            scan = PrimaryStorageScanResult(listOf(item(currentPath)), isComplete = true),
            fileDao = database.fileDao(),
            searchFtsDao = database.searchFtsDao(),
            isCurrentFile = { true }
        )

        assertEquals(FileIndexingOutcome.Completed(1), result)
        assertNull(database.fileDao().getByPath(stalePath))
        assertNotNull(database.fileDao().getByPath(currentPath))
    }

    @Test
    fun stalePreVaultScanCannotReinsertPathAfterVaultDeletionWinsLock() = runBlocking {
        val source = File(context.cacheDir, "vault-race-${UUID.randomUUID()}.txt").apply {
            writeText("secret")
        }
        database.fileDao().insertOrUpdate(entity(source.absolutePath))

        PrimaryStorageMutationLock.mutex.withLock {
            source.delete()
            database.fileDao().deleteByPath(source.absolutePath)
        }

        reconcilePrimaryStorageIndex(
            scan = PrimaryStorageScanResult(listOf(item(source.absolutePath)), isComplete = true),
            fileDao = database.fileDao(),
            searchFtsDao = database.searchFtsDao(),
            isCurrentFile = { File(it).exists() }
        )

        assertNull(database.fileDao().getByPath(source.absolutePath))
    }

    @Test
    fun vaultDeletionWaitsForInFlightReconciliationAndRemovesItsInsertedRow() = runBlocking {
        val source = File(context.cacheDir, "vault-race-${UUID.randomUUID()}.txt").apply {
            writeText("secret")
        }
        val started = CountDownLatch(1)
        val continueReconcile = CountDownLatch(1)
        val scan = PrimaryStorageScanResult(listOf(item(source.absolutePath)), isComplete = true)

        val indexing = async(Dispatchers.IO) {
            reconcilePrimaryStorageIndex(
                scan = scan,
                fileDao = database.fileDao(),
                searchFtsDao = database.searchFtsDao(),
                isCurrentFile = {
                    started.countDown()
                    check(continueReconcile.await(10, TimeUnit.SECONDS))
                    File(it).exists()
                }
            )
        }

        withContext(Dispatchers.IO) {
            check(started.await(10, TimeUnit.SECONDS))
        }
        val vaultDeletion = async(Dispatchers.IO) {
            PrimaryStorageMutationLock.mutex.withLock {
                source.delete()
                database.fileDao().deleteByPath(source.absolutePath)
            }
        }

        continueReconcile.countDown()
        indexing.await()
        vaultDeletion.await()

        assertNull(database.fileDao().getByPath(source.absolutePath))
    }

    private fun entity(path: String) = FileMetadataEntity(
        path = path,
        name = File(path).name,
        parentPath = File(path).parent.orEmpty(),
        sizeBytes = 10L,
        mimeType = "text/plain",
        isDirectory = false,
        modifiedDate = 1L
    )

    private fun item(path: String) = FileItem(
        path = path,
        name = File(path).name,
        sizeBytes = 10L,
        mimeType = "text/plain",
        isDirectory = false,
        lastModified = 1L
    )
}
