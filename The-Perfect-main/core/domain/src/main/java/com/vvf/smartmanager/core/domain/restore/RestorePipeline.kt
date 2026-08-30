package com.vvf.smartmanager.core.domain.restore

import com.vvf.smartmanager.core.model.CloudBackupInfo

/** Orchestrates confirmation, download, verification, decryption, and eventual restore. */
interface RestorePipeline {
    suspend fun restore(
        remoteBackupId: String,
        expectedChecksum: String?
    ): Result<RestoreResult>

    suspend fun dryRun(
        remoteBackupId: String,
        expectedChecksum: String?
    ): Result<DryRunResult>

    suspend fun rollback(snapshotToken: String): Result<Unit>
}

data class RestoreResult(
    val success: Boolean,
    val restoredFileCount: Int,
    val restoredVaultItemCount: Int,
    val backupInfo: CloudBackupInfo,
    val snapshotToken: String?,
    val message: String,
    val timestamp: Long
)

data class DryRunResult(
    val backupInfo: CloudBackupInfo,
    val fileCount: Int,
    val vaultItemCount: Int,
    val totalSizeBytes: Long,
    val timestamp: Long,
    val message: String
)
