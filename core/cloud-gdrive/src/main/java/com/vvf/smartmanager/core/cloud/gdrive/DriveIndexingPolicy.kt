package com.vvf.smartmanager.core.cloud.gdrive

/**
 * Pure policy shared by a WorkManager Drive indexer. It deliberately never advances a durable
 * cursor on failure/cancellation; callers persist the returned cursor only after the batch commits.
 */
object DriveIndexingPolicy {
    const val BATCH_SIZE = 50
    const val MAX_FILES = 20_000

    fun <T> batches(items: List<T>): List<List<T>> =
        items.chunked(BATCH_SIZE)

    fun bounded(items: List<DriveFileDto>, maximum: Int = MAX_FILES): List<DriveFileDto> {
        require(maximum in 1..MAX_FILES) { "maximum must be between 1 and $MAX_FILES" }
        return items.take(maximum)
    }

    /** Resume the same page after failure; only adopt a new start cursor after all changes commit. */
    fun cursorAfterBatch(
        currentCursor: String,
        nextPageToken: String?,
        newStartPageToken: String?,
        batchCommitted: Boolean
    ): String {
        require(currentCursor.isNotBlank()) { "current cursor must not be blank" }
        if (!batchCommitted) return currentCursor
        return nextPageToken?.takeIf { it.isNotBlank() }
            ?: newStartPageToken?.takeIf { it.isNotBlank() }
            ?: currentCursor
    }
}
