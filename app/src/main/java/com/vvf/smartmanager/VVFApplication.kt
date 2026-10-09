package com.vvf.smartmanager

import android.app.Application
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.google.android.gms.auth.api.signin.GoogleSignIn
import androidx.work.Configuration
import com.vvf.smartmanager.core.background.workers.FileIndexingOutcome
import com.vvf.smartmanager.core.background.workers.FileIndexingRuntime
import com.vvf.smartmanager.core.background.workers.CloudBackupBootstrap
import com.vvf.smartmanager.core.background.workers.JunkScanBootstrap
import com.vvf.smartmanager.core.background.BackgroundSyncManager
import com.vvf.smartmanager.core.background.drive.DriveSyncRuntime
import com.vvf.smartmanager.core.background.drive.DriveContentIndexCoordinator
import com.vvf.smartmanager.core.background.drive.DriveContentIndexRuntime
import com.vvf.smartmanager.core.background.drive.DriveContentIndexWorker
import com.vvf.smartmanager.core.background.drive.DriveEmbeddingIndexCoordinator
import com.vvf.smartmanager.core.background.drive.DriveEmbeddingRuntime
import com.vvf.smartmanager.core.background.drive.DriveEmbeddingIndexWorker
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveServiceImpl
import com.vvf.smartmanager.core.data.backup.InjectedVaultSnapshotSource
import com.vvf.smartmanager.core.data.backup.ReadOnlyDatabaseSnapshotSource
import com.vvf.smartmanager.core.data.repository.OfflineFileManagerRepository
import com.vvf.smartmanager.core.data.repository.OfflineSearchRepository
import com.vvf.smartmanager.core.data.repository.SecureVaultRepository
import com.vvf.smartmanager.core.data.permission.StoragePermissionGate
import com.vvf.smartmanager.core.data.storage.StorageManager
import com.vvf.smartmanager.core.database.VVFDatabase
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
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
import com.vvf.smartmanager.core.domain.SearchIndexManagementUseCase
import com.vvf.smartmanager.core.domain.SemanticSearchUseCase
import com.vvf.smartmanager.core.domain.DriveTextExtractor
import com.vvf.smartmanager.core.domain.EmbeddingProvider
import com.vvf.smartmanager.core.domain.DisabledEmbeddingProvider
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
import com.vvf.smartmanager.plugin.semanticsearch.SemanticSearchPluginImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.io.File

class VVFApplication : Application(), Configuration.Provider {

    companion object {
        private const val TAG = "VVFApplication"
        private const val SETTINGS_PREFS = "vvf_app_settings"
        private const val KEY_AUTO_INDEX_OCR = "auto_index_ocr"
        private const val KEY_FULL_CONTENT_INDEX_CONSENT = "full_content_index_consent_v1"
        private const val KEY_EMBEDDING_CONSENT = "embedding_consent_v1"
        private const val KEY_OFFLINE_ONLY_MODE = "offline_only_mode"
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setMinimumLoggingLevel(Log.INFO)
            .build()

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
    lateinit var searchIndexManagementUseCase: SearchIndexManagementUseCase
    lateinit var tagManagementUseCase: TagManagementUseCase
    lateinit var extractTextUseCase: ExtractTextUseCase
    lateinit var indexOcrTextUseCase: IndexOcrTextUseCase
    lateinit var saveOcrTextUseCase: SaveOcrTextUseCase
    lateinit var ocrIndexingService: OcrIndexingService
    lateinit var semanticSearchUseCase: SemanticSearchUseCase
    lateinit var aiIntelligenceUseCase: AiIntelligenceUseCase
    lateinit var googleDriveService: GoogleDriveService
    lateinit var embeddingProvider: EmbeddingProvider
    lateinit var cloudSyncUseCase: CloudSyncUseCase
    lateinit var backgroundSyncManager: BackgroundSyncManager
    lateinit var ocrPlugin: OcrPluginImpl
    lateinit var semanticSearchPlugin: ISemanticSearchEngine

    @Volatile
    var pendingGoogleDriveSignInCallback: ((Result<String>) -> Unit)? = null

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val settingsPrefs by lazy {
        getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)
    }

    fun isEmbeddingConsentGranted(): Boolean =
        settingsPrefs.getBoolean(KEY_EMBEDDING_CONSENT, false)

    fun setEmbeddingConsentGranted(granted: Boolean) {
        settingsPrefs.edit().putBoolean(KEY_EMBEDDING_CONSENT, granted).apply()
        if (!granted) {
            DriveEmbeddingIndexWorker.cancel(this)
            if (::database.isInitialized) {
                applicationScope.launch { database.driveIndexDao().clearAllEmbeddings() }
            }
        }
    }

    fun isFullContentIndexConsentGranted(): Boolean =
        settingsPrefs.getBoolean(KEY_FULL_CONTENT_INDEX_CONSENT, false)

