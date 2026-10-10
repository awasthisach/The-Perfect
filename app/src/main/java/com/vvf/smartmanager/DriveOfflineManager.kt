package com.vvf.smartmanager

import android.content.Context
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveAuth
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/** App-private Drive pinning with a strict 200 MiB / 80-file quota and oldest-first eviction. */
class DriveOfflineManager(private val context: Context) {
    suspend fun toggle(path: String): Result<Boolean> = withContext(Dispatchers.IO) {
        val app = context.applicationContext as VVFApplication
        val dao = app.database.fileDao()
        val item = dao.getByPath(path)
            ?: return@withContext Result.failure(IllegalArgumentException("Drive search item is no longer indexed"))
        if (!item.path.startsWith(DRIVE_PREFIX)) {
            return@withContext Result.failure(IllegalArgumentException("Only Google Drive search results can be pinned"))
        }

        if (item.offlinePinned) {
            dao.setOfflinePin(item.path, false, null, null, 0L)
            item.offlineLocalPath?.let { File(it).delete() }
            return@withContext Result.success(false)
        }

        val id = item.path.removePrefix(DRIVE_PREFIX)
        if (!id.matches(Regex("[A-Za-z0-9_-]{1,200}"))) {
            return@withContext Result.failure(IllegalArgumentException("Invalid Drive file ID"))
        }
        if (BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank() || BuildConfig.GOOGLE_WEB_CLIENT_ID == "__UNCONFIGURED__") {
            return@withContext Result.failure(IllegalStateException("Google Drive sign-in is not configured"))
        }

        val auth = GoogleDriveAuth(context, BuildConfig.GOOGLE_WEB_CLIENT_ID, app.googleDriveService)
        val token = auth.silentRefreshDriveAccessToken().getOrElse {
            return@withContext Result.failure(IllegalStateException("Reconnect the same Google account before pinning files offline"))
        }
        app.googleDriveService.setAccessToken(token)

        val directory = File(app.filesDir, "drive-offline").apply { mkdirs() }
        val staging = File.createTempFile("drive_pin_", ".staging", directory)
        var finalFile: File? = null
        try {
            val downloaded = app.googleDriveService.downloadFileBounded(
                id,
                staging.absolutePath,
                MAX_OFFLINE_BYTES,
                item.mimeType
            )
            if (downloaded.isFailure) return@withContext Result.failure(downloaded.exceptionOrNull()!!)
            val actualBytes = staging.length()
            if (actualBytes <= 0L || actualBytes > MAX_OFFLINE_BYTES) {
                return@withContext Result.failure(IllegalArgumentException("File is empty or exceeds the 200 MiB offline limit"))
            }

            var currentCount = dao.getOfflinePinnedCount()
            var currentBytes = dao.getOfflinePinnedBytes()
            val oldestFirst = dao.getOfflinePinnedDriveFiles()
            for (oldPin in oldestFirst) {
                if (currentCount < MAX_OFFLINE_FILES && currentBytes + actualBytes <= MAX_OFFLINE_BYTES) break
                oldPin.offlineLocalPath?.let { File(it).delete() }
                dao.setOfflinePin(oldPin.path, false, null, null, 0L)
                currentCount = (currentCount - 1).coerceAtLeast(0)
                currentBytes = (currentBytes - oldPin.offlineBytes).coerceAtLeast(0L)
            }
            if (currentCount >= MAX_OFFLINE_FILES || currentBytes + actualBytes > MAX_OFFLINE_BYTES) {
                return@withContext Result.failure(IllegalStateException("Offline quota is full; remove a pinned file and retry"))
            }

            val extension = when (item.mimeType) {
                GOOGLE_DOC -> "pdf"
                GOOGLE_SHEET -> "xlsx"
                GOOGLE_SLIDES -> "pdf"
                else -> item.name.substringAfterLast('.', "bin")
                    .lowercase().filter { it.isLetterOrDigit() }.take(10).ifBlank { "bin" }
            }
            val destination = File(directory, "$id.$extension")
            finalFile = destination
            if (destination.exists() && !destination.delete()) {
                return@withContext Result.failure(IllegalStateException("Could not replace a stale offline copy"))
            }
            if (!staging.renameTo(destination)) {
                staging.copyTo(destination, overwrite = true)
                staging.delete()
            }
            dao.setOfflinePin(item.path, true, destination.absolutePath, System.currentTimeMillis(), actualBytes)
            Result.success(true)
        } catch (e: Exception) {
            finalFile?.delete()
            Result.failure(e)
        } finally {
            staging.delete()
        }
    }

    fun clearAllLocalCopies() {
        File(context.applicationContext.filesDir, "drive-offline").deleteRecursively()
        File(context.applicationContext.cacheDir, "drive-index-inputs").deleteRecursively()
    }

    companion object {
        const val MAX_OFFLINE_FILES = 80
        const val MAX_OFFLINE_BYTES = 200L * 1024L * 1024L
        private const val DRIVE_PREFIX = "gdrive://"
        private const val GOOGLE_DOC = "application/vnd.google-apps.document"
        private const val GOOGLE_SHEET = "application/vnd.google-apps.spreadsheet"
        private const val GOOGLE_SLIDES = "application/vnd.google-apps.presentation"
    }
}
