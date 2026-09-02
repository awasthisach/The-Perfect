package com.vvf.smartmanager

import android.app.Application
import android.util.Log
import com.vvf.smartmanager.core.background.BackgroundSyncManager
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveServiceImpl
import com.vvf.smartmanager.core.data.backup.InjectedVaultSnapshotSource
import com.vvf.smartmanager.core.data.backup.ReadOnlyDatabaseSnapshotSource
import com.vvf.smartmanager.core.data.repository.OfflineFileManagerRepository
import com.vvf.smartmanager.core.data.repository.OfflineSearchRepository
import com.vvf.smartmanager.core.data.repository.SecureVaultRepository
import com.vvf.smartmanager.core.data.storage.StorageManager
import com.vvf.smartmanager.core.database.VVFDatabase
import com.vvf.smartmanager.core.domain.AiIntelligenceUseCase
import com.vvf.smartmanager.core.domain.backup.ArchiveService
import com.vvf.smartmanager.core.domain.CloudSyncUseCase
import com.vvf.smartmanager.core.domain.DeleteVaultItemUseCase
import com.vvf.smartmanager.core.domain.DuplicateCleanerUseCase
import com.vvf.smartmanager.core.domain.ExportVaultItemUseCase
import com.vvf.smartmanager.core.domain.ExtractTextUseCase
import com.vvf.smartmanager.core.domain.FileOperationsUseCase
import com.vvf.smartmanager.core.domain.GetCategorizedFilesUseCase
import com.vvf.smartmanager.core.domain.GetDirectoryFilesUseCase
import com.vvf.smartmanager.core.domain.GetStorageOverviewUseCase
import com.vvf.smartmanager.core.domain.GetVaultItemsUseCase
import com.vvf.smartmanager.core.domain.IndexOcrTextUseCase
import com.vvf.smartmanager.core.domain.JunkCleanerUseCase
import com.vvf.smartmanager.core.domain.LockFileInVaultUseCase
import com.vvf.smartmanager.core.domain.OcrIndexingService
import com.vvf.smartmanager.core.domain.RecycleBinUseCase
import com.vvf.smartmanager.core.domain.RestoreVaultItemUseCase
import com.vvf.smartmanager.core.domain.SaveOcrTextUseCase
import com.vvf.smartmanager.core.domain.SearchFilesUseCase
import com.vvf.smartmanager.core.domain.SearchHistoryUseCase
import com.vvf.smartmanager.core.domain.SemanticSearchUseCase
import com.vvf.smartmanager.core.domain.TagManagementUseCase
import com.vvf.smartmanager.core.domain.VaultAuthUseCase
import com.vvf.smartmanager.core.model.CloudProviderType
import com.vvf.smartmanager.core.plugin.spi.ISemanticSearchEngine
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import com.vvf.smartmanager.plugin.clouddrivers.DropboxDriverImpl
import com.vvf.smartmanager.plugin.clouddrivers.LocalNasDriverImpl
import com.vvf.smartmanager.plugin.clouddrivers.NextCloudDriverImpl
import com.vvf.smartmanager.plugin.clouddrivers.OneDriveDriverImpl
import com.vvf.smartmanager.plugin.clouddrivers.S3StorageDriverImpl
import com.vvf.smartmanager.plugin.ocr.OcrPluginImpl
import com.vvf.smartmanager.plugin.semantic.SemanticSearchPluginImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.io.File

/**
 * Application-level composition for VVF Smart Manager.
 * Storage listing is gated by [AppCompositionRoot] permission policy (PROD-007).
 * Cloud restore uses fail-closed pipeline wiring (PROD-003).
 */
class VVFApplication : Application() {

    companion object {
        private const val TAG = "VVFApplication"
    }

