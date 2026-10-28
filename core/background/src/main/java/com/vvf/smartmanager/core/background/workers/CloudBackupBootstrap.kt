package com.vvf.smartmanager.core.background.workers

import com.vvf.smartmanager.core.domain.CloudSyncUseCase

/**
 * Wires the cloud backup runtime after application startup.
 *
 * Scheduled backups are intentionally fail-closed for now: the current archive
 * format is encrypted with a device-bound Keystore key and this worker has no
 * safe passphrase/key-wrapping input. Do not upload a backup that appears
 * portable but cannot be restored on another device. Re-enable only after the
 * portable envelope includes rewrapped database/vault keys and has end-to-end
 * restore coverage.
 */
object CloudBackupBootstrap {
    @Suppress("UNUSED_PARAMETER")
    fun wire(cloudSyncUseCase: CloudSyncUseCase) {
        CloudBackupRuntime.configure {
            CloudBackupOutcome.PermanentFailure(
                "Scheduled backups are disabled until portable backup key wrapping and restore are implemented"
            )
        }
    }
}
