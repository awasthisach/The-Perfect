package com.vvf.smartmanager.core.database

import androidx.test.core.app.ApplicationProvider
import com.vvf.smartmanager.core.database.dao.CloudSyncDao
import com.vvf.smartmanager.core.database.dao.FileDao
import com.vvf.smartmanager.core.database.dao.SearchFtsDao
import com.vvf.smartmanager.core.database.dao.VaultDao
import com.vvf.smartmanager.core.database.model.CloudSyncEntity
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import com.vvf.smartmanager.core.database.model.VaultItemEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class VVFDatabaseDaoTest {

    private lateinit var database: VVFDatabase
    private lateinit var fileDao: FileDao
    private lateinit var searchFtsDao: SearchFtsDao
    private lateinit var vaultDao: VaultDao
    private lateinit var cloudSyncDao: CloudSyncDao

    @Before
    fun setup() {
        database = VVFDatabase.buildInMemoryDatabase(ApplicationProvider.getApplicationContext())
        fileDao = database.fileDao()
        searchFtsDao = database.searchFtsDao()
        vaultDao = database.vaultDao()
        cloudSyncDao = database.cloudSyncDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun testFileMetadataInsertAndQuery() = runBlocking {
        val entity = FileMetadataEntity(
            path = "/storage/emulated/0/Documents/report.pdf",
            name = "report.pdf",
            parentPath = "/storage/emulated/0/Documents",
            sizeBytes = 1024L,
            mimeType = "application/pdf",
            isDirectory = false,
            modifiedDate = 1000L,
            isFavorite = true,
            tags = "work,important",
            md5Hash = "d41d8cd98f00b204e9800998ecf8427e"
        )

        val id = fileDao.insertOrUpdate(entity)
        assertTrue(id > 0)

        val fetched = fileDao.getByPath("/storage/emulated/0/Documents/report.pdf")
        assertNotNull(fetched)
        assertEquals("report.pdf", fetched?.name)
        assertTrue(fetched?.isFavorite == true)

        val favorites = fileDao.getFavorites().first()
        assertEquals(1, favorites.size)
        assertEquals("report.pdf", favorites[0].name)
    }

    @Test
    fun testLevel1DuplicateDetection() = runBlocking {
        val file1 = FileMetadataEntity(
            path = "/storage/emulated/0/Download/image1.png",
            name = "image1.png",
            parentPath = "/storage/emulated/0/Download",
            sizeBytes = 50000L,
            mimeType = "image/png",
            isDirectory = false,
            modifiedDate = 1000L
        )

        val file2 = FileMetadataEntity(
            path = "/storage/emulated/0/DCIM/image1_copy.png",
            name = "image1_copy.png",
            parentPath = "/storage/emulated/0/DCIM",
            sizeBytes = 50000L,
            mimeType = "image/png",
            isDirectory = false,
            modifiedDate = 1000L
        )

        fileDao.insertOrUpdate(file1)
        fileDao.insertOrUpdate(file2)

        val duplicates = fileDao.findPotentialDuplicateSizes().first()
        assertEquals(1, duplicates.size)
        assertEquals(50000L, duplicates[0].sizeBytes)
        assertEquals(2, duplicates[0].count)
    }

    @Test
    fun testVaultItemInsertAndFetch() = runBlocking {
        val vaultItem = VaultItemEntity(
            id = "test-uuid-1",
            encryptedFileName = "enc_test-uuid-1.vvf",
            originalName = "financial_statement.xlsx",
            originalPath = "/storage/emulated/0/Documents/financial_statement.xlsx",
            mimeType = "application/vnd.ms-excel",
            sizeBytes = 204800L,
            category = "Documents",
            encryptionIv = "AQIDBAUGBwgJCgsMDQ4PEA==",
            notes = "Confidential Q3"
        )

        vaultDao.insertVaultItem(vaultItem)

        val fetched = vaultDao.getVaultItemById("test-uuid-1")
        assertNotNull(fetched)
        assertEquals("financial_statement.xlsx", fetched?.originalName)
        assertEquals("Documents", fetched?.category)
        assertEquals("AQIDBAUGBwgJCgsMDQ4PEA==", fetched?.encryptionIv)

        val allItems = vaultDao.getAllVaultItems().first()
        assertEquals(1, allItems.size)

        vaultDao.deleteById("test-uuid-1")
        val afterDelete = vaultDao.getVaultItemById("test-uuid-1")
        assertNull(afterDelete)
    }

    @Test
    fun testCloudSyncRecord() = runBlocking {
        val record = CloudSyncEntity(
            localPath = "/storage/emulated/0/Documents/contract.pdf",
            remoteFileId = "drive_file_id_999",
            provider = "GDRIVE",
            status = "SYNCED"
        )

        cloudSyncDao.insertOrUpdate(record)

        val fetched = cloudSyncDao.getRecord("/storage/emulated/0/Documents/contract.pdf", "GDRIVE")
        assertNotNull(fetched)
        assertEquals("drive_file_id_999", fetched?.remoteFileId)
        assertEquals("SYNCED", fetched?.status)
    }

    @Test
    fun testSearchFtsDaoFallbackAndTags() = runBlocking {
        val file1 = FileMetadataEntity(
            path = "/storage/emulated/0/Documents/annual_audit_2026.pdf",
            name = "annual_audit_2026.pdf",
            parentPath = "/storage/emulated/0/Documents",
            sizeBytes = 2048000L,
            mimeType = "application/pdf",
            isDirectory = false,
            modifiedDate = 2000L,
            tags = "audit,finance"
        )
        val file2 = FileMetadataEntity(
            path = "/storage/emulated/0/Photos/annual_party.jpg",
            name = "annual_party.jpg",
            parentPath = "/storage/emulated/0/Photos",
            sizeBytes = 4096000L,
            mimeType = "image/jpeg",
            isDirectory = false,
            modifiedDate = 1500L,
            tags = "event,celebration"
        )

        fileDao.insertOrUpdate(file1)
        fileDao.insertOrUpdate(file2)

        // Substring search on filename
        val resultsName = searchFtsDao.searchFilesFallback("audit").first()
        assertEquals(1, resultsName.size)
        assertEquals("annual_audit_2026.pdf", resultsName[0].name)

        // Search by Tag
        val resultsTag = searchFtsDao.searchByTag("finance").first()
        assertEquals(1, resultsTag.size)
        assertEquals("annual_audit_2026.pdf", resultsTag[0].name)

        // Retrieve all unique tags
        val tags = searchFtsDao.getAllTags().first()
        assertTrue(tags.contains("audit,finance"))
        assertTrue(tags.contains("event,celebration"))

        // Update tag by path
        searchFtsDao.updateTagsByPath("/storage/emulated/0/Documents/annual_audit_2026.pdf", "audit,finance,vvf_confidential")
        val updated = searchFtsDao.searchByTag("vvf_confidential").first()
        assertEquals(1, updated.size)
    }
}
