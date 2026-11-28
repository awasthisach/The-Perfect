package com.vvf.smartmanager

import com.vvf.smartmanager.core.background.workers.FileIndexingOutcome
import com.vvf.smartmanager.core.data.storage.PrimaryStorageMutationLock
import com.vvf.smartmanager.core.database.dao.FileDao
import com.vvf.smartmanager.core.database.dao.SearchFtsDao
import com.vvf.smartmanager.core.database.dao.SourceFileRecordDao
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import com.vvf.smartmanager.core.database.model.SourceFileRecordEntity
import com.vvf.smartmanager.core.model.FileSourceType
import com.vvf.smartmanager.core.model.IndexedSourceAccessState
import com.vvf.smartmanager.core.model.IndexedSourceFile
import com.vvf.smartmanager.core.model.SourceFileIdentity
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
    sourceFileRecordDao: SourceFileRecordDao,
    isCurrentFile: (String) -> Boolean = { path -> File(path).exists() && File(path).isFile },
    onWarning: (String) -> Unit,
    onInfo: (String) -> Unit
): FileIndexingOutcome = PrimaryStorageMutationLock.mutex.withLock {
    val scannedItems = scan.items.filter { item -> isCurrentFile(item.path) }
    val existingByPath = fileDao.getIndexedPathSnapshot().associateBy { it.path }
    val scannedPaths = scannedItems.map { it.path }.toHashSet()
    val sourceRecords = scannedItems.map { item ->
        SourceFileRecordEntity.from(
            IndexedSourceFile(
                identity = SourceFileIdentity(
                    sourceType = FileSourceType.INTERNAL_STORAGE,
                    sourceInstanceId = "primary",
                    sourceFileId = item.path
                ),
                displayName = item.name,
                sizeBytes = item.sizeBytes,
                mimeType = item.mimeType,
                modifiedAtEpochMillis = item.lastModified.coerceAtLeast(0L),
                accessState = IndexedSourceAccessState.AVAILABLE
            )
        )
    }
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
    if (sourceRecords.isNotEmpty()) {
        sourceRecords.chunked(400).forEach { chunk -> sourceFileRecordDao.upsertAll(chunk) }
    }
    // Only a complete, uncapped inventory may mark previously indexed source records missing.
    // Preserve their metadata and identity so a temporarily unavailable source can be recovered.
    if (shouldPruneStalePrimaryIndexRows(scan.items.size, scan.isComplete)) {
        val currentIdentityKeys = sourceRecords.map { it.identityKey }.toHashSet()
        sourceFileRecordDao.getBySourceInstance(
            FileSourceType.INTERNAL_STORAGE.name, "primary"
        ).filter { it.identityKey !in currentIdentityKeys }.forEach { stale ->
            sourceFileRecordDao.updateAccessState(
                stale.identityKey, IndexedSourceAccessState.SOURCE_REMOVED.name
            )
        }
    }
    if (metadata.isNotEmpty() || staleRemoved > 0) {
        searchFtsDao.rebuildFtsIndex()
    }
    FileIndexingOutcome.Completed(metadata.size)
}
