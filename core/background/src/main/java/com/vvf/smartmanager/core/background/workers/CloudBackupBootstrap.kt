package com.vvf.smartmanager.core.background.workers

import com.vvf.smartmanager.core.domain.CloudSyncUseCase
import com.vvf.smartmanager.core.model.CloudProviderType

/**
 * Wires [CloudBackupRuntime] to the real [CloudSyncUseCase.createCloudBackup] pipeline.
 * Call once from Application.onCreate after cloudSyncUseCase is constructed.
 */
object CloudBackupBootstrap {
    fun wire(cloudSyncUseCase: CloudSyncUseCase) {
        CloudBackupRuntime.configure { includeVault ->
            try {
                val result = cloudSyncUseCase.createCloudBackup(
                    providerType = CloudProviderType.GOOGLE_DRIVE,
                    includeVault = includeVault
                )
                result.fold(
                    onSuccess = { info ->
                        CloudBackupOutcome.Completed(
                            backupId = info.backupId,
                            sizeBytes = info.backupSizeBytes,
                            uploaded = true
                        )
                    },
                    onFailure = { err ->
                        val msg = err.message.orEmpty()
                        val lower = msg.lowercase()
                        when {
                            "unavailable" in lower || "not configured" in lower ->
                                CloudBackupOutcome.NotConfigured(msg)
                            "network" in lower || "timeout" in lower || "token" in lower ->
                                CloudBackupOutcome.RetryableFailure(msg)
                            else ->
                                CloudBackupOutcome.PermanentFailure(msg.ifBlank { "backup failed" })
                        }
                    }
                )
            } catch (e: Exception) {
                CloudBackupOutcome.RetryableFailure(e.message ?: "backup error")
            }
        }
    }
}
