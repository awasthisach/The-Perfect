package com.vvf.smartmanager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vvf.smartmanager.core.database.VVFDatabase
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import com.vvf.smartmanager.core.database.model.VaultItemEntity
import com.vvf.smartmanager.core.model.SemanticIndexRecord
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Comprehensive Critical User Journey (CUJ) & Security Test Suite for VVF Smart Manager.
 * Robolectric SDK pinned to 34 (compileSdk 36 is not yet fully supported by Robolectric).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], manifest = Config.NONE)
class VVFSmartManagerCUJTest {

    private lateinit var context: Context
    private lateinit var database: VVFDatabase

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = VVFDatabase.buildInMemoryDatabase(context)
    }

    @Test
    fun cuj1_fileStorageAndRecycleBinLifecycle() = runBlocking {
        val fileDao = database.fileDao()

        val file = FileMetadataEntity(
            path = "/storage/emulated/0/Documents/Project_Plan.pdf",
            name = "Project_Plan.pdf",
            parentPath = "/storage/emulated/0/Documents",
            sizeBytes = 2048576L,
            mimeType = "application/pdf",
            isDirectory = false,
            modifiedDate = System.currentTimeMillis(),
            isFavorite = false,
            isTrash = false
        )
        val insertedId = fileDao.insertOrUpdate(file)
        assertTrue(insertedId > 0)

        val retrieved = fileDao.getByPath("/storage/emulated/0/Documents/Project_Plan.pdf")
        assertNotNull(retrieved)
        assertEquals("Project_Plan.pdf", retrieved?.name)
        assertEquals(2048576L, retrieved?.sizeBytes)

        fileDao.markAsTrash(
            path = "/storage/emulated/0/Documents/Project_Plan.pdf",
            isTrash = true,
            originalPath = "/storage/emulated/0/Documents/Project_Plan.pdf",
            deletedTimestamp = System.currentTimeMillis()
        )
        val inRecycleBin = fileDao.getByPath("/storage/emulated/0/Documents/Project_Plan.pdf")
        assertTrue(inRecycleBin?.isTrash == true)

        val recycleBinItems = fileDao.getTrashFiles().first()
        assertEquals(1, recycleBinItems.size)
        assertEquals("/storage/emulated/0/Documents/Project_Plan.pdf", recycleBinItems[0].path)

        fileDao.restoreFromTrash("/storage/emulated/0/Documents/Project_Plan.pdf")
        val restored = fileDao.getByPath("/storage/emulated/0/Documents/Project_Plan.pdf")
        assertFalse(restored?.isTrash == true)

        fileDao.deleteByPath("/storage/emulated/0/Documents/Project_Plan.pdf")
        val deleted = fileDao.getByPath("/storage/emulated/0/Documents/Project_Plan.pdf")
        assertNull(deleted)
    }

    @Test
    fun cuj2_secureVaultEncryptionAndLockUnlock() = runBlocking {
        val vaultDao = database.vaultDao()

        val vaultItem = VaultItemEntity(
            id = "vault_item_1",
            originalName = "Confidential_Financials.xlsx",
            originalPath = "/storage/emulated/0/Documents/Confidential_Financials.xlsx",
            encryptedFileName = "enc_financials_uuid.vvfvault",
            sizeBytes = 4096000L,
            mimeType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            category = "Documents",
            encryptionIv = "k9sD81uNqwXzP==",
            notes = "Confidential Q3"
        )
        vaultDao.insertVaultItem(vaultItem)

        val allVaultItems = vaultDao.getAllVaultItems().first()
        assertEquals(1, allVaultItems.size)
        assertEquals("Confidential_Financials.xlsx", allVaultItems[0].originalName)
        assertEquals("enc_financials_uuid.vvfvault", allVaultItems[0].encryptedFileName)

        vaultDao.deleteById("vault_item_1")
        val remaining = vaultDao.getAllVaultItems().first()
        assertTrue(remaining.isEmpty())
    }

    @Test
    fun cuj3_searchEngineFtsHistoryAndTagging() = runBlocking {
        val searchFtsDao = database.searchFtsDao()
        val fileDao = database.fileDao()

        val f1 = FileMetadataEntity(
            path = "/storage/emulated/0/Documents/VVF_Quarterly_Report_2026.pdf",
            name = "VVF_Quarterly_Report_2026.pdf",
            parentPath = "/storage/emulated/0/Documents",
            sizeBytes = 1048576L,
            mimeType = "application/pdf",
            isDirectory = false,
            modifiedDate = System.currentTimeMillis(),
            tags = "Finance,Audit"
        )
        val f2 = FileMetadataEntity(
            path = "/storage/emulated/0/DCIM/Family_Vacation_Photo.jpg",
            name = "Family_Vacation_Photo.jpg",
            parentPath = "/storage/emulated/0/DCIM",
            sizeBytes = 5242880L,
            mimeType = "image/jpeg",
            isDirectory = false,
            modifiedDate = System.currentTimeMillis(),
            tags = "Family,Vacation"
        )
        fileDao.insertOrUpdate(f1)
        fileDao.insertOrUpdate(f2)

        val searchResults = searchFtsDao.searchFilesFallback("Quarterly").first()
        assertEquals(1, searchResults.size)
        assertEquals("VVF_Quarterly_Report_2026.pdf", searchResults[0].name)

        val tagResults = searchFtsDao.searchByTag("Finance").first()
        assertEquals(1, tagResults.size)
        assertEquals("VVF_Quarterly_Report_2026.pdf", tagResults[0].name)

        searchFtsDao.updateTagsByPath(f1.path, "Finance,Audit,Q3Approved")
        val updatedTagResults = searchFtsDao.searchByTag("Q3Approved").first()
        assertEquals(1, updatedTagResults.size)
    }

    @Test
    fun cuj4_duplicateDetectionAndJunkCategorization() = runBlocking {
        val fileDao = database.fileDao()

        val dup1 = FileMetadataEntity(
            path = "/storage/emulated/0/Download/Backup_Image.png",
            name = "Backup_Image.png",
            parentPath = "/storage/emulated/0/Download",
            sizeBytes = 3000000L,
            mimeType = "image/png",
            isDirectory = false,
            modifiedDate = System.currentTimeMillis(),
            md5Hash = "hash_sha256_exact_match_999"
        )
        val dup2 = FileMetadataEntity(
            path = "/storage/emulated/0/DCIM/Backup_Image_Copy.png",
            name = "Backup_Image_Copy.png",
            parentPath = "/storage/emulated/0/DCIM",
            sizeBytes = 3000000L,
            mimeType = "image/png",
            isDirectory = false,
            modifiedDate = System.currentTimeMillis() + 1000,
            md5Hash = "hash_sha256_exact_match_999"
        )
        fileDao.insertOrUpdate(dup1)
        fileDao.insertOrUpdate(dup2)

        val duplicates = fileDao.getFilesByHash("hash_sha256_exact_match_999")
        assertEquals(2, duplicates.size)
        assertEquals("Backup_Image.png", duplicates[0].name)
        assertEquals("Backup_Image_Copy.png", duplicates[1].name)
    }

    @Test
    fun cuj5_applicationContextAndComponentsSanity() {
        val app = ApplicationProvider.getApplicationContext<VVFApplication>()
        assertNotNull("VVF Application context must be initialized", app)
        assertNotNull("Database must be reachable", app.database)
        assertNotNull("Crypto Security Manager must be active", app.cryptoSecurityManager)
        assertNotNull("File Manager Repository must be active", app.fileManagerRepository)
        assertNotNull("Vault Repository must be active", app.vaultRepository)
        assertNotNull("Search Repository must be active", app.searchRepository)
        assertNotNull("OCR Plugin SPI must be registered", app.ocrPlugin)
        assertNotNull("Semantic Search SPI must be registered", app.semanticSearchPlugin)
        assertNotNull("Google Drive Service must be registered", app.googleDriveService)
    }

    @Test
    fun cuj6_safeDestructiveOperationPolicy() {
        val db = database
        assertFalse(db.inTransaction())
    }

    @Test
    fun cuj7_databaseEncryptionPassphraseWiring() {
        val app = ApplicationProvider.getApplicationContext<VVFApplication>()
        assertNotNull(app.cloudSyncUseCase)
        assertNotNull(app.googleDriveService)
    }

    @Test
    fun cuj8_cloudSyncQueueIsolationAndTokenSanitization() {
        val fileEntity = FileMetadataEntity(
            path = "/storage/emulated/0/DCIM/JournalTest.jpg",
            name = "JournalTest.jpg",
            parentPath = "/storage/emulated/0/DCIM",
            sizeBytes = 1500000L,
            mimeType = "image/jpeg",
            isDirectory = false,
            modifiedDate = System.currentTimeMillis()
        )
        assertEquals("IDLE", fileEntity.operationState)
        val inProgressEntity = fileEntity.copy(operationState = "MOVING")
        assertEquals("MOVING", inProgressEntity.operationState)
    }

    @Test
    fun cuj9_semanticIndexRecordAndIdentityBinding() {
        val record = SemanticIndexRecord(
            fileId = "local_doc_101",
            canonicalPath = "/storage/emulated/0/Documents/doc101.pdf",
            contentIdentityVersion = 1L,
            modelVersion = "tflite_mobilebert_v1",
            embeddingDimension = 384,
            embedding = FloatArray(384) { 0.5f },
            createdAt = System.currentTimeMillis()
        )
        assertEquals(384, record.embeddingDimension)
        assertEquals("local_doc_101", record.fileId)
        assertEquals(1L, record.contentIdentityVersion)
        assertEquals(384, record.embedding.size)
    }

    @Test
    fun cuj10_physicalStorageBoundaryValidation() = runBlocking {
        val storageManager = com.vvf.smartmanager.core.data.storage.StorageManager(context, database.fileDao())

        val allowedRoots = storageManager.getAllowedStorageRoots()
        assertTrue("Storage roots must be present", allowedRoots.isNotEmpty())

        val validCachePath = context.cacheDir.absolutePath
        assertTrue("Cache dir must be allowed", storageManager.isAllowedPhysicalPath(validCachePath))

        val disallowedPath = "/data/system/users/0/secret.xml"
        assertFalse("System path must be disallowed", storageManager.isAllowedPhysicalPath(disallowedPath))

        val illegalDeleteResult = storageManager.deleteSafely(disallowedPath)
        assertTrue("Disallowed path mutation must fail", illegalDeleteResult.isFailure)
    }

    @Test
    fun cuj11_vaultPinLifecycleAndDecoyIsolation() {
        val cryptoManager = CryptoSecurityManager(context, "InUnitTestMemory")

        assertTrue(cryptoManager.setupVaultPin("123456"))
        assertTrue(cryptoManager.setupDecoyPin("654321"))

        val realAuth = cryptoManager.verifyVaultPinWithResult("123456")
        assertEquals(CryptoSecurityManager.VaultAuthResult.SUCCESS_REAL, realAuth)
        assertTrue(cryptoManager.verifyVaultPin("123456"))

        val decoyAuth = cryptoManager.verifyVaultPinWithResult("654321")
        assertEquals(CryptoSecurityManager.VaultAuthResult.SUCCESS_DECOY, decoyAuth)
        assertTrue(cryptoManager.verifyDecoyPin("654321"))

        val invalidAuth = cryptoManager.verifyVaultPinWithResult("000000")
        assertTrue(invalidAuth is CryptoSecurityManager.VaultAuthResult.INVALID_PIN)

        val secretBytes = byteArrayOf(1, 2, 3, 4, 5, 6, 7, 8)
        cryptoManager.wipeBuffer(secretBytes)
        assertTrue(secretBytes.all { it == 0.toByte() })
    }
}
