package com.vvf.smartmanager

import com.vvf.smartmanager.core.background.workers.FileIndexingOutcome
import com.vvf.smartmanager.core.data.storage.PrimaryStorageMutationLock
import com.vvf.smartmanager.core.database.dao.FileDao
import com.vvf.smartmanager.core.database.dao.SearchFtsDao
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import com.vvf.smartmanager.core.data.storage.PrimaryStorageScanResult
import java.io.File
import kotlinx.coroutines.sync.withLock

/**
 * Applies a storage scan to Room under the same lock used by destructive vault source removal.
 * The scan itself happens before the lock; paths are revalidated while holding it so a stale
 * pre-vault snapshot cannot recreate searchable metadata for a shredded original.
 */
internal suspend fun reconcilePrimaryStorageIndex(
    scan: PrimaryStorageScanResult,
    fileDao: FileDao,
    searchFtsDao: SearchFtsDao,
    isCurrentFile: (String) -> Boolean = { path -> File(path).exists() && File(path).isFile },
    onWarning: (String) -> Unit = {},
    onInfo: (String) -> Unit = {}
): FileIndexingOutcome = PrimaryStorageMutationLock.mutex.withLock {
    val scannedItems = scan.items.filter { item -> isCurrentFile(item.path) }
    val existingByPath = fileDao.getIndexedPathSnapshot().associateBy { it.path }
    val scannedPaths = scannedItems.map { it.path }.toHashSet()
    val metadata = scannedItems.map { item ->
        val existing = existingByPath[item.path]
        FileMetadataEntity(
            id = existing?.id ?: 0L,
            path = item.path,
            name = item.name,
            parentPath = File(item.path).parent.orEmpty(),
            sizeBytes = item.sizeBytes,
            mimeType = item.mimeType ?: "inode/directory",
            isDirectory = item.isDirectory,
            modifiedDate = item.lastModified,
            isFavorite = existing?.isFavorite ?: false,
            isTrash = existing?.isTrash ?: false,
            originalPath = existing?.originalPath,
            deletedTimestamp = existing?.deletedTimestamp,
            tags = existing?.tags.orEmpty(),
            md5Hash = existing?.md5Hash,
            operationState = existing?.operationState ?: "IDLE"
        )
    }

    // Use the original scan count, not the post-revalidation count. A capped scan remains
    // potentially truncated even if some paths disappear before reconciliation.
    val stalePaths = if (
        shouldPruneStalePrimaryIndexRows(
            scan.items.size,
            scanCompleted = scan.isComplete
        )
    ) {
        existingByPath.keys.filter { path -> path !in scannedPaths }
    } else {
        onWarning(
            "Primary storage scan was incomplete or reached its $PRIMARY_STORAGE_INDEX_LIMIT item cap; preserving stale index rows"
        )
        emptyList()
    }

    var staleRemoved = 0
    if (stalePaths.isNotEmpty()) {
        stalePaths.chunked(400).forEach { chunk -> fileDao.deleteStaleByPaths(chunk) }
        staleRemoved = stalePaths.size
        onInfo("Removed $staleRemoved stale index row(s)")
    }
    if (metadata.isNotEmpty()) {
        metadata.chunked(400).forEach { chunk -> fileDao.insertAllPreservingLatestMetadata(chunk) }
    }
    if (metadata.isNotEmpty() || staleRemoved > 0) {
        searchFtsDao.rebuildFtsIndex()
    }
    FileIndexingOutcome.Completed(metadata.size)
}