    fun setFullContentIndexConsentGranted(granted: Boolean) {
        settingsPrefs.edit().putBoolean(KEY_FULL_CONTENT_INDEX_CONSENT, granted).apply()
        if (!granted) {
            settingsPrefs.edit().putBoolean(KEY_AUTO_INDEX_OCR, false).apply()
            DriveContentIndexWorker.cancel(this)
            if (::database.isInitialized) {
                applicationScope.launch {
                    database.driveIndexDao().clearOcrIndexedText()
                    DriveContentIndexWorker.enqueue(this@VVFApplication)
                }
            }
        }
    }

    fun isAutoIndexOcrEnabled(): Boolean =
        isFullContentIndexConsentGranted() && settingsPrefs.getBoolean(KEY_AUTO_INDEX_OCR, false)

    fun setAutoIndexOcrEnabled(enabled: Boolean) {
        val effective = enabled && isFullContentIndexConsentGranted()
        settingsPrefs.edit().putBoolean(KEY_AUTO_INDEX_OCR, effective).apply()
        if (::database.isInitialized) {
            applicationScope.launch {
                if (effective) database.driveIndexDao().requeueOcrConsentRequired()
                if (effective) DriveContentIndexWorker.enqueue(this@VVFApplication)
                else {
                    DriveContentIndexWorker.cancel(this@VVFApplication)
                    DriveContentIndexWorker.enqueue(this@VVFApplication)
                }
            }
        }
    }

    fun isOfflineOnlyModeEnabled(): Boolean = settingsPrefs.getBoolean(KEY_OFFLINE_ONLY_MODE, true)

    fun setOfflineOnlyModeEnabled(enabled: Boolean) {
        settingsPrefs.edit().putBoolean(KEY_OFFLINE_ONLY_MODE, enabled).apply()
    }

