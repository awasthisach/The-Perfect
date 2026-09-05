package com.vvf.smartmanager.core.database.dao

/**
 * Lightweight projection used by bulk indexing to preserve favorites/tags without N+1 queries.
 */
data class FilePathSnapshot(
    val path: String,
    val id: Long,
    val isFavorite: Boolean,
    val tags: String,
    val md5Hash: String?,
    val originalPath: String?,
    val deletedTimestamp: Long?,
    val operationState: String
)