    lateinit var cryptoSecurityManager: CryptoSecurityManager
    lateinit var database: VVFDatabase
    lateinit var storageManager: StorageManager
    lateinit var fileManagerRepository: OfflineFileManagerRepository
    lateinit var vaultRepository: SecureVaultRepository
    lateinit var searchRepository: OfflineSearchRepository
    lateinit var getStorageOverviewUseCase: GetStorageOverviewUseCase
    lateinit var getDirectoryFilesUseCase: GetDirectoryFilesUseCase
    lateinit var getCategorizedFilesUseCase: GetCategorizedFilesUseCase
    lateinit var fileOperationsUseCase: FileOperationsUseCase
    lateinit var recycleBinUseCase: RecycleBinUseCase
    lateinit var duplicateCleanerUseCase: DuplicateCleanerUseCase
    lateinit var junkCleanerUseCase: JunkCleanerUseCase
    lateinit var getVaultItemsUseCase: GetVaultItemsUseCase
    lateinit var lockFileInVaultUseCase: LockFileInVaultUseCase
    lateinit var restoreVaultItemUseCase: RestoreVaultItemUseCase
    lateinit var exportVaultItemUseCase: ExportVaultItemUseCase
    lateinit var deleteVaultItemUseCase: DeleteVaultItemUseCase
    lateinit var vaultAuthUseCase: VaultAuthUseCase
    lateinit var searchFilesUseCase: SearchFilesUseCase
    lateinit var searchHistoryUseCase: SearchHistoryUseCase
    lateinit var tagManagementUseCase: TagManagementUseCase
    lateinit var extractTextUseCase: ExtractTextUseCase
    lateinit var indexOcrTextUseCase: IndexOcrTextUseCase
    lateinit var saveOcrTextUseCase: SaveOcrTextUseCase
    lateinit var ocrIndexingService: OcrIndexingService
    lateinit var semanticSearchUseCase: SemanticSearchUseCase
    lateinit var aiIntelligenceUseCase: AiIntelligenceUseCase
    lateinit var googleDriveService: GoogleDriveService
    lateinit var cloudSyncUseCase: CloudSyncUseCase
    lateinit var backgroundSyncManager: BackgroundSyncManager
    lateinit var ocrPlugin: OcrPluginImpl
    lateinit var semanticSearchPlugin: ISemanticSearchEngine

