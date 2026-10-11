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

    /** Re-index new files and files whose Drive modifiedTime changed; unknown timestamps fail safe. */
    fun changedOnly(
        files: List<DriveFileDto>,
        indexedModifiedTimes: Map<String, String>
    ): List<DriveFileDto> = files.filter { file ->
        val id = file.id?.takeIf { it.isNotBlank() }
        val modified = file.modifiedTime?.takeIf { it.isNotBlank() }
        id == null || modified == null || indexedModifiedTimes[id] != modified
    }

    /** A missing timestamp must never be treated as proof that indexed content is unchanged. */
    fun isUnchanged(
        currentModifiedTime: Long,
        indexedModifiedTime: Long?,
        currentMd5: String?,
        indexedMd5: String?
    ): Boolean =
        currentModifiedTime > 0L &&
            indexedModifiedTime == currentModifiedTime &&
            currentMd5 == indexedMd5

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
