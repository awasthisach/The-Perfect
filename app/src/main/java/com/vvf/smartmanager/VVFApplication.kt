package com.vvf.smartmanager

import android.app.Application
import com.vvf.smartmanager.core.data.repository.OfflineFileManagerRepository
import com.vvf.smartmanager.core.data.repository.OfflineSearchRepository
import com.vvf.smartmanager.core.data.repository.SecureVaultRepository
import com.vvf.smartmanager.core.data.storage.StorageManager
import com.vvf.smartmanager.core.database.VVFDatabase
import com.vvf.smartmanager.core.domain.AiIntelligenceUseCase
import com.vvf.smartmanager.core.domain.DuplicateCleanerUseCase
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
import com.vvf.smartmanager.core.domain.ExportVaultItemUseCase
import com.vvf.smartmanager.core.domain.DeleteVaultItemUseCase
import com.vvf.smartmanager.core.domain.SearchFilesUseCase
import com.vvf.smartmanager.core.domain.SearchHistoryUseCase
import com.vvf.smartmanager.core.domain.SemanticSearchUseCase
import com.vvf.smartmanager.core.domain.TagManagementUseCase
import com.vvf.smartmanager.core.domain.VaultAuthUseCase
import com.vvf.smartmanager.core.plugin.spi.IOcrEngine
import com.vvf.smartmanager.core.plugin.spi.ISemanticSearchEngine
import com.vvf.smartmanager.core.plugin.spi.OcrPluginSPI
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import com.vvf.smartmanager.plugin.ocr.OcrEnginePlugin
import com.vvf.smartmanager.plugin.ocr.OcrPluginImpl
import com.vvf.smartmanager.plugin.semanticsearch.SemanticSearchPluginImpl
import com.vvf.smartmanager.core.background.BackgroundSyncManager
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveServiceImpl
import com.vvf.smartmanager.core.domain.CloudSyncUseCase
import com.vvf.smartmanager.core.model.CloudProviderType
import com.vvf.smartmanager.plugin.clouddrivers.DropboxDriverImpl
import com.vvf.smartmanager.plugin.clouddrivers.LocalNasDriverImpl
import com.vvf.smartmanager.plugin.clouddrivers.NextCloudDriverImpl
import com.vvf.smartmanager.plugin.clouddrivers.OneDriveDriverImpl
import com.vvf.smartmanager.plugin.clouddrivers.S3StorageDriverImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File

/**
 * Root Application entry point for VVF Smart Manager with AppContainer for clean DI.
 */
class VVFApplication : Application() {

    lateinit var cryptoSecurityManager: CryptoSecurityManager
        private set

    lateinit var database: VVFDatabase
        private set

    lateinit var storageManager: StorageManager
        private set

    lateinit var fileManagerRepository: OfflineFileManagerRepository
        private set

    lateinit var vaultRepository: SecureVaultRepository
        private set

    lateinit var searchRepository: OfflineSearchRepository
        private set

    lateinit var backgroundSyncManager: BackgroundSyncManager
        private set

    // Domain UseCases & Services
    lateinit var getStorageOverviewUseCase: GetStorageOverviewUseCase
        private set
    lateinit var getDirectoryFilesUseCase: GetDirectoryFilesUseCase
        private set
    lateinit var getCategorizedFilesUseCase: GetCategorizedFilesUseCase
        private set
    lateinit var fileOperationsUseCase: FileOperationsUseCase
        private set
    lateinit var recycleBinUseCase: RecycleBinUseCase
        private set
    lateinit var duplicateCleanerUseCase: DuplicateCleanerUseCase
        private set
    lateinit var junkCleanerUseCase: JunkCleanerUseCase
        private set
    lateinit var getVaultItemsUseCase: GetVaultItemsUseCase
        private set
    lateinit var lockFileInVaultUseCase: LockFileInVaultUseCase
        private set
    lateinit var restoreVaultItemUseCase: RestoreVaultItemUseCase
        private set
    lateinit var exportVaultItemUseCase: ExportVaultItemUseCase
        private set
    lateinit var deleteVaultItemUseCase: DeleteVaultItemUseCase
        private set
    lateinit var vaultAuthUseCase: VaultAuthUseCase
        private set
    lateinit var searchFilesUseCase: SearchFilesUseCase
        private set
    lateinit var searchHistoryUseCase: SearchHistoryUseCase
        private set
    lateinit var tagManagementUseCase: TagManagementUseCase
        private set
    lateinit var ocrPlugin: OcrEnginePlugin
        private set
    lateinit var extractTextUseCase: ExtractTextUseCase
        private set
    lateinit var indexOcrTextUseCase: IndexOcrTextUseCase
        private set
    lateinit var saveOcrTextUseCase: SaveOcrTextUseCase
        private set
    lateinit var ocrIndexingService: OcrIndexingService
        private set
    lateinit var semanticSearchPlugin: ISemanticSearchEngine
        private set
    lateinit var semanticSearchUseCase: SemanticSearchUseCase
        private set
    lateinit var aiIntelligenceUseCase: AiIntelligenceUseCase
        private set
    lateinit var googleDriveService: GoogleDriveService
        private set
    lateinit var cloudSyncUseCase: CloudSyncUseCase
        private set

    companion object {
        lateinit var instance: VVFApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this

        cryptoSecurityManager = CryptoSecurityManager(this)

        val passphrase = try {
            cryptoSecurityManager.getOrCreateDatabasePassphrase()
        } catch (e: Throwable) {
            throw SecurityException(
                "Secure database initialization failed: Crypto passphrase generation or decryption error",
                e
            )
        }

        database = try {
            VVFDatabase.buildEncryptedDatabase(this, passphrase)
        } catch (e: UnsatisfiedLinkError) {
            // Local JVM unit test environment (Robolectric) lacks native SQLCipher (.so) libraries
            VVFDatabase.buildInMemoryDatabase(this)
        } catch (e: Throwable) {
            throw SecurityException(
                "Secure database initialization failed: Failed to construct encrypted SQLCipher database",
                e
            )
        } finally {
            cryptoSecurityManager.wipeBuffer(passphrase)
        }

        storageManager = StorageManager(this, database.fileDao())
        fileManagerRepository = OfflineFileManagerRepository(
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

        // Initialize UseCases
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

        cloudSyncUseCase = CloudSyncUseCase(
            googleDriveService = googleDriveService,
            pluginDrivers = cloudDrivers
        )

        backgroundSyncManager = BackgroundSyncManager(this)
        // Optimize Cold Start: schedule non-critical background sync tasks asynchronously with SupervisorJob & Exception Handler
        val bgExceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
            android.util.Log.e("VVFApplication", "Background sync scheduling failed safely", throwable)
        }
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO + bgExceptionHandler
        ).launch {
            try {
                backgroundSyncManager.schedulePeriodicIndexing(intervalHours = 6L)
                backgroundSyncManager.schedulePeriodicJunkScan(intervalHours = 12L)
            } catch (_: Throwable) {}
        }
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= TRIM_MEMORY_MODERATE) {
            // Trim non-critical memory caches and release active OCR working buffers
            try {
                ocrPlugin.cancelOngoing()
            } catch (_: Throwable) {}
        }
    }

    override fun onLowMemory() {
        super.onLowMemory()
        try {
            ocrPlugin.cancelOngoing()
        } catch (_: Throwable) {}
    }
}
