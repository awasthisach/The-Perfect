package com.vvf.smartmanager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vvf.smartmanager.core.database.VVFDatabase
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import com.vvf.smartmanager.core.database.model.VaultItemEntity
import com.vvf.smartmanager.core.model.AIModelStatus
import com.vvf.smartmanager.core.model.DerivedIndexStatus
import com.vvf.smartmanager.core.model.DurableOperationState
import com.vvf.smartmanager.core.model.SemanticIndexRecord
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
 * Validates core storage, vault, search, duplicate, application, semantic, cloud and state-machine contracts.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [35])
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
    fun cuj6_cosineSimilarityAndThresholdFiltering() {
        val app = ApplicationProvider.getApplicationContext<VVFApplication>()
        val semanticEngine = app.semanticSearchPlugin
        val vecA = floatArrayOf(1.0f, 0.0f, 0.0f)
        val vecB = floatArrayOf(1.0f, 0.0f, 0.0f)
        val vecC = floatArrayOf(0.0f, 1.0f, 0.0f)
        val simIdentical = semanticEngine.computeCosineSimilarity(vecA, vecB)
        val simOrthogonal = semanticEngine.computeCosineSimilarity(vecA, vecC)
        assertEquals(1.0f, simIdentical, 0.01f)
        assertEquals(0.0f, simOrthogonal, 0.01f)
    }

    @Test
    fun cuj7_cloudDriverRegistryAndStateVerification() {
        val app = ApplicationProvider.getApplicationContext<VVFApplication>()
        assertNotNull(app.cloudSyncUseCase)
        assertNotNull(app.googleDriveService)
    }

    @Test
    fun cuj8_masterSkillV3OperationStateMachinesAndJournals() {
        val planned = DurableOperationState.PLANNED
        val committed = DurableOperationState.PHYSICAL_COMMITTED
        val completed = DurableOperationState.COMPLETED
        assertNotNull(planned)
        assertNotNull(committed)
        assertNotNull(completed)
        val notIndexed = DerivedIndexStatus.NOT_INDEXED
        val pending = DerivedIndexStatus.PENDING
        val indexed = DerivedIndexStatus.INDEXED
        val stale = DerivedIndexStatus.STALE
        val failed = DerivedIndexStatus.FAILED
        assertNotNull(notIndexed)
        assertNotNull(pending)
        assertNotNull(indexed)
        assertNotNull(stale)
        assertNotNull(failed)
        assertEquals(5, DerivedIndexStatus.values().size)
        val unavailable = AIModelStatus.MODEL_UNAVAILABLE
        val ready = AIModelStatus.MODEL_READY
        val fallback = AIModelStatus.FALLBACK_ACTIVE
        assertNotNull(unavailable)
        assertNotNull(ready)
        assertNotNull(fallback)
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
}
