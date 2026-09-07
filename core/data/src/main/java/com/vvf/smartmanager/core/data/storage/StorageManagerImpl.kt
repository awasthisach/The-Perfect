package com.vvf.smartmanager.core.data.storage

import android.content.Context
import android.os.Build
import android.os.Environment
import android.os.storage.StorageManager
import android.os.StatFs
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

    fun getAllowedStorageRoots(): List<File> {
        val roots = mutableListOf<File>()
        try {
            context.filesDir?.let { roots.add(it.canonicalFile) }
            context.cacheDir?.let { roots.add(it.canonicalFile) }
            context.getExternalFilesDirs(null)?.filterNotNull()?.forEach { roots.add(it.canonicalFile) }
            context.getExternalCacheDirs()?.filterNotNull()?.forEach { roots.add(it.canonicalFile) }
            val extStorage = Environment.getExternalStorageDirectory()
            if (extStorage != null) roots.add(extStorage.canonicalFile)
            for (vol in listStorageVolumeRoots()) {
                roots.add(vol.canonicalFile)
            }
        } catch (_: Exception) {}
        return roots.distinctBy { it.absolutePath }
    }

    fun listStorageVolumeRoots(): List<File> {
        val volumes = mutableListOf<File>()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                val sm = context.getSystemService(StorageManager::class.java)
                sm?.storageVolumes?.forEach { volume ->
                    val dir = volume.directory
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

    fun requireAllowedPhysicalPath(path: String): File {
        require(path.isNotBlank()) { "Physical path cannot be blank" }
        val candidate = File(path).canonicalFile
        val rootPaths = getAllowedStorageRoots().map { it.absolutePath }
        val allowed = rootPaths.any { root ->
            candidate.absolutePath == root || candidate.absolutePath.startsWith(root + File.separator)
        }
        require(allowed) { "Path is outside allowed storage roots: $path" }
        return candidate
    }

    fun isAllowedPhysicalPath(path: String): Boolean = try {
        requireAllowedPhysicalPath(path)
        true
    } catch (_: Exception) {
        false
    }

    fun getPrimaryStoragePath(): String = try {
        Environment.getExternalStorageDirectory().absolutePath
    } catch (_: Exception) {
        context.getExternalFilesDir(null)?.absolutePath ?: context.filesDir.absolutePath
    }

    fun getFileSize(path: String): Long = try {
        File(path).length()
    } catch (_: Exception) {
        0L
    }

    fun calculateStorageBreakdown(): StorageBreakdown = try {
        val path = getPrimaryStoragePath()
        val stat = StatFs(path)
        val totalBytes = stat.totalBytes
        val freeBytes = stat.availableBytes
        val usedBytes = totalBytes - freeBytes
        StorageBreakdown(totalBytes = totalBytes, usedBytes = usedBytes, freeBytes = freeBytes)
    } catch (_: Exception) {
        StorageBreakdown(totalBytes = 0, usedBytes = 0, freeBytes = 0)
    }

    fun listDirectory(directoryPath: String, sortOption: FileSortOption, showHidden: Boolean): List<FileItem> {
        val dir = File(directoryPath)
        if (!dir.exists() || !dir.isDirectory) return emptyList()
        val files = dir.listFiles() ?: return emptyList()
        val items = files.filter { showHidden || !it.name.startsWith(".") }.map { file ->
            FileItem(
                path = file.absolutePath,
                name = file.name,
                sizeBytes = if (file.isDirectory) 0L else file.length(),
                mimeType = if (file.isDirectory) "inode/directory" else getMimeType(file.name),
                isDirectory = file.isDirectory,
                lastModified = file.lastModified()
            )
        }
        return sortFiles(items, sortOption)
    }

    fun collectPrimaryStorageItems(maxItems: Int = 10_000): List<FileItem> {
        val list = mutableListOf<File>()
        collectFiles(File(getPrimaryStoragePath()), list, maxItems)
        return list.map { file ->
            FileItem(
                path = file.absolutePath,
                name = file.name,
                sizeBytes = file.length(),
                mimeType = getMimeType(file.name),
                isDirectory = false,
                lastModified = file.lastModified()
            )
        }
    }

    fun listCategorizedFiles(category: FileCategory, sortOption: FileSortOption): List<FileItem> {
        val results = mutableListOf<FileItem>()
        when (category) {
            FileCategory.ALL -> results.addAll(collectPrimaryStorageItems())
            FileCategory.IMAGES -> results.addAll(queryMediaStoreFiles(MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
            FileCategory.VIDEOS -> results.addAll(queryMediaStoreFiles(MediaStore.Video.Media.EXTERNAL_CONTENT_URI))
            FileCategory.AUDIO -> results.addAll(queryMediaStoreFiles(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI))
            FileCategory.DOCUMENTS -> scanDirectoryByExtensions(File(getPrimaryStoragePath()), setOf("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "txt", "csv", "epub"), results)
            FileCategory.ARCHIVES -> scanDirectoryByExtensions(File(getPrimaryStoragePath()), setOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz"), results)
            FileCategory.APKS -> scanDirectoryByExtensions(File(getPrimaryStoragePath()), setOf("apk", "xapk", "apks"), results)
            else -> results.addAll(collectPrimaryStorageItems())
        }
        return sortFiles(results, sortOption)
    }

    private fun queryMediaStoreFiles(uri: Uri): List<FileItem> {
        val items = mutableListOf<FileItem>()
        try {
            val projection = arrayOf(
                MediaStore.MediaColumns.DATA,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.DATE_MODIFIED
            )
            context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
                val dataIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val nameIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val mimeIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dateIdx = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)
                while (cursor.moveToNext()) {
                    val path = cursor.getString(dataIdx) ?: continue
                    items.add(
                        FileItem(
                            path = path,
                            name = cursor.getString(nameIdx) ?: File(path).name,
                            sizeBytes = cursor.getLong(sizeIdx),
                            mimeType = cursor.getString(mimeIdx) ?: getMimeType(path),
                            isDirectory = false,
                            lastModified = cursor.getLong(dateIdx) * 1000L
                        )
                    )
                }
            }
        } catch (_: Exception) {}
        return items
    }

    private fun scanDirectoryByExtensions(
        dir: File,
        extensions: Set<String>,
        outList: MutableList<FileItem>,
        currentDepth: Int = 0,
        maxDepth: Int = 3
    ) {
        if (!dir.exists() || !dir.isDirectory || currentDepth > maxDepth) return
        val children = dir.listFiles() ?: return
        for (file in children) {
            if (file.isDirectory) {
                if (!file.name.startsWith(".") && file.name != "Android")
                    scanDirectoryByExtensions(file, extensions, outList, currentDepth + 1, maxDepth)
            } else {
                val ext = file.name.substringAfterLast('.', "").lowercase()
                if (ext in extensions) {
                    outList.add(
                        FileItem(
                            path = file.absolutePath,
                            name = file.name,
                            sizeBytes = file.length(),
                            mimeType = getMimeType(file.name),
                            isDirectory = false,
                            lastModified = file.lastModified()
                        )
                    )
                }
            }
        }
    }

    private fun sortFiles(files: List<FileItem>, sortOption: FileSortOption): List<FileItem> =
        when (sortOption) {
            FileSortOption.NAME_ASC -> files.sortedBy { it.name.lowercase() }
            FileSortOption.NAME_DESC -> files.sortedByDescending { it.name.lowercase() }
            FileSortOption.SIZE_ASC -> files.sortedBy { it.sizeBytes }
            FileSortOption.SIZE_DESC -> files.sortedByDescending { it.sizeBytes }
            FileSortOption.DATE_ASC -> files.sortedBy { it.lastModified }
            FileSortOption.DATE_DESC -> files.sortedByDescending { it.lastModified }
            else -> files
        }

    protected fun calculatePartialHash(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                var remaining = 64 * 1024
                while (remaining > 0) {
                    val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                    remaining -= read
                }
            }
            bytesToHex(digest.digest())
        } catch (_: Exception) {
            ""
        }
    }

    protected fun calculateFullSha256(file: File): String {
        return try {
            val digest = MessageDigest.getInstance("SHA-256")
            FileInputStream(file).use { input ->
                val buffer = ByteArray(8192)
                while (true) {
                    val read = input.read(buffer)
                    if (read <= 0) break
                    digest.update(buffer, 0, read)
                }
            }
            bytesToHex(digest.digest())
        } catch (_: Exception) {
            ""
        }
    }

    protected fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

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
