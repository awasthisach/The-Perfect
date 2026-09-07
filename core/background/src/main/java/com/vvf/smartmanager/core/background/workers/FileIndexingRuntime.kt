package com.vvf.smartmanager.core.background.workers

/**
 * App-process bridge for the real indexer. WorkManager creates workers with only Context and
 * WorkerParameters, while database and storage objects are composed by VVFApplication.
 *
 * **Process model:** VVFApplication implements WorkManager [androidx.work.Configuration.Provider]
 * so workers run in the default app process. Do not set a custom `android:process` on WorkManager
 * components, or this bridge will be null and indexing will retry forever.
 */
sealed interface FileIndexingOutcome {
    data class Completed(val indexedCount: Int) : FileIndexingOutcome
    data class PermissionRequired(val reason: String) : FileIndexingOutcome
    data class RetryableFailure(val reason: String) : FileIndexingOutcome
    data class PermanentFailure(val reason: String) : FileIndexingOutcome
}

object FileIndexingRuntime {
    @Volatile
    private var indexer: (suspend () -> FileIndexingOutcome)? = null

    @Volatile
    private var configured: Boolean = false

    fun configure(realIndexer: suspend () -> FileIndexingOutcome) {
        indexer = realIndexer
        configured = true
    }

    fun isConfigured(): Boolean = configured && indexer != null

    suspend fun index(): FileIndexingOutcome {
        val configuredIndexer = indexer
            ?: return FileIndexingOutcome.RetryableFailure(
                "Indexer dependencies are not ready. " +
                    "Ensure VVFApplication.onCreate configured FileIndexingRuntime " +
                    "and WorkManager runs in the default app process."
            )
        return configuredIndexer()
    }
}
