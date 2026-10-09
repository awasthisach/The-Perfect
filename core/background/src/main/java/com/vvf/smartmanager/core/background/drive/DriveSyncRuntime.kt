package com.vvf.smartmanager.core.background.drive

import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.database.dao.DriveIndexDao

/**
 * Application composition hook for WorkManager. The worker obtains only typed services/DAOs;
 * credentials are never placed in WorkRequest input data.
 */
object DriveSyncRuntime {
    @Volatile
    private var service: GoogleDriveService? = null

    @Volatile
    private var dao: DriveIndexDao? = null

    @Volatile
    private var accountEmailProvider: (() -> String?)? = null

    fun configure(
        driveService: GoogleDriveService,
        driveIndexDao: DriveIndexDao,
        currentAccountEmail: () -> String?
    ) {
        service = driveService
        dao = driveIndexDao
        accountEmailProvider = currentAccountEmail
    }

    fun coordinatorOrNull(): DriveMetadataSyncCoordinator? {
        val driveService = service ?: return null
        val driveIndexDao = dao ?: return null
        val emailProvider = accountEmailProvider ?: return null
        return DriveMetadataSyncCoordinator(driveService, driveIndexDao, emailProvider)
    }
}
