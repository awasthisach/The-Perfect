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
 */
object FileOpenHelper {

    fun open(context: Context, item: FileItem): String? {
        if (item.isDirectory) return "Cannot open a folder with a viewer"
        val file = File(item.path)
        if (!file.exists() || !file.canRead()) {
            return "File not found or unreadable"
        }
        return try {
            val authority = "${context.packageName}.fileprovider"
            val uri = FileProvider.getUriForFile(context, authority, file)
            val mime = item.mimeType
                ?: MimeTypeMap.getSingleton()
                    .getMimeTypeFromExtension(file.extension.lowercase())
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
}
