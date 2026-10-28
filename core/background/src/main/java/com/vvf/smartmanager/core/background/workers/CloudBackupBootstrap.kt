package com.vvf.smartmanager.core.background.workers

import com.vvf.smartmanager.core.domain.CloudSyncUseCase

/**
 * Wires the cloud backup runtime after application startup.
 *
 * Scheduled backups remain fail-closed because a worker cannot securely obtain the
 * user-entered portable-backup passphrase or the current real vault PIN. Never put either
 * secret in WorkManager input data, preferences, logs, or cloud metadata. Re-enable only
 * after a secure user-mediated credential flow and scheduled-backup restore coverage exist.
 */
object CloudBackupBootstrap {
    @Suppress("UNUSED_PARAMETER")
    fun wire(cloudSyncUseCase: CloudSyncUseCase) {
        CloudBackupRuntime.configure {
            CloudBackupOutcome.PermanentFailure(
                "Scheduled backups are disabled until secure user-mediated backup credentials are available"
            )
        }
    }
}
