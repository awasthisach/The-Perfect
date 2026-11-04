package com.vvf.smartmanager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vvf.smartmanager.core.data.repository.SecureVaultRepository
import com.vvf.smartmanager.core.database.VVFDatabase
import com.vvf.smartmanager.core.database.dao.VaultDao
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import com.vvf.smartmanager.core.database.model.VaultItemEntity
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import java.io.File
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SecureVaultIndexIntegrationTest {
    private lateinit var context: Context
    private lateinit var database: VVFDatabase
    private lateinit var crypto: CryptoSecurityManager
    private lateinit var root: File
    private lateinit var source: File
    private lateinit var vaultDirectory: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = VVFDatabase.buildInMemoryDatabase(context)
        crypto = CryptoSecurityManager(context, "Robolectric")
        root = File(context.cacheDir, "vault-index-${UUID.randomUUID()}").apply { mkdirs() }
        source = File(root, "secret.txt").apply { writeText("sensitive source content") }
        vaultDirectory = File(root, "vault").apply { mkdirs() }
        runBlocking {
            database.fileDao().insertOrUpdate(
                FileMetadataEntity(
                    path = source.absolutePath,
                    name = source.name,
                    parentPath = source.parent.orEmpty(),
                    sizeBytes = source.length(),
                    mimeType = "text/plain",
                    isDirectory = false,
                    modifiedDate = source.lastModified(),
                    tags = "private-tag"
                )
            )
        }
    }

    @After
    fun tearDown() {
        database.close()
        root.deleteRecursively()
    }

    @Test
    fun successfulVaultShredRemovesOrdinarySearchMetadata() = runBlocking {
        val repository = repository()
        val result = repository.lockFileInVault(source, deleteOriginal = true)

        assertTrue(result.isSuccess)
        assertFalse(source.exists())
        assertEquals(null, database.fileDao().getByPath(source.absolutePath))
        assertTrue(database.searchFtsDao().searchFilesFts("private-tag").first().isEmpty())
        assertEquals(1, database.vaultDao().getVaultItemCount().first())
    }

    @Test
    fun failedSourceShredRetainsOriginalMetadataAndEncryptedVaultCopy() = runBlocking {
        val repository = repository(
            secureShredFile = { file ->
                if (file.absolutePath == source.absolutePath) false
                else crypto.secureShredFile(file)
            }
        )

        val result = repository.lockFileInVault(source, deleteOriginal = true)

        assertTrue(result.isFailure)
        assertTrue(source.exists())
        assertNotNull(database.fileDao().getByPath(source.absolutePath))
        assertEquals(listOf(source.absolutePath), database.searchFtsDao().searchFilesFts("private-tag").first().map { it.path })
        assertEquals(1, database.vaultDao().getVaultItemCount().first())
        assertEquals(1, database.vaultJournalDao().getPendingJournals("FAILED_SHRED").size)
    }

    @Test
    fun cancellationBeforeVaultRowCommitRollsBackEncryptedStagingCopy() = runBlocking {
        val delegate = database.vaultDao()
        val cancellingDao = object : VaultDao by delegate {
            override suspend fun insertVaultItem(item: VaultItemEntity) {
                throw CancellationException("cancel before vault row commit")
            }
        }
        val repository = repository(vaultDao = cancellingDao)

        try {
            repository.lockFileInVault(source, deleteOriginal = true)
            throw AssertionError("Expected cancellation")
        } catch (cancelled: CancellationException) {
            assertEquals("cancel before vault row commit", cancelled.message)
        }

        assertTrue(source.exists())
        assertNotNull(database.fileDao().getByPath(source.absolutePath))
        assertEquals(listOf(source.absolutePath), database.searchFtsDao().searchFilesFts("private-tag").first().map { it.path })
        assertTrue(vaultDirectory.listFiles().orEmpty().isEmpty())
        assertEquals(0, delegate.getVaultItemCount().first())
        assertEquals(1, database.vaultJournalDao().getPendingJournals("FAILED").size)
    }

    @Test
    fun cancellationAfterSuccessfulShredStillRemovesSearchMetadata() = runBlocking {
        val repository = repository(
            secureShredFile = { file ->
                if (file.absolutePath == source.absolutePath) {
                    assertTrue(crypto.secureShredFile(file))
                    throw CancellationException("cancel after source shred")
                }
                crypto.secureShredFile(file)
            }
        )

        try {
            repository.lockFileInVault(source, deleteOriginal = true)
            throw AssertionError("Expected cancellation")
        } catch (cancelled: CancellationException) {
            assertEquals("cancel after source shred", cancelled.message)
        }

        assertFalse(source.exists())
        assertEquals(null, database.fileDao().getByPath(source.absolutePath))
        assertTrue(database.searchFtsDao().searchFilesFts("private-tag").first().isEmpty())
        assertEquals(1, database.vaultDao().getVaultItemCount().first())
        assertEquals(1, database.vaultJournalDao().getPendingJournals("COMPLETED").size)
    }

    private fun repository(
        vaultDao: VaultDao = database.vaultDao(),
        secureShredFile: (File) -> Boolean = crypto::secureShredFile
    ) = SecureVaultRepository(
        vaultDao = vaultDao,
        cryptoManager = crypto,
        vaultDirectory = vaultDirectory,
        vaultJournalDao = database.vaultJournalDao(),
        fileDao = database.fileDao(),
        secureShredFile = secureShredFile
    )
}
