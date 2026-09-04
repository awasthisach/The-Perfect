package com.vvf.smartmanager.core.background.workers

/**
 * Process-local bridge: WorkManager workers cannot reach VVFApplication fields directly.
 * Configure once from Application.onCreate with ArchiveService + optional upload path.
 */
sealed interface CloudBackupOutcome {
    data class Completed(
        val backupId: String,
        val sizeBytes: Long,
        val uploaded: Boolean
    ) : CloudBackupOutcome

    data class RetryableFailure(val reason: String) : CloudBackupOutcome
    data class PermanentFailure(val reason: String) : CloudBackupOutcome
    data class NotConfigured(val reason: String) : CloudBackupOutcome
}

object CloudBackupRuntime {
    @Volatile
    private var runner: (suspend (includeVault: Boolean) -> CloudBackupOutcome)? = null

    fun configure(realRunner: suspend (includeVault: Boolean) -> CloudBackupOutcome) {
        runner = realRunner
    }

    suspend fun run(includeVault: Boolean): CloudBackupOutcome {
        val configured = runner
            ?: return CloudBackupOutcome.NotConfigured("Cloud backup dependencies are not ready")
        return configured(includeVault)
    }
}