    override fun onCreate() {
        super.onCreate()
        cryptoSecurityManager = CryptoSecurityManager(this)
        val passphrase = cryptoSecurityManager.getOrCreateDatabasePassphrase()
        try {
            database = VVFDatabase.build(this, passphrase)
        } catch (e: Exception) {
            throw IllegalStateException(
                "Secure database initialization failed: Failed to construct encrypted SQLCipher database",
                e
            )
        } finally {
            cryptoSecurityManager.wipeBuffer(passphrase)
        }

        storageManager = StorageManager(this, database.fileDao())
        fileManagerRepository = AppCompositionRoot.offlineFileManagerRepository(
            context = this,
            storageManager = storageManager,
            fileDao = database.fileDao(),
            searchFtsDao = database.searchFtsDao()
        )
        val vaultDir = File(filesDir, "vvf_vault_encrypted")
        vaultRepository = SecureVaultRepository(
            vaultDao = database.vaultDao(),
            cryptoManager = cryptoSecurityManager,
            vaultDirectory = vaultDir,
            vaultJournalDao = database.vaultJournalDao()
        )
        searchRepository = OfflineSearchRepository(
            context = this,
            searchFtsDao = database.searchFtsDao(),
            fileDao = database.fileDao(),
            storageManager = storageManager
        )
        ocrPlugin = OcrPluginImpl(this)
        getStorageOverviewUseCase = GetStorageOverviewUseCase(fileManagerRepository)
        getDirectoryFilesUseCase = GetDirectoryFilesUseCase(fileManagerRepository)
        getCategorizedFilesUseCase = GetCategorizedFilesUseCase(fileManagerRepository)
        fileOperationsUseCase = FileOperationsUseCase(fileManagerRepository)
        recycleBinUseCase = RecycleBinUseCase(fileManagerRepository)
        duplicateCleanerUseCase = DuplicateCleanerUseCase(fileManagerRepository)
        junkCleanerUseCase = JunkCleanerUseCase(fileManagerRepository)
        getVaultItemsUseCase = GetVaultItemsUseCase(vaultRepository)
        lockFileInVaultUseCase = LockFileInVaultUseCase(vaultRepository)
        restoreVaultItemUseCase = RestoreVaultItemUseCase(vaultRepository)
        exportVaultItemUseCase = ExportVaultItemUseCase(vaultRepository)
        deleteVaultItemUseCase = DeleteVaultItemUseCase(vaultRepository)
        vaultAuthUseCase = VaultAuthUseCase(vaultRepository)
        searchFilesUseCase = SearchFilesUseCase(searchRepository)
        searchHistoryUseCase = SearchHistoryUseCase(searchRepository)
        tagManagementUseCase = TagManagementUseCase(searchRepository)
        extractTextUseCase = ExtractTextUseCase(ocrPlugin)
        indexOcrTextUseCase = IndexOcrTextUseCase(searchRepository)
        saveOcrTextUseCase = SaveOcrTextUseCase(fileManagerRepository)
        ocrIndexingService = OcrIndexingService(
            searchRepository = searchRepository,
            indexOcrTextUseCase = indexOcrTextUseCase
        )
        semanticSearchPlugin = SemanticSearchPluginImpl()
        semanticSearchUseCase = SemanticSearchUseCase(
            semanticPlugin = semanticSearchPlugin,
            searchRepository = searchRepository,
            fileManagerRepository = fileManagerRepository
        )
        aiIntelligenceUseCase = AiIntelligenceUseCase(
            semanticPlugin = semanticSearchPlugin,
            fileManagerRepository = fileManagerRepository,
            searchRepository = searchRepository
        )
        googleDriveService = GoogleDriveServiceImpl(this)
        val cloudDrivers = mapOf(
            CloudProviderType.ONE_DRIVE to OneDriveDriverImpl(),
            CloudProviderType.DROPBOX to DropboxDriverImpl(),
            CloudProviderType.NEXTCLOUD to NextCloudDriverImpl(),
            CloudProviderType.AWS_S3 to S3StorageDriverImpl(),
            CloudProviderType.LOCAL_NAS to LocalNasDriverImpl()
        )
        val archiveService = ArchiveService(
            cacheDir = cacheDir,
            snapshotSources = listOf(
                ReadOnlyDatabaseSnapshotSource(File(filesDir, VVFDatabase.DATABASE_NAME)),
                InjectedVaultSnapshotSource(vaultDir)
            ),
            cryptoSecurityManager = cryptoSecurityManager
        )
        cloudSyncUseCase = AppCompositionRoot.cloudSyncUseCase(
            context = this,
            googleDriveService = googleDriveService,
            pluginDrivers = cloudDrivers,
            archiveService = archiveService,
            cryptoSecurityManager = cryptoSecurityManager,
            vaultDir = vaultDir,
            databaseName = VVFDatabase.DATABASE_NAME
        )
        backgroundSyncManager = BackgroundSyncManager(this)
        val bgExceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Background sync scheduling failed safely", throwable)
        }
        CoroutineScope(SupervisorJob() + Dispatchers.IO + bgExceptionHandler).launch {
            try {
                backgroundSyncManager.schedulePeriodicIndexing(intervalHours = 6L)
                backgroundSyncManager.schedulePeriodicJunkScan(intervalHours = 12L)
            } catch (e: Throwable) {
                Log.e(TAG, "Background sync scheduling failed", e)
            }
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            try {
                ocrPlugin.cancelOngoing()
            } catch (e: Throwable) {
                Log.w(TAG, "OCR cancel on trim failed", e)
            }
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            ocrPlugin.cancelOngoing()
        } catch (e: Throwable) {
            Log.w(TAG, "OCR cancel on low memory failed", e)
        }
    }
}
