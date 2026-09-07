package com.vvf.smartmanager.core.data.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.vvf.smartmanager.core.database.dao.FileDao
import com.vvf.smartmanager.core.model.FileCategory
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.FileSortOption
import com.vvf.smartmanager.core.model.JunkItem
import com.vvf.smartmanager.core.model.JunkCategory
import com.vvf.smartmanager.core.model.StorageBreakdown
import android.net.Uri
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.ArrayDeque

open class StorageManagerImpl(
    protected val context: Context,
    protected val fileDao: FileDao
) {
    protected val trashDirectory: File by lazy {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, ".vvf_trash")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    /**
     * All browsable roots: internal + removable SD volumes (when mounted).
     */
    fun getAllowedStorageRoots(): List<File> {
        val roots = linkedSetOf<File>()
        try {
            context.filesDir?.let { roots.add(it.canonicalFile) }
            context.cacheDir?.let { roots.add(it.canonicalFile) }
            context.getExternalFilesDirs(null)?.filterNotNull()?.forEach { roots.add(it.canonicalFile) }
            context.getExternalCacheDirs()?.filterNotNull()?.forEach { roots.add(it.canonicalFile) }
            Environment.getExternalStorageDirectory()?.let { roots.add(it.canonicalFile) }
            for (vol in listStorageVolumeRoots()) {
                roots.add(vol.canonicalFile)
            }
        } catch (_: Exception) {}
        return roots.toList()
    }

    /**
     * Public volume roots for UI (Internal + SD Card).
     */
    fun listStorageVolumeRoots(): List<File> {
        val volumes = mutableListOf<File>()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val sm = context.getSystemService(StorageManager::class.java)
                sm?.storageVolumes?.forEach { volume ->
                    val dir = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        volume.directory
                    } else {
                        @Suppress("DEPRECATION")
                        volume.getPath()?.let { File(it) }
                    }
                    if (dir != null && dir.exists() && dir.canRead()) {
                        volumes.add(dir)
                    }
                }
            }
            context.getExternalFilesDirs(null)?.forEachIndexed { index, dir ->
                if (index > 0 && dir != null) {
                    var walk: File? = dir
                    repeat(4) {
                        val parent = walk?.parentFile ?: return@repeat
                        if (parent.absolutePath.startsWith("/storage/") &&
                            parent.name != "emulated" &&
                            parent.canRead()
                        ) {
                            volumes.add(parent)
                            return@forEachIndexed
                        }
                        walk = parent
                    }
                }
            }
        } catch (_: Exception) {}
        val primary = try {
            Environment.getExternalStorageDirectory()
        } catch (_: Exception) {
            null
        }
        if (primary != null && volumes.none { it.absolutePath == primary.absolutePath }) {
            volumes.add(0, primary)
        }
        return volumes.distinctBy { it.absolutePath }
    }

    open fun getPrimaryStoragePath(): String {
        return try {
            Environment.getExternalStorageDirectory().absolutePath
        } catch (_: Exception) {
            context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath
        }
    }

    open fun getStorageBreakdown(): StorageBreakdown {
        val path = getPrimaryStoragePath()
        val stat = try {
            StatFs(path)
        } catch (_: Exception) {
            return StorageBreakdown(
                totalBytes = 0L,
                freeBytes = 0L,
                usedBytes = 0L,
                categoryBytes = emptyMap()
            )
        }
        val total = stat.totalBytes
        val free = stat.availableBytes
        val used = total - free
        return StorageBreakdown(
            totalBytes = total,
            freeBytes = free,
            usedBytes = used,
            categoryBytes = emptyMap()
        )
    }

    open fun listDirectory(
        path: String,
        sortOption: FileSortOption = FileSortOption.NAME_ASC,
        showHidden: Boolean = false
    ): List<FileItem> {
        val dir = File(path)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val files = dir.listFiles() ?: return emptyList()
        val items = files
            .filter { showHidden || !it.name.startsWith(".") }
            .map { file ->
                FileItem(
                    path = file.absolutePath,
                    name = file.name,
                    sizeBytes = if (file.isDirectory) 0L else file.length(),
                    mimeType = if (file.isDirectory) "inode/directory" else getMimeType(file.name),
                    isDirectory = file.isDirectory,
                    lastModified = file.lastModified()
                )
            }
        return when (sortOption) {
            FileSortOption.NAME_ASC -> items.sortedBy { it.name.lowercase() }
            FileSortOption.NAME_DESC -> items.sortedByDescending { it.name.lowercase() }
            FileSortOption.SIZE_ASC -> items.sortedBy { it.sizeBytes }
            FileSortOption.SIZE_DESC -> items.sortedByDescending { it.sizeBytes }
            FileSortOption.DATE_ASC -> items.sortedBy { it.lastModified }
            FileSortOption.DATE_DESC -> items.sortedByDescending { it.lastModified }
            else -> items.sortedBy { it.name.lowercase() }
        }
    }

    protected fun collectFiles(dir: File, list: MutableList<File>, maxFiles: Int) {
        if (!dir.exists() || !dir.isDirectory || dir.name.startsWith(".") || list.size >= maxFiles) return
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (list.size >= maxFiles) break
            if (child.isDirectory) {
                if (child.name != "Android" && !child.name.startsWith(".")) collectFiles(child, list, maxFiles)
            } else list.add(child)
        }
    }

    protected fun collectFilesAndDirectories(
        dir: File,
        fileList: MutableList<File>,
        emptyFolderList: MutableList<JunkItem>,
        maxItems: Int
    ) {
        if (!dir.exists() || !dir.isDirectory || dir.name.startsWith(".") || fileList.size >= maxItems) return
        val children = dir.listFiles() ?: return
        if (children.isEmpty() && dir != File(getPrimaryStoragePath())) {
            emptyFolderList.add(
                JunkItem(
                    path = dir.absolutePath,
                    name = dir.name,
                    sizeBytes = 0L,
                    category = JunkCategory.EMPTY_FOLDERS,
                    details = "Empty directory without files"
                )
            )
            return
        }
        for (child in children) {
            if (fileList.size >= maxItems) break
            if (child.isDirectory) {
                if (child.name != "Android" && !child.name.startsWith("."))
                    collectFilesAndDirectories(child, fileList, emptyFolderList, maxItems)
            } else fileList.add(child)
        }
    }

    protected fun getUniqueDestinationFile(parentDir: File, originalName: String): File {
        var file = File(parentDir, originalName)
        if (!file.exists()) return file
        val nameWithoutExt = originalName.substringBeforeLast('.')
        val ext = if (originalName.contains('.')) ".${originalName.substringAfterLast('.')}" else ""
        var index = 1
        while (file.exists()) {
            file = File(parentDir, "${nameWithoutExt}_($index)$ext")
            index++
        }
        return file
    }

    protected fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: when (extension) {
            "jpg", "jpeg", "png", "webp", "gif", "svg" -> "image/$extension"
            "mp4", "mkv", "mov", "avi", "3gp" -> "video/$extension"
            "mp3", "wav", "m4a", "flac", "ogg" -> "audio/$extension"
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "txt", "csv", "log" -> "text/plain"
            "zip", "rar", "7z", "tar", "gz" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }
}
