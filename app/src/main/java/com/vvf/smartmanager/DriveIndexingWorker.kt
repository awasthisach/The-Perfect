package com.vvf.smartmanager

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.vvf.smartmanager.core.cloud.gdrive.DriveChangeDto
import com.vvf.smartmanager.core.cloud.gdrive.DriveFileDto
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveAuth
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import com.vvf.smartmanager.core.model.FileItem
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
            saveStatus(prefs, "SIGN_IN_REQUIRED", 0, false, it.message.orEmpty())
            return@withContext Result.failure(workDataOf("reason" to "Sign in to Google Drive again to resume indexing"))
        }
        app.googleDriveService.setAccessToken(token)

        try {
            val savedCursor = prefs.getString(KEY_CURSOR, null)
            val snapshot = app.googleDriveService.syncDriveSnapshot(savedCursor).getOrElse {
                saveStatus(prefs, "RETRY_REQUIRED", 0, false, it.message.orEmpty())
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
                        stalePaths.chunked(BATCH_SIZE).forEach { app.database.fileDao().deleteStaleByPaths(it) }
                    }
                }
            } else {
                val changes = snapshot.changes
                    ?: return@withContext Result.failure(workDataOf("reason" to "Drive returned neither a full listing nor changes"))
                for (batch in changes.changes.chunked(BATCH_SIZE)) {
                    if (isStopped) return@withContext Result.retry()
                    for (change in batch) {
                        if (change.removed) {
                            app.database.fileDao().deleteByPath(DRIVE_PATH_PREFIX + change.fileId)
                        } else {
                            change.file?.let { dto -> upsertDriveFile(app, dto.toFileItem()) }
                        }
                        indexed++
                    }
                    app.database.searchFtsDao().rebuildFtsIndex()
                    setProgress(workDataOf("indexed_count" to indexed))
                }
            }

            if (snapshot.capped) {
                // Do not commit a cursor after a capped full listing: that would silently skip
                // files beyond the cap. The status is visible to diagnostics and the next manual
                // sync will retry a bounded full listing.
                saveStatus(prefs, "LIMIT_REACHED", indexed, true, "Drive listing reached the 20,000-file safety cap; index may be incomplete")
            } else {
                prefs.edit().putString(KEY_CURSOR, snapshot.cursor).apply()
                saveStatus(prefs, "SYNCED", indexed, false, "")
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
            Log.w(TAG, "Drive indexing interrupted; cursor was not advanced", e)
            saveStatus(prefs, "RETRY_REQUIRED", 0, false, e.message.orEmpty())
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
        val unchanged = old != null &&
            old.modifiedDate == item.lastModified &&
            old.md5Hash == item.md5Hash
        val canExtract = isSupportedTextType(item.mimeType)
        val content = when {
            unchanged && (!canExtract || old!!.contentText.isNotEmpty()) -> old!!.contentText
            canExtract -> app.googleDriveService.extractTextContent(id, item.mimeType)
                .getOrElse { old?.contentText?.takeIf { unchanged }.orEmpty() }
            else -> ""
        }
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
                contentText = content
            )
        )
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
        prefs: android.content.SharedPreferences,
        status: String,
        count: Int,
        capped: Boolean,
        message: String
    ) {
        prefs.edit()
            .putString(KEY_STATUS, status)
            .putInt(KEY_LAST_COUNT, count)
            .putBoolean(KEY_CAPPED, capped)
            .putString(KEY_MESSAGE, message.take(500))
            .putLong(KEY_UPDATED_AT, System.currentTimeMillis())
            .apply()
    }

    companion object {
        private const val TAG = "DriveIndexingWorker"
        private const val PREFS = "drive_search_index"
        private const val KEY_CURSOR = "changes_cursor"
        private const val KEY_STATUS = "last_status"
        private const val KEY_LAST_COUNT = "last_indexed_count"
        private const val KEY_CAPPED = "listing_capped"
        private const val KEY_MESSAGE = "last_message"
        private const val KEY_UPDATED_AT = "last_updated_at"
        private const val BATCH_SIZE = 50
        private const val DRIVE_PATH_PREFIX = "gdrive://"
    }
}
