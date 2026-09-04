package com.vvf.smartmanager.core.background.workers

/**
 * App-process bridge for the real indexer. WorkManager creates workers with only Context and
 * WorkerParameters, while database and storage objects are composed by VVFApplication.
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

    fun configure(realIndexer: suspend () -> FileIndexingOutcome) {
        indexer = realIndexer
    }

    suspend fun index(): FileIndexingOutcome {
        val configuredIndexer = indexer
            ?: return FileIndexingOutcome.RetryableFailure("Indexer dependencies are not ready")
        return configuredIndexer()
    }
}
