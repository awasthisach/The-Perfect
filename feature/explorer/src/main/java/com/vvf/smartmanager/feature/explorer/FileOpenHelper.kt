package com.vvf.smartmanager.feature.explorer

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.webkit.MimeTypeMap
import androidx.core.content.FileProvider
import com.vvf.smartmanager.core.model.FileItem
import java.io.File

/**
 * Opens a local file with the system viewer via FileProvider + ACTION_VIEW.
 *
 * Files outside the app cache/files trees are copied into cache first so FileProvider
 * never needs a <root-path> entry (Phase-1 FILEPROVIDER-PATH-TRAVERSAL fix).
 */
object FileOpenHelper {

    fun open(context: Context, item: FileItem): String? {
        if (item.isDirectory) return "Cannot open a folder with a viewer"
        val file = File(item.path)
        if (!file.exists() || !file.canRead()) {
            return "File not found or unreadable"
        }
        return try {
            val shareable = ensureShareableFile(context, file)
                ?: return "Unable to stage file for secure sharing"
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, shareable)
            val mime = item.mimeType
                ?: MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(shareable.extension.lowercase())
                ?: "*/*"
            val intent = Intent(Intent.ACTION_VIEW).apply {
                setDataAndType(uri, mime)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(Intent.createChooser(intent, "Open with"))
            null
        } catch (e: ActivityNotFoundException) {
            "No app found to open this file type"
        } catch (e: Exception) {
            e.localizedMessage ?: "Failed to open file"
        }
    }

    /**
     * Returns a file under cacheDir or filesDir suitable for FileProvider.
     * Copies external/user storage paths into cache/shared-open/.
     */
    internal fun ensureShareableFile(context: Context, file: File): File? {
        val canonical = runCatching { file.canonicalFile }.getOrElse { return null }
        val allowedRoots = buildList {
            add(context.cacheDir)
            add(context.filesDir)
            context.getExternalFilesDir(null)?.let { add(it) }
            context.externalCacheDir?.let { add(it) }
        }.mapNotNull { runCatching { it.canonicalFile }.getOrNull() }

        val alreadyAllowed = allowedRoots.any { root ->
            canonical.path == root.path || canonical.path.startsWith(root.path + File.separator)
        }
        if (alreadyAllowed) return canonical

        val shareDir = File(context.cacheDir, "shared-open").apply { mkdirs() }
        val dest = File(shareDir, canonical.name)
        return runCatching {
            canonical.copyTo(dest, overwrite = true)
            dest
        }.getOrNull()
    }
}
