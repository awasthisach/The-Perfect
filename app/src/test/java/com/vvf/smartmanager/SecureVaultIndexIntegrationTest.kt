package com.vvf.smartmanager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vvf.smartmanager.core.data.repository.SecureVaultRepository
import com.vvf.smartmanager.core.database.VVFDatabase
import com.vvf.smartmanager.core.database.dao.VaultDao
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import com.vvf.smartmanager.core.database.model.VaultItemEntity
import com.vvf.smartmanager.core.database.model.VaultJournalEntity
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
import org.junit.Assert.assertNull
import org.junit.Assert.fail
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
                    tags = "privatetag"
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
        assertTrue(database.searchFtsDao().searchFilesFts("privatetag").first().isEmpty())
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
        assertEquals(listOf(source.absolutePath), database.searchFtsDao().searchFilesFts("privatetag").first().map { it.path })
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
        assertEquals(listOf(source.absolutePath), database.searchFtsDao().searchFilesFts("privatetag").first().map { it.path })
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
        assertTrue(database.searchFtsDao().searchFilesFts("privatetag").first().isEmpty())
        assertEquals(1, database.vaultDao().getVaultItemCount().first())
        assertEquals(1, database.vaultJournalDao().getPendingJournals("COMPLETED").size)
    }

    @Test
    fun startupReconciliationRemovesOnlyVaultSourcesThatAreActuallyMissing() = runBlocking {
        val missingSource = File(root, "previously-shredded.txt")
        database.fileDao().insertOrUpdate(
            FileMetadataEntity(
                path = missingSource.absolutePath,
                name = missingSource.name,
                parentPath = missingSource.parent.orEmpty(),
                sizeBytes = 12L,
                mimeType = "text/plain",
                isDirectory = false,
                modifiedDate = 4L,
                tags = "privatetag"
            )
        )
        database.vaultDao().insertVaultItem(
            VaultItemEntity(
                id = "copy-only-item",
                encryptedFileName = "copy-only.vvf",
                originalName = source.name,
                originalPath = source.absolutePath,
                sizeBytes = source.length(),
                mimeType = "text/plain",
                category = "Other",
                encryptionIv = "copy-iv",
                createdAt = 5L
            )
        )
        database.vaultDao().insertVaultItem(
            VaultItemEntity(
                id = "shredded-item",
                encryptedFileName = "shredded.vvf",
                originalName = missingSource.name,
                originalPath = missingSource.absolutePath,
                sizeBytes = 12L,
                mimeType = "text/plain",
                category = "Other",
                encryptionIv = "shredded-iv",
                createdAt = 6L,
                isDecoy = true
            )
        )

        val removed = repository().reconcileVaultedSourceMetadata()

        assertEquals(1, removed)
        assertNotNull(database.fileDao().getByPath(source.absolutePath))
        assertNull(database.fileDao().getByPath(missingSource.absolutePath))
    }

    @Test
    fun recoveryRemovesNonemptyEncryptedOrphanWhenPlaintextStillExists() = runBlocking {
        val orphan = File(vaultDirectory, "orphan.vvf").apply { writeText("encrypted orphan") }
        database.vaultJournalDao().insertJournal(
            VaultJournalEntity(
                operationType = "ENCRYPT",
                originalPath = source.absolutePath,
                vaultPath = orphan.absolutePath,
                status = "PENDING"
            )
        )

        val recovered = repository().recoverOrphanedJournals()

        assertEquals(1, recovered)
        assertFalse(orphan.exists())
        assertTrue(source.exists())
        assertTrue(database.vaultJournalDao().getPendingJournals("PENDING").isEmpty())
        assertEquals(1, database.vaultJournalDao().getPendingJournals("FAILED").size)
    }

    @Test
    fun recoveryFailsClosedAndRetainsOnlyEncryptedCopyWhenPlaintextIsMissing() = runBlocking {
        val missingSource = File(root, "missing-source.txt")
        val orphan = File(vaultDirectory, "orphan-only-copy.vvf").apply { writeText("encrypted only copy") }
        database.vaultJournalDao().insertJournal(
            VaultJournalEntity(
                operationType = "ENCRYPT",
                originalPath = missingSource.absolutePath,
                vaultPath = orphan.absolutePath,
                status = "PENDING"
            )
        )

        try {
            repository().recoverOrphanedJournals()
            fail("Expected unresolved recovery to fail closed")
        } catch (expected: IllegalStateException) {
            assertTrue(expected.message.orEmpty().contains("unresolved operation"))
        }

        assertTrue(orphan.exists())
        assertEquals(1, database.vaultJournalDao().getPendingJournals("PENDING").size)
    }

    @Test
    fun recoveryCompletesShreddedSourceAndRemovesItsOrdinaryMetadata() = runBlocking {
        val missingSource = File(root, "already-shredded.txt")
        database.fileDao().insertOrUpdate(
            FileMetadataEntity(
                path = missingSource.absolutePath,
                name = missingSource.name,
                parentPath = missingSource.parent.orEmpty(),
                sizeBytes = 25L,
                mimeType = "text/plain",
                isDirectory = false,
                modifiedDate = 2L,
                tags = "privatetag"
            )
        )
        val encryptedName = "enc-recovery.vvf"
        File(vaultDirectory, encryptedName).writeText("encrypted vault content")
        database.vaultDao().insertVaultItem(
            VaultItemEntity(
                id = "recovery-vault-item",
                encryptedFileName = encryptedName,
                originalName = missingSource.name,
                originalPath = missingSource.absolutePath,
                sizeBytes = 25L,
                mimeType = "text/plain",
                category = "Other",
                encryptionIv = "test-iv",
                createdAt = 3L
            )
        )
        database.vaultJournalDao().insertJournal(
            VaultJournalEntity(
                operationType = "ENCRYPT",
                originalPath = missingSource.absolutePath,
                vaultPath = File(vaultDirectory, encryptedName).absolutePath,
                status = "PENDING"
            )
        )

        repository().recoverOrphanedJournals()

        assertNull(database.fileDao().getByPath(missingSource.absolutePath))
        assertTrue(database.searchFtsDao().searchFilesFts("privatetag").first().isEmpty())
        assertEquals(1, database.vaultDao().getVaultItemCount().first())
        assertTrue(database.vaultJournalDao().getPendingJournals("PENDING").isEmpty())
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