    override fun onCreate() {
        super.onCreate()
        cryptoSecurityManager = CryptoSecurityManager(this)

        val jvmUnitTest = CryptoSecurityManager.isJvmUnitTestEnvironment(this)
        embeddingProvider = if (jvmUnitTest) {
            DisabledEmbeddingProvider()
        } else {
            runCatching {
                val appCheck = FirebaseAppCheck.getInstance()
                appCheck.installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
                DriveEmbeddingClient(
                    context = this,
                    firebaseAuth = FirebaseAuth.getInstance(),
                    appCheck = appCheck,
                    googleAccountEmail = { runCatching { GoogleSignIn.getLastSignedInAccount(this)?.email }.getOrNull() },
                    consentGranted = { isEmbeddingConsentGranted() }
                )
            }.getOrElse {
                Log.w(TAG, "Firebase App Check is unavailable; neural embedding requests remain disabled.")
                DisabledEmbeddingProvider()
            }
        }
        if (jvmUnitTest) {
            database = VVFDatabase.buildInMemoryDatabase(this)
            Log.i(TAG, "JVM unit-test environment: using in-memory Room database")
        } else {
            // SQLCipher's native core must be loaded before encrypted Room initialization.
            System.loadLibrary("sqlcipher")
            val passphrase = cryptoSecurityManager.getOrCreateDatabasePassphrase()
            try {
                database = VVFDatabase.buildEncryptedDatabase(this, passphrase)
            } catch (e: Exception) {
                throw IllegalStateException(
                    "Secure database initialization failed: Failed to construct encrypted SQLCipher database",
                    e
                )
            } finally {
                cryptoSecurityManager.wipeBuffer(passphrase)
            }
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
            storageManager = storageManager,
            cryptoSecurityManager = cryptoSecurityManager
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
        searchIndexManagementUseCase = SearchIndexManagementUseCase(searchRepository)
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
            fileManagerRepository = fileManagerRepository,
            embeddingConsentGranted = { isEmbeddingConsentGranted() },
            embeddingBackendReady = { false }
        )
        aiIntelligenceUseCase = AiIntelligenceUseCase(
            semanticPlugin = semanticSearchPlugin,
            fileManagerRepository = fileManagerRepository,
            searchRepository = searchRepository,
            embeddingConsentGranted = { isEmbeddingConsentGranted() },
            embeddingBackendReady = { false }
        )
        googleDriveService = GoogleDriveServiceImpl(this)
        val driveIndexDao = database.driveIndexDao()
        DriveSyncRuntime.configure(googleDriveService, driveIndexDao) { FirebaseAuth.getInstance().currentUser?.email }
        DriveContentIndexRuntime.configure(
            DriveContentIndexCoordinator(
                context = this,
                driveService = googleDriveService,
                driveIndexDao = driveIndexDao,
                textExtractor = DriveTextExtractor(this),
                ocrEngine = ocrPlugin,
                fullContentConsentGranted = { isFullContentIndexConsentGranted() },
                autoOcrEnabled = { isAutoIndexOcrEnabled() }
            )
        )
        DriveEmbeddingRuntime.configure(
            DriveEmbeddingIndexCoordinator(
                driveIndexDao = driveIndexDao,
                embeddingProvider = embeddingProvider,
                embeddingConsentGranted = { isEmbeddingConsentGranted() }
            )
        )
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
                ReadOnlyDatabaseSnapshotSource(
                    databaseFile = getDatabasePath(VVFDatabase.DATABASE_NAME),
                    beforeSnapshot = {
                        runCatching {
                            if (::database.isInitialized) {
                                database.openHelper.writableDatabase.execSQL("PRAGMA wal_checkpoint(FULL)")
                                Log.i(TAG, "WAL checkpoint before backup DB snapshot")
                            }
                        }.onFailure { e -> Log.w(TAG, "WAL checkpoint before snapshot failed", e) }
                    }
                ),
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
            databaseName = VVFDatabase.DATABASE_NAME,
            beforeRestoreApply = {
                runCatching {
                    if (::database.isInitialized) {
                        database.close()
                        Log.i(TAG, "Closed Room database before restore apply")
                    }
                }.onFailure { err ->
                    Log.w(TAG, "Room close before restore failed (continuing)", err)
                }
            },
            cloudSyncDao = database.cloudSyncDao()
        )
        backgroundSyncManager = BackgroundSyncManager(this)
        FileIndexingRuntime.configure { indexPrimaryStorageForSearch() }
        CloudBackupBootstrap.wire(cloudSyncUseCase)
        JunkScanBootstrap.wire(junkCleanerUseCase)
        OcrBatchBootstrap.wire(database, ocrPlugin, ocrIndexingService)
        val bgExceptionHandler = kotlinx.coroutines.CoroutineExceptionHandler { _, throwable ->
            Log.e(TAG, "Background sync scheduling failed safely", throwable)
        }
        applicationScope.launch(bgExceptionHandler) {
            try {
                backgroundSyncManager.schedulePeriodicIndexing(intervalHours = 6L)
                backgroundSyncManager.triggerImmediateIndexing()
                backgroundSyncManager.schedulePeriodicJunkScan(intervalHours = 12L)
            } catch (e: Throwable) {
                Log.e(TAG, "Background sync scheduling failed", e)
            }
        }
    }

    private suspend fun indexPrimaryStorageForSearch(): FileIndexingOutcome {
        val access = StoragePermissionGate(this).evaluate()
        if (!access.canBrowsePrimaryTree) {
            return FileIndexingOutcome.PermissionRequired(access.userMessageKey)
        }
        return try {
            val fileDao = database.fileDao()
            val existingByPath = fileDao.getIndexedPathSnapshot().associateBy { it.path }
            val scannedItems = storageManager.collectPrimaryStorageItems()
            val scannedPaths = scannedItems.map { it.path }.toHashSet()
            val metadata = scannedItems.map { item ->
                val existing = existingByPath[item.path]
                FileMetadataEntity(
                    id = existing?.id ?: 0L,
                    path = item.path,
                    name = item.name,
                    parentPath = java.io.File(item.path).parent.orEmpty(),
                    sizeBytes = item.sizeBytes,
                    mimeType = item.mimeType ?: "inode/directory",
                    isDirectory = item.isDirectory,
                    modifiedDate = item.lastModified,
                    isFavorite = existing?.isFavorite ?: false,
                    isTrash = existing?.isTrash ?: false,
                    originalPath = existing?.originalPath,
                    deletedTimestamp = existing?.deletedTimestamp,
                    tags = existing?.tags.orEmpty(),
                    md5Hash = existing?.md5Hash,
                    operationState = existing?.operationState ?: "IDLE"
                )
            }
            val stalePaths = existingByPath.keys.filter { path -> path !in scannedPaths }
            var staleRemoved = 0
            if (stalePaths.isNotEmpty()) {
                stalePaths.chunked(400).forEach { chunk -> fileDao.deleteStaleByPaths(chunk) }
                staleRemoved = stalePaths.size
                Log.i(TAG, "Removed $staleRemoved stale index row(s)")
            }
            if (metadata.isNotEmpty()) {
                metadata.chunked(400).forEach { chunk -> fileDao.insertAll(chunk) }
            }
            if (metadata.isNotEmpty() || staleRemoved > 0) {
                database.searchFtsDao().rebuildFtsIndex()
            }
            FileIndexingOutcome.Completed(metadata.size)
        } catch (securityError: SecurityException) {
            FileIndexingOutcome.PermissionRequired(securityError.message ?: "storage access was revoked")
        } catch (ioError: java.io.IOException) {
            FileIndexingOutcome.RetryableFailure(ioError.message ?: "storage I/O failed")
        } catch (error: Throwable) {
            Log.e(TAG, "Storage indexing failed", error)
            FileIndexingOutcome.PermanentFailure(error.message ?: "unexpected indexing failure")
        }
    }

    override fun onTerminate() {
        applicationScope.cancel()
        super.onTerminate()
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
