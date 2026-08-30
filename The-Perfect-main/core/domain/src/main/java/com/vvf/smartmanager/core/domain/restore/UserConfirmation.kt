package com.vvf.smartmanager.core.domain.restore

import com.vvf.smartmanager.core.model.CloudBackupInfo

/** Provides explicit user approval before any restore can be applied. */
interface UserConfirmationProvider {
    suspend fun requestConfirmation(backupInfo: CloudBackupInfo): UserConfirmation
}

sealed class UserConfirmation {
    data class Confirmed(
        val backupInfo: CloudBackupInfo,
        val timestamp: Long
    ) : UserConfirmation()

    data class Cancelled(
        val reason: String
    ) : UserConfirmation()

    data class DryRun(
        val backupInfo: CloudBackupInfo
    ) : UserConfirmation()
}
