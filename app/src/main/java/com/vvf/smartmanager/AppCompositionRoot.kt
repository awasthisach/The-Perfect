package com.vvf.smartmanager

import android.content.Context
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.database.dao.CloudSyncDao
import com.vvf.smartmanager.core.domain.CloudSyncUseCase
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

/** Remaining manual composition root for fail-closed cloud restore. */
object AppCompositionRoot {
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
            onRestoreFinished = onRestoreFinished
        )
    }
}
