package com.vvf.smartmanager

import android.content.Context
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.data.permission.StoragePermissionGate
import com.vvf.smartmanager.core.data.repository.OfflineFileManagerRepository
import com.vvf.smartmanager.core.data.storage.StorageManager
import com.vvf.smartmanager.core.database.dao.CloudSyncDao
import com.vvf.smartmanager.core.database.dao.FileDao
import com.vvf.smartmanager.core.database.dao.SearchFtsDao
import com.vvf.smartmanager.core.domain.CloudSyncUseCase
import com.vvf.smartmanager.core.domain.RestoreOutcomeStore
import com.vvf.smartmanager.core.domain.backup.ArchiveService
import com.vvf.smartmanager.core.domain.restore.FailClosedRestorePipeline
import com.vvf.smartmanager.core.domain.restore.impl.CryptoBackupDecryptor
import com.vvf.smartmanager.core.domain.restore.impl.GoogleDriveBackupDownloader
import com.vvf.smartmanager.core.domain.restore.impl.LocalRestoreApplier
import com.vvf.smartmanager.core.domain.restore.impl.Sha256BackupVerifier
import com.vvf.smartmanager.core.model.CloudProviderType
import com.vvf.smartmanager.core.plugin.spi.CloudDriverSPI
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import java.io.File

/** Composition root for permission-gated storage and fail-closed cloud restore. */
object AppCompositionRoot {
    private const val KEY_PENDING_RESTORE_OUTCOME = "pending_restore_outcome"
    fun storagePermissionGate(context: Context): StoragePermissionGate =
        StoragePermissionGate(context)

    fun offlineFileManagerRepository(
        context: Context,
        storageManager: StorageManager,
        fileDao: FileDao,
        searchFtsDao: SearchFtsDao
    ): OfflineFileManagerRepository = OfflineFileManagerRepository(
        storageManager = storageManager,
        fileDao = fileDao,
        searchFtsDao = searchFtsDao,
        storagePermissionGate = storagePermissionGate(context)
    )

    fun cloudSyncUseCase(
        context: Context,
        googleDriveService: GoogleDriveService,
        pluginDrivers: Map<CloudProviderType, CloudDriverSPI>,
        archiveService: ArchiveService,
        cryptoSecurityManager: CryptoSecurityManager,
        vaultDir: File,
        databaseName: String,
        beforeRestoreApply: (() -> Unit)? = null,
        cloudSyncDao: CloudSyncDao? = null,
        onRestoreFinished: (() -> Unit)? = null
    ): CloudSyncUseCase {
        val restoreWorkingDir = File(context.filesDir, "restore-work")
        val restorePipeline = FailClosedRestorePipeline(
            workingDir = restoreWorkingDir,
            downloader = GoogleDriveBackupDownloader(
                driveService = googleDriveService,
                downloadDir = File(restoreWorkingDir, "downloads")
            ),
            verifier = Sha256BackupVerifier(),
            decryptor = CryptoBackupDecryptor(cryptoSecurityManager),
            applier = LocalRestoreApplier(
                liveDatabaseFile = context.getDatabasePath(databaseName),
                liveVaultDir = vaultDir,
                snapshotRoot = File(restoreWorkingDir, "snapshots"),
                beforeApply = beforeRestoreApply,
                cryptoSecurityManager = cryptoSecurityManager
            )
        )
        return CloudSyncUseCase(
            googleDriveService = googleDriveService,
            pluginDrivers = pluginDrivers,
            archiveService = archiveService,
            restorePipeline = restorePipeline,
            cloudSyncDao = cloudSyncDao,
            onRestoreFinished = onRestoreFinished,
            restoreOutcomeStore = object : RestoreOutcomeStore {
                private val preferences = context.getSharedPreferences("vvf_app_settings", Context.MODE_PRIVATE)

                override fun save(message: String) {
                    check(preferences.edit().putString(KEY_PENDING_RESTORE_OUTCOME, message).commit()) {
                        "Unable to persist restore outcome before process restart"
                    }
                }

                override fun consume(): String? {
                    val message = preferences.getString(KEY_PENDING_RESTORE_OUTCOME, null) ?: return null
                    check(preferences.edit().remove(KEY_PENDING_RESTORE_OUTCOME).commit()) {
                        "Unable to clear consumed restore outcome"
                    }
                    return message
                }
            }
        )
    }
}
