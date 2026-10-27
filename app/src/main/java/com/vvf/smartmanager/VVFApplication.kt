package com.vvf.smartmanager

import android.app.AlarmManager
import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.os.Process
import android.os.SystemClock
import android.util.Log
import androidx.work.Configuration
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory
import com.vvf.smartmanager.core.background.workers.FileIndexingOutcome
import com.vvf.smartmanager.core.background.workers.FileIndexingRuntime
import com.vvf.smartmanager.core.background.workers.CloudBackupBootstrap
import com.vvf.smartmanager.core.background.workers.JunkScanBootstrap
import com.vvf.smartmanager.core.background.BackgroundSyncManager
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
import com.vvf.smartmanager.core.domain.TagManagementUseCase
import com.vvf.smartmanager.core.domain.VaultAuthUseCase
import com.vvf.smartmanager.core.domain.restore.impl.LocalRestoreApplier
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
    lateinit var cloudSyncUseCase: CloudSyncUseCase
    lateinit var backgroundSyncManager: BackgroundSyncManager
    lateinit var ocrPlugin: OcrPluginImpl
    lateinit var semanticSearchPlugin: ISemanticSearchEngine

    @Volatile
    private var restoreClosedDatabase = false

    @Volatile
    var pendingGoogleDriveSignInCallback: ((Result<String>) -> Unit)? = null

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val settingsPrefs by lazy {
        getSharedPreferences(SETTINGS_PREFS, MODE_PRIVATE)
    }

    fun isAutoIndexOcrEnabled(): Boolean = settingsPrefs.getBoolean(KEY_AUTO_INDEX_OCR, true)

    fun setAutoIndexOcrEnabled(enabled: Boolean) {
        settingsPrefs.edit().putBoolean(KEY_AUTO_INDEX_OCR, enabled).apply()
    }

    fun isOfflineOnlyModeEnabled(): Boolean = settingsPrefs.getBoolean(KEY_OFFLINE_ONLY_MODE, true)

    fun setOfflineOnlyModeEnabled(enabled: Boolean) {
        settingsPrefs.edit().putBoolean(KEY_OFFLINE_ONLY_MODE, enabled).apply()
    }

    override fun onCreate() {
        super.onCreate()
        cryptoSecurityManager = CryptoSecurityManager(this)
        val jvmUnitTest = CryptoSecurityManager.isJvmUnitTestEnvironment(this)
        if (!jvmUnitTest) {
            LocalRestoreApplier.recoverInterruptedRestore(
                liveDatabaseFile = getDatabasePath(VVFDatabase.DATABASE_NAME),
                liveVaultDir = File(filesDir, "vvf_vault_encrypted"),
                snapshotRoot = File(filesDir, "restore-work/snapshots"),
                cryptoSecurityManager = cryptoSecurityManager
            )
        }
        cleanupStalePlaintextBackupStaging()
        // Play Integrity App Check is device/Play-only; skip on JVM unit tests (Robolectric).
        if (!jvmUnitTest) {
            try {
                FirebaseAppCheck.getInstance().apply {
                    installAppCheckProviderFactory(PlayIntegrityAppCheckProviderFactory.getInstance())
                }
            } catch (e: Exception) {
                Log.w(TAG, "Firebase App Check init skipped: ${e.message}")
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
                ReadOnlyDatabaseSnapshotSource(
                    databaseFile = getDatabasePath(VVFDatabase.DATABASE_NAME),
                    beforeSnapshot = {
                        check(::database.isInitialized) {
                            "Database is not initialized; refusing an inconsistent backup snapshot"
                        }
                        database.openHelper.writableDatabase.query("PRAGMA wal_checkpoint(FULL)").use { cursor ->
                            check(cursor.moveToFirst() && cursor.getInt(0) == 0) {
                                "SQLite WAL checkpoint was busy or did not complete"
                            }
                        }
                        Log.i(TAG, "WAL checkpoint before backup DB snapshot")
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
                check(::database.isInitialized) {
                    "Database is not initialized; refusing to mutate live restore targets"
                }
                restoreClosedDatabase = true
                database.close()
                Log.i(TAG, "Closed Room database before restore snapshot")
            },
            cloudSyncDao = database.cloudSyncDao(),
            onRestoreFinished = { restartIfRestoreClosedDatabase() }
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


    /**
     * Remove abandoned app-private plaintext ZIP/staging artifacts left by a prior process death.
     * Do not delete restore-work snapshots here: those may be the only rollback material after a crash.
     */
    private fun cleanupStalePlaintextBackupStaging() {
        cacheDir.listFiles().orEmpty()
            .filter { file ->
                file.name.startsWith("archive-staging-") ||
                    (file.name.startsWith("backup-") && file.name.endsWith(".zip"))
            }
            .forEach(::deleteStalePlaintextArtifact)

        // Restore staging can contain decrypted payloads or temporary GCM plaintext files.
        // Preserve the separate snapshots directory: it may contain the only rollback copy.
        File(filesDir, "restore-work").listFiles().orEmpty()
            .filter { it.name.startsWith("restore-") || it.name.startsWith("dry-run-") }
            .forEach(::deleteStalePlaintextArtifact)
    }

    private fun deleteStalePlaintextArtifact(file: File) {
        val removed = if (file.isDirectory) file.deleteRecursively() else file.delete()
        if (!removed && file.exists()) {
            Log.w(TAG, "Unable to remove stale plaintext backup staging: ${file.name}")
        }
    }


    /**
     * Room and repositories are bound to the old database instance. Once restore closes Room,
     * relaunch the app after the transaction (success or rollback) so every DAO is rebound to
     * the database/key state on disk instead of leaving the current process half-alive.
     */
    private fun restartIfRestoreClosedDatabase() {
        if (!restoreClosedDatabase) return
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
            ?: throw IllegalStateException("Cannot safely restart after database restore: launch intent is missing")
        launchIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        val pendingIntent = PendingIntent.getActivity(
            this,
            74031,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarmManager = getSystemService(ALARM_SERVICE) as? AlarmManager
            ?: throw IllegalStateException("Cannot safely restart after database restore: AlarmManager unavailable")
        alarmManager.set(
            AlarmManager.ELAPSED_REALTIME_WAKEUP,
            SystemClock.elapsedRealtime() + 1_500L,
            pendingIntent
        )
        Log.w(TAG, "Restarting app to reopen Room against restored database/key state")
        Process.killProcess(Process.myPid())
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
