package com.vvf.smartmanager.core.background.drive

import com.vvf.smartmanager.core.cloud.gdrive.DriveChangeTokenInvalidException
import com.vvf.smartmanager.core.cloud.gdrive.DriveMetadataRecord
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.database.dao.DriveIndexDao
import com.vvf.smartmanager.core.database.model.DriveIndexFileEntity
import com.vvf.smartmanager.core.database.model.DriveSyncStateEntity

/**
 * One bounded page of metadata work per invocation. The cursor is committed only after the page
 * is written to Room, so cancellation/process death resumes from the last successful page.
 */
class DriveMetadataSyncCoordinator(
    private val driveService: GoogleDriveService,
    private val driveIndexDao: DriveIndexDao,
    private val currentAccountEmail: () -> String? = { "test@example.com" }
) {
    suspend fun runOneBatch(): Result<Boolean> {
        var state = driveIndexDao.getSyncState() ?: DriveSyncStateEntity()
        val activeEmail = currentAccountEmail()?.trim()?.lowercase()?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("A linked Google/Firebase account is required for Drive sync."))
        if (state.accountEmail != null && state.accountEmail != activeEmail) {
            driveIndexDao.clearUnpinnedDriveRecords()
            driveIndexDao.markPinsFromPreviousAccount()
            state = DriveSyncStateEntity(accountEmail = activeEmail)
            driveIndexDao.saveSyncState(state)
            return Result.success(true)
        }
        if (state.accountEmail == null) {
            state = state.copy(accountEmail = activeEmail)
            driveIndexDao.saveSyncState(state)
        }

        if (state.changeStartPageToken == null && state.fullListPageToken == null && !state.indexingInProgress) {
            val startToken = driveService.getStartPageToken().getOrElse { return Result.failure(it) }
            state = state.copy(
                changeStartPageToken = startToken,
                fullListPageToken = "",
                fullListFilesSeen = 0,
                changePageToken = null,
                indexingInProgress = true,
                listingIncomplete = true,
                lastError = null
            )
            driveIndexDao.saveSyncState(state)
            return Result.success(true)
        }

        if (state.fullListPageToken != null) {
            return processFullListPage(state)
        }

        if (!state.indexingInProgress) {
            val token = state.changeStartPageToken
                ?: return Result.failure(IllegalStateException("Drive sync cursor is missing."))
            driveIndexDao.saveSyncState(
                state.copy(changePageToken = token, indexingInProgress = true, lastError = null)
            )
            return Result.success(true)
        }

        val changeToken = state.changePageToken
            ?: return Result.failure(IllegalStateException("Drive sync state is incomplete; retry a full sync."))
        return processChangesPage(state, changeToken)
    }

    private suspend fun processFullListPage(state: DriveSyncStateEntity): Result<Boolean> {
        val pageToken = state.fullListPageToken?.takeIf { it.isNotEmpty() }
        val page = driveService.listDriveMetadataPage(pageToken = pageToken, pageSize = BATCH_SIZE)
            .getOrElse { error ->
                driveIndexDao.saveSyncState(state.copy(lastError = "Drive metadata page failed; retrying."))
                return Result.failure(error)
            }

        val remaining = (MAX_DRIVE_FILES - state.fullListFilesSeen).coerceAtLeast(0)
        val records = page.files.take(remaining)
        for (record in records) {
            if (record.trashed) markRemoteRemoved(record.fileId) else upsertMetadata(record)
        }
        val seen = state.fullListFilesSeen + records.size
        val capped = seen >= MAX_DRIVE_FILES && page.nextPageToken != null
        val nextState = when {
            page.nextPageToken != null && !capped -> state.copy(
                fullListPageToken = page.nextPageToken,
                fullListFilesSeen = seen,
                listingIncomplete = true,
                lastError = null
            )
            else -> state.copy(
                fullListPageToken = null,
                fullListFilesSeen = seen,
                changePageToken = state.changeStartPageToken,
                indexingInProgress = true,
                listingIncomplete = capped,
                lastSyncAtMs = System.currentTimeMillis(),
                lastError = if (capped) "Drive listing capped at 20,000 files; results may be incomplete." else null
            )
        }
        driveIndexDao.saveSyncState(nextState)
        return Result.success(true)
    }

    private suspend fun processChangesPage(
        state: DriveSyncStateEntity,
        pageToken: String
    ): Result<Boolean> {
        val result = driveService.listChanges(pageToken)
        val page = result.getOrElse { error ->
            if (error is DriveChangeTokenInvalidException) {
                val freshStartToken = driveService.getStartPageToken().getOrElse {
                    driveIndexDao.saveSyncState(state.copy(lastError = "Drive cursor expired; full sync could not start."))
                    return Result.failure(it)
                }
                driveIndexDao.saveSyncState(
                    state.copy(
                        changeStartPageToken = freshStartToken,
                        changePageToken = null,
                        fullListPageToken = "",
                        fullListFilesSeen = 0,
                        indexingInProgress = true,
                        listingIncomplete = true,
                        lastError = null
                    )
                )
                return Result.success(true)
            }
            driveIndexDao.saveSyncState(state.copy(lastError = "Drive incremental sync failed; retrying."))
            return Result.failure(error)
        }

        for (change in page.changes) {
            val record = change.file
            if (change.removed || record?.trashed == true) {
                markRemoteRemoved(change.fileId)
            } else if (record != null) {
                upsertMetadata(record)
            } else {
                driveIndexDao.saveSyncState(state.copy(lastError = "Drive returned an incomplete change record."))
                return Result.failure(IllegalStateException("Drive returned an incomplete change record."))
            }
        }

        val nextState = if (page.nextPageToken != null) {
            state.copy(changePageToken = page.nextPageToken, indexingInProgress = true, lastError = null)
        } else {
            val newStartToken = page.newStartPageToken
                ?: return Result.failure(IllegalStateException("Drive did not return a new change cursor."))
            state.copy(
                changeStartPageToken = newStartToken,
                changePageToken = null,
                indexingInProgress = false,
                lastSyncAtMs = System.currentTimeMillis(),
                lastError = null
            )
        }
        driveIndexDao.saveSyncState(nextState)
        return Result.success(page.nextPageToken != null)
    }

    private suspend fun upsertMetadata(record: DriveMetadataRecord) {
        val existing = driveIndexDao.getByDriveId(record.fileId)
        if (existing == null) {
            driveIndexDao.upsertFile(
                DriveIndexFileEntity(
                    driveFileId = record.fileId,
                    name = record.name,
                    mimeType = record.mimeType,
                    sizeBytes = record.sizeBytes,
                    modifiedTimeMs = record.modifiedTimeMs,
                    parentIdsCsv = record.parentIds.joinToString(","),
                    webViewLink = record.webViewLink,
                    starred = record.starred
                )
            )
            return
        }

        val contentChanged = existing.modifiedTimeMs != record.modifiedTimeMs
        driveIndexDao.upsertFile(
            existing.copy(
                name = record.name,
                mimeType = record.mimeType,
                sizeBytes = record.sizeBytes,
                modifiedTimeMs = record.modifiedTimeMs,
                parentIdsCsv = record.parentIds.joinToString(","),
                webViewLink = record.webViewLink,
                starred = record.starred,
                extractedText = if (contentChanged) "" else existing.extractedText,
                extractionSource = if (contentChanged) null else existing.extractionSource,
                contentSha256 = if (contentChanged) null else existing.contentSha256,
                embeddingModel = if (contentChanged) null else existing.embeddingModel,
                embeddingVersion = if (contentChanged) null else existing.embeddingVersion,
                embeddingDimension = if (contentChanged) null else existing.embeddingDimension,
                embeddingVector = if (contentChanged) null else existing.embeddingVector,
                indexStatus = if (contentChanged) "METADATA_ONLY" else existing.indexStatus,
                indexAttempts = if (contentChanged) 0 else existing.indexAttempts,
                lastIndexedAtMs = if (contentChanged) 0L else existing.lastIndexedAtMs
            )
        )
    }

    private suspend fun markRemoteRemoved(fileId: String) {
        driveIndexDao.removeUnpinnedStaleDriveMetadata(fileId)
        driveIndexDao.markPinnedFileAsRemoteRemoved(fileId)
    }

    companion object {
        const val BATCH_SIZE = 50
        const val MAX_DRIVE_FILES = 20_000
    }
}
