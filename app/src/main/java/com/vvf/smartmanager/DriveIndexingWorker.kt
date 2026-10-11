package com.vvf.smartmanager

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vvf.smartmanager.core.cloud.gdrive.DriveFileDto
import com.vvf.smartmanager.core.cloud.gdrive.DriveIndexingPolicy
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveAuth
import com.vvf.smartmanager.core.database.model.CloudSyncEntity
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.OcrOptions
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * User-authorized Google Drive index sync. Tokens are reacquired silently from the existing
 * matching Google/Firebase session and are never written to WorkManager input/output or prefs.
 *
 * A batch is committed to Room before the changes cursor is advanced. Re-running a failed
 * full listing is idempotent because file rows are keyed by the stable Drive file ID.
 */
class DriveIndexingWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val app = applicationContext as? VVFApplication
            ?: return@withContext Result.failure(workDataOf("reason" to "Application composition root unavailable"))
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank() ||
            BuildConfig.GOOGLE_WEB_CLIENT_ID == "__UNCONFIGURED__"
        ) {
            return@withContext Result.failure(workDataOf("reason" to "Google OAuth web client ID is not configured"))
        }

        val prefs = applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val auth = GoogleDriveAuth(applicationContext, BuildConfig.GOOGLE_WEB_CLIENT_ID, app.googleDriveService)
        val token = auth.silentRefreshDriveAccessToken().getOrElse {
            saveStatus(app, "SIGN_IN_REQUIRED", 0, false, it.message.orEmpty())
            return@withContext Result.failure(workDataOf("reason" to "Sign in to Google Drive again to resume indexing"))
        }
        app.googleDriveService.setAccessToken(token)
        app.publishDriveIndexStatus("SYNCING", 0, false, "Preparing Drive index sync")

        try {
            val cursorDao = app.database.cloudSyncDao()
            val savedCursor = cursorDao.getRecord(CURSOR_LOCAL_PATH, CURSOR_PROVIDER)?.remoteFileId
                ?: prefs.getString(KEY_CURSOR, null)
            val snapshot = app.googleDriveService.syncDriveSnapshot(savedCursor).getOrElse {
                saveStatus(app, "RETRY_REQUIRED", 0, false, it.message.orEmpty())
                return@withContext if (runAttemptCount < 5) Result.retry()
                else Result.failure(workDataOf("reason" to "Drive sync failed; sign in again or retry later"))
            }

            var indexed = 0
            val fullListing = snapshot.fullListing
            if (fullListing != null) {
                val files = fullListing.files
                for (batch in files.chunked(BATCH_SIZE)) {
                    if (isStopped) return@withContext Result.retry()
                    for (item in batch) {
                        if (isStopped) return@withContext Result.retry()
                        upsertDriveFile(app, item)
                        indexed++
                    }
                    app.database.searchFtsDao().rebuildFtsIndex()
                    setProgress(workDataOf("indexed_count" to indexed, "total_count" to files.size))
                    app.publishDriveIndexStatus("SYNCING", indexed, fullListing.capped, "Indexed $indexed of ${files.size} Drive items")
                }

                // Remove stale local search rows only when the full Drive listing was complete.
                if (!fullListing.capped) {
                    val livePaths = files.mapTo(HashSet(files.size)) { it.path }
                    val stalePaths = app.database.fileDao().getIndexedPathSnapshot()
                        .asSequence()
                        .map { it.path }
                        .filter { it.startsWith(DRIVE_PATH_PREFIX) && it !in livePaths }
                        .toList()
                    if (stalePaths.isNotEmpty()) {
                        stalePaths.chunked(BATCH_SIZE).forEach { batch ->
                            batch.forEach { stalePath ->
                                app.database.fileDao().getByPath(stalePath)?.offlineLocalPath
                                    ?.let { java.io.File(it).delete() }
                            }
                            app.database.fileDao().deleteStaleByPaths(batch)
                        }
                    }
                }
            } else {
                val changes = snapshot.changes
                    ?: return@withContext Result.failure(workDataOf("reason" to "Drive returned neither a full listing nor changes"))
                for (batch in changes.changes.chunked(BATCH_SIZE)) {
                    if (isStopped) return@withContext Result.retry()
                    for (change in batch) {
                        if (change.removed || change.file?.trashed == true) {
                            val removedPath = DRIVE_PATH_PREFIX + change.fileId
                            app.database.fileDao().getByPath(removedPath)?.offlineLocalPath
                                ?.let { java.io.File(it).delete() }
                            app.database.fileDao().deleteByPath(removedPath)
                        } else {
                            change.file?.let { dto -> upsertDriveFile(app, dto.toFileItem()) }
                        }
                        indexed++
                    }
                    app.database.searchFtsDao().rebuildFtsIndex()
                    setProgress(workDataOf("indexed_count" to indexed))
                    app.publishDriveIndexStatus("SYNCING", indexed, false, "Applied $indexed Drive changes")
                }
            }

            if (snapshot.capped) {
                // Do not commit a cursor after a capped full listing: that would silently skip
                // files beyond the cap. The status is visible to diagnostics and the next manual
                // sync will retry a bounded full listing.
                saveStatus(app, "LIMIT_REACHED", indexed, true, "Drive listing reached the 20,000-file safety cap; index may be incomplete")
            } else {
                // Persist the opaque Drive change cursor in Room only after the full sync succeeds.
                // It is not an auth token and is never written to WorkManager input/output.
                cursorDao.insertOrUpdate(
                    CloudSyncEntity(
                        localPath = CURSOR_LOCAL_PATH,
                        remoteFileId = snapshot.cursor,
                        provider = CURSOR_PROVIDER,
                        status = "CURSOR",
                        errorMessage = null
                    )
                )
                prefs.edit().putString(KEY_CURSOR, snapshot.cursor).apply() // backward compatibility
                saveStatus(app, "SYNCED", indexed, false, "")
            }
            Result.success(
                workDataOf(
                    "indexed_count" to indexed,
                    "capped" to snapshot.capped,
                    "full_resync" to snapshot.fullResyncRequired
                )
            )
        } catch (e: Exception) {
            // Never log bearer tokens or response headers.
            Log.w(TAG, "Drive indexing interrupted; cursor was not advanced")
            // Exception messages may contain remote response details; keep them out of status and logs.
            saveStatus(app, "RETRY_REQUIRED", 0, false, "Indexing failed. Retry or sign in again if requested.")
            if (runAttemptCount < 5) Result.retry()
            else Result.failure(workDataOf("reason" to "Drive indexing failed; last committed cursor was preserved"))
        }
    }

    private suspend fun upsertDriveFile(app: VVFApplication, item: FileItem) {
        val id = item.localFileId?.takeIf { it.isNotBlank() }
            ?: item.path.removePrefix(DRIVE_PATH_PREFIX).takeIf { it.isNotBlank() }
            ?: return
        val path = DRIVE_PATH_PREFIX + id
        val dao = app.database.fileDao()
        val old = dao.getByPath(path)
        val unchanged = old != null && DriveIndexingPolicy.isUnchanged(
            currentModifiedTime = item.lastModified,
            indexedModifiedTime = old.modifiedDate,
            currentMd5 = item.md5Hash,
            indexedMd5 = old.md5Hash
        )
        if (old?.offlinePinned == true && !unchanged) {
            old.offlineLocalPath?.let { java.io.File(it).delete() }
        }
        val preserveOfflinePin = unchanged && old?.offlinePinned == true &&
            old.offlineLocalPath?.let { java.io.File(it).isFile } == true
        val canExtract = isSupportedTextType(item.mimeType) ||
            (app.isDriveFullContentConsentEnabled() && isFullContentType(item))
        val content = when {
            unchanged && (!canExtract || old!!.contentText.isNotEmpty()) -> old!!.contentText
            canExtract -> extractDriveText(app, id, item, unchanged, old)
            else -> ""
        }
        if (isStopped) throw CancellationException("Drive indexing cancelled")
        val safeContent = if (!app.isDriveFullContentConsentEnabled() && isFullContentType(item)) "" else content
        dao.insertOrUpdate(
            FileMetadataEntity(
                path = path,
                name = item.name,
                parentPath = "gdrive://root",
                sizeBytes = item.sizeBytes.coerceAtLeast(0L),
                mimeType = item.mimeType ?: "application/octet-stream",
                isDirectory = item.isDirectory,
                modifiedDate = item.lastModified.coerceAtLeast(0L),
                isFavorite = item.isFavorite,
                isTrash = false,
                tags = "google-drive",
                md5Hash = item.md5Hash,
                contentText = safeContent,
                canonicalUri = item.canonicalUri,
                offlinePinned = preserveOfflinePin,
                offlineLocalPath = if (preserveOfflinePin) old?.offlineLocalPath else null,
                offlinePinnedAt = if (preserveOfflinePin) old?.offlinePinnedAt else null,
                offlineBytes = if (preserveOfflinePin) old?.offlineBytes ?: 0L else 0L
            )
        )
    }

    private suspend fun extractDriveText(
        app: VVFApplication,
        id: String,
        item: FileItem,
        unchanged: Boolean,
        old: FileMetadataEntity?
    ): String {
        if (isSupportedTextType(item.mimeType)) {
            return app.googleDriveService.extractTextContent(id, item.mimeType)
                .getOrElse { old?.contentText?.takeIf { unchanged }.orEmpty() }
        }
        if (!app.isDriveFullContentConsentEnabled() || !isFullContentType(item)) return ""
        if (item.sizeBytes !in 1..MAX_BINARY_DOWNLOAD_BYTES.toLong()) {
            return old?.contentText?.takeIf { unchanged }.orEmpty()
        }

        val extension = item.name.substringAfterLast('.', "bin")
            .lowercase()
            .filter { it.isLetterOrDigit() }
            .take(8)
            .ifBlank { "bin" }
        val directory = java.io.File(applicationContext.cacheDir, "drive-index-inputs").apply { mkdirs() }
        val temporary = java.io.File.createTempFile("drive_index_", ".$extension", directory)
        try {
            val download = app.googleDriveService.downloadFileBounded(id, temporary.absolutePath, MAX_BINARY_DOWNLOAD_BYTES.toLong(), item.mimeType)
            if (download.isFailure || temporary.length() !in 1..MAX_BINARY_DOWNLOAD_BYTES.toLong()) {
                return old?.contentText?.takeIf { unchanged }.orEmpty()
            }
            val mime = item.mimeType.orEmpty().lowercase()
            val officeMime = when (item.extension) {
                "docx" -> DriveOfficeTextExtractor.DOCX
                "xlsx" -> DriveOfficeTextExtractor.XLSX
                "pptx" -> DriveOfficeTextExtractor.PPTX
                else -> mime
            }
            if (officeMime == DriveOfficeTextExtractor.DOCX ||
                officeMime == DriveOfficeTextExtractor.XLSX ||
                officeMime == DriveOfficeTextExtractor.PPTX
            ) {
                return runCatching { DriveOfficeTextExtractor.extract(temporary, officeMime) }
                    .getOrElse { old?.contentText?.takeIf { unchanged }.orEmpty() }
            }
            val localItem = item.copy(
                path = temporary.absolutePath,
                sizeBytes = temporary.length(),
                canonicalUri = null,
                localFileId = null
            )
            return app.extractTextUseCase(
                localItem,
                OcrOptions(maxDimension = 2048, maxPagesForPdf = MAX_PDF_PAGES)
            ).fold(
                onSuccess = { it.fullText.take(MAX_EXTRACTED_TEXT_CHARS) },
                onFailure = { old?.contentText?.takeIf { unchanged }.orEmpty() }
            )
        } finally {
            temporary.delete()
        }
    }

    private fun isFullContentType(item: FileItem): Boolean {
        val mime = item.mimeType.orEmpty().lowercase()
        val extension = item.extension
        return mime == "application/pdf" ||
            mime.startsWith("image/") ||
            mime in setOf(DriveOfficeTextExtractor.DOCX, DriveOfficeTextExtractor.XLSX, DriveOfficeTextExtractor.PPTX) ||
            extension in setOf("pdf", "jpg", "jpeg", "png", "webp", "bmp", "heic", "docx", "xlsx", "pptx")
    }

    private fun isSupportedTextType(mimeType: String?): Boolean {
        val type = mimeType.orEmpty().lowercase()
        return type.startsWith("text/") ||
            type in setOf(
                "application/json", "application/xml", "application/csv", "application/rtf",
                "application/vnd.google-apps.document",
                "application/vnd.google-apps.spreadsheet",
                "application/vnd.google-apps.presentation"
            )
    }

    private fun DriveFileDto.toFileItem(): FileItem = FileItem(
        path = DRIVE_PATH_PREFIX + id.orEmpty(),
        name = name.orEmpty(),
        sizeBytes = size?.toLongOrNull() ?: 0L,
        lastModified = modifiedTime?.let {
            runCatching { java.time.Instant.parse(it).toEpochMilli() }.getOrDefault(0L)
        } ?: 0L,
        isDirectory = mimeType == "application/vnd.google-apps.folder",
        mimeType = mimeType,
        isFavorite = starred,
        md5Hash = md5Checksum,
        localFileId = id,
        canonicalUri = webViewLink
    )

    private fun saveStatus(
        app: VVFApplication,
        status: String,
        count: Int,
        capped: Boolean,
        message: String
    ) {
        app.publishDriveIndexStatus(status, count, capped, message)
    }

    companion object {
        private const val TAG = "DriveIndexingWorker"
        private const val PREFS = "drive_search_index"
        private const val KEY_CURSOR = "changes_cursor"
        private const val CURSOR_LOCAL_PATH = "__drive_semantic_search_changes_cursor__"
        private const val CURSOR_PROVIDER = "GDRIVE_INDEX_CURSOR"
        private const val BATCH_SIZE = 50
        private const val MAX_BINARY_DOWNLOAD_BYTES = 20 * 1024 * 1024
        private const val MAX_EXTRACTED_TEXT_CHARS = 250_000
        private const val MAX_PDF_PAGES = 10
        private const val DRIVE_PATH_PREFIX = "gdrive://"
    }
}
