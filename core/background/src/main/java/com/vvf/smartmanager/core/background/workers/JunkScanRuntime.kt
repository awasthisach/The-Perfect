package com.vvf.smartmanager.core.background.workers

/**
 * App-process bridge for junk scan. WorkManager cannot inject domain use cases;
 * VVFApplication configures the real scanner at process start.
 */
sealed interface JunkScanOutcome {
    data class Completed(
        val totalScanned: Int,
        val wastedBytes: Long,
        val junkItemCount: Int
    ) : JunkScanOutcome

    data class RetryableFailure(val reason: String) : JunkScanOutcome
    data class PermanentFailure(val reason: String) : JunkScanOutcome
}

object JunkScanRuntime {
    @Volatile
    private var scanner: (suspend () -> JunkScanOutcome)? = null

    fun configure(realScanner: suspend () -> JunkScanOutcome) {
        scanner = realScanner
    }

    suspend fun scan(): JunkScanOutcome {
        val configured = scanner
            ?: return JunkScanOutcome.RetryableFailure("Junk scanner dependencies are not ready")
        return configured()
    }
}
