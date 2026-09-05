package com.vvf.smartmanager.core.data.storage

import android.content.Context
import android.os.Build
import android.os.Environment
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

/**
 * Base storage implementation: fail-closed path auth, listing, and shared helpers.
 */
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
        } catch (_: Exception) {}
        return roots
    }

    fun requireAllowedPhysicalPath(path: String): File {
        require(path.isNotBlank()) { "Physical path cannot be blank" }
        val candidate = File(path).canonicalFile
        val rootPaths = getAllowedStorageRoots().map { it.absolutePath }
        require(StoragePathPolicy.isPathWithinApprovedRoots(candidate.absolutePath, rootPaths)) {
            StoragePathPolicy.denialMessage(path, rootPaths)
        }
        return candidate
    }

    fun isAllowedPhysicalPath(path: String): Boolean = try {
        requireAllowedPhysicalPath(path); true
    } catch (_: Exception) { false }

    fun getPrimaryStoragePath(): String = try {
        val extDir = Environment.getExternalStorageDirectory()
        when {
            extDir != null && extDir.exists() && extDir.canRead() -> extDir.absolutePath
            else -> (context.getExternalFilesDir(null) ?: context.filesDir).absolutePath
        }
    } catch (_: Exception) { context.filesDir.absolutePath }

    fun getFileSize(path: String): Long = try {
        val f = File(path); if (f.exists() && !f.isDirectory) f.length() else 0L
    } catch (_: Exception) { 0L }

    fun calculateStorageBreakdown(): StorageBreakdown = try {
        val rootPath = getPrimaryStoragePath()
        val stat = StatFs(rootPath)
        val totalBytes = stat.blockCountLong * stat.blockSizeLong
        val freeBytes = stat.availableBlocksLong * stat.blockSizeLong
        val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
        StorageBreakdown(totalBytes = totalBytes, usedBytes = usedBytes, freeBytes = freeBytes)
    } catch (_: Exception) {
        StorageBreakdown(totalBytes = 0, usedBytes = 0, freeBytes = 0)
    }

    fun listDirectory(directoryPath: String, sortOption: FileSortOption, showHidden: Boolean): List<FileItem> {
        val targetDir = File(directoryPath)
        if (!targetDir.exists() || !targetDir.isDirectory) return emptyList()
        val rawFiles = targetDir.listFiles() ?: return emptyList()
        val items = rawFiles
            .filter { (showHidden || !it.name.startsWith(".")) && !it.absolutePath.contains(".vvf_trash") }
            .map { file ->
                FileItem(
                    path = file.absolutePath, name = file.name,
                    sizeBytes = if (file.isDirectory) 0L else file.length(),
                    lastModified = file.lastModified(), isDirectory = file.isDirectory,
                    mimeType = if (file.isDirectory) null else getMimeType(file.name),
                    itemCount = if (file.isDirectory) (file.listFiles()?.size ?: 0) else 0
                )
            }
        return sortFiles(items, sortOption)
    }

    /**
     * Bounded snapshot of primary shared storage for search indexing.
     * Skips per-directory listFiles() for itemCount during bulk walk (lag fix).
     */
    fun collectPrimaryStorageItems(maxItems: Int = 10_000): List<FileItem> {
        require(maxItems > 0) { "maxItems must be positive" }
        val root = File(getPrimaryStoragePath())
        if (!root.exists() || !root.isDirectory) return emptyList()

        val result = ArrayList<FileItem>(minOf(maxItems, 1_024))
        val directories = ArrayDeque<File>()
        directories.add(root)
        while (directories.isNotEmpty() && result.size < maxItems) {
            val directory = directories.removeFirst()
            val children = directory.listFiles() ?: continue
            for (child in children) {
                if (result.size >= maxItems) break
                if (child.name.startsWith(".") || child.name == "Android" || child.absolutePath.contains(".vvf_trash")) {
                    continue
                }
                val isDirectory = child.isDirectory
                result.add(
                    FileItem(
                        path = child.absolutePath,
                        name = child.name,
                        sizeBytes = if (isDirectory) 0L else child.length(),
                        lastModified = child.lastModified(),
                        isDirectory = isDirectory,
                        mimeType = if (isDirectory) null else getMimeType(child.name),
                        itemCount = 0
                    )
                )
                if (isDirectory) directories.addLast(child)
            }
        }
        return result
    }

    fun listCategorizedFiles(category: FileCategory, sortOption: FileSortOption): List<FileItem> {
        if (category == FileCategory.ALL) return listDirectory(getPrimaryStoragePath(), sortOption, false)
        val results = mutableListOf<FileItem>()
        when (category) {
            FileCategory.IMAGES -> results.addAll(queryMediaStoreFiles(MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
            FileCategory.VIDEOS -> results.addAll(queryMediaStoreFiles(MediaStore.Video.Media.EXTERNAL_CONTENT_URI))
            FileCategory.AUDIO -> results.addAll(queryMediaStoreFiles(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI))
            FileCategory.DOCUMENTS -> scanDirectoryByExtensions(File(getPrimaryStoragePath()), setOf("pdf","doc","docx","xls","xlsx","ppt","pptx","txt","csv","epub"), results)
            FileCategory.ARCHIVES -> scanDirectoryByExtensions(File(getPrimaryStoragePath()), setOf("zip","rar","7z","tar","gz","bz2","xz"), results)
            FileCategory.APKS -> scanDirectoryByExtensions(File(getPrimaryStoragePath()), setOf("apk","xapk","apks"), results)
            else -> {}
        }
        return sortFiles(results, sortOption)
    }

    private fun queryMediaStoreFiles(uri: Uri): List<FileItem> {
        val list = mutableListOf<FileItem>()
        try {
            val projection = arrayOf(MediaStore.MediaColumns.DATA, MediaStore.MediaColumns.DISPLAY_NAME, MediaStore.MediaColumns.SIZE, MediaStore.MediaColumns.DATE_MODIFIED, MediaStore.MediaColumns.MIME_TYPE)
            context.contentResolver.query(uri, projection, null, null, "${MediaStore.MediaColumns.DATE_MODIFIED} DESC")?.use { cursor ->
                val dataCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATA)
                val nameCol = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
                val sizeCol = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
                val dateCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_MODIFIED)
                val mimeCol = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
                while (cursor.moveToNext()) {
                    val path = if (dataCol != -1) cursor.getString(dataCol) else null ?: continue
                    val name = if (nameCol != -1) cursor.getString(nameCol) else File(path).name
                    list.add(FileItem(path = path, name = name ?: File(path).name, sizeBytes = if (sizeCol != -1) cursor.getLong(sizeCol) else 0L, lastModified = if (dateCol != -1) cursor.getLong(dateCol) * 1000 else System.currentTimeMillis(), isDirectory = false, mimeType = if (mimeCol != -1) cursor.getString(mimeCol) else getMimeType(name ?: "")))
                }
            }
        } catch (_: Exception) {}
        return list
    }

    private fun scanDirectoryByExtensions(dir: File, extensions: Set<String>, outList: MutableList<FileItem>, currentDepth: Int = 0, maxDepth: Int = 3) {
        if (currentDepth > maxDepth || !dir.exists() || !dir.isDirectory || dir.name.startsWith(".")) return
        val children = dir.listFiles() ?: return
        for (file in children) {
            if (file.isDirectory) {
                if (!file.name.startsWith(".") && file.name != "Android") scanDirectoryByExtensions(file, extensions, outList, currentDepth + 1, maxDepth)
            } else if (extensions.contains(file.extension.lowercase())) {
                outList.add(FileItem(path = file.absolutePath, name = file.name, sizeBytes = file.length(), lastModified = file.lastModified(), isDirectory = false, mimeType = getMimeType(file.name)))
            }
        }
    }

    private fun sortFiles(files: List<FileItem>, sortOption: FileSortOption): List<FileItem> {
        val (dirs, nonDirs) = files.partition { it.isDirectory }
        fun sortList(list: List<FileItem>) = when (sortOption) {
            FileSortOption.NAME_ASC -> list.sortedBy { it.name.lowercase() }
            FileSortOption.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
            FileSortOption.DATE_DESC -> list.sortedByDescending { it.lastModified }
            FileSortOption.DATE_ASC -> list.sortedBy { it.lastModified }
            FileSortOption.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
            FileSortOption.SIZE_ASC -> list.sortedBy { it.sizeBytes }
            FileSortOption.TYPE_ASC -> list.sortedBy { it.name.lowercase() }
        }
        return sortList(dirs) + sortList(nonDirs)
    }

    protected fun calculatePartialHash(file: File): String {
        try {
            val length = file.length()
            if (length <= 64 * 1024) return calculateFullSha256(file)
            val md = MessageDigest.getInstance("SHA-256")
            val sampleSize = 8 * 1024
            val buffer = ByteArray(sampleSize)
            FileInputStream(file).use { fis ->
                var read = fis.read(buffer, 0, sampleSize); if (read > 0) md.update(buffer, 0, read)
                fis.channel.position(length / 2); read = fis.read(buffer, 0, sampleSize); if (read > 0) md.update(buffer, 0, read)
                fis.channel.position((length - sampleSize).coerceAtLeast(0L)); read = fis.read(buffer, 0, sampleSize); if (read > 0) md.update(buffer, 0, read)
            }
            return bytesToHex(md.digest())
        } catch (_: Exception) { return "${file.length()}_${file.name}" }
    }

    protected fun calculateFullSha256(file: File): String {
        try {
            val md = MessageDigest.getInstance("SHA-256")
            val buffer = ByteArray(64 * 1024)
            FileInputStream(file).use { fis ->
                var bytesRead: Int
                while (fis.read(buffer).also { bytesRead = it } != -1) md.update(buffer, 0, bytesRead)
            }
            return bytesToHex(md.digest())
        } catch (_: Exception) { return "${file.length()}_${file.lastModified()}" }
    }

    protected fun bytesToHex(bytes: ByteArray): String {
        val hexDigits = "0123456789abcdef"
        return buildString(bytes.size * 2) {
            for (b in bytes) {
                val v = b.toInt() and 0xFF
                append(hexDigits[v ushr 4]); append(hexDigits[v and 0x0F])
            }
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

    protected fun collectFilesAndDirectories(dir: File, fileList: MutableList<File>, emptyFolderList: MutableList<JunkItem>, maxItems: Int) {
        if (!dir.exists() || !dir.isDirectory || dir.name.startsWith(".") || fileList.size >= maxItems) return
        val children = dir.listFiles() ?: return
        if (children.isEmpty() && dir != File(getPrimaryStoragePath())) {
            emptyFolderList.add(JunkItem(path = dir.absolutePath, name = dir.name, sizeBytes = 0L, category = JunkCategory.EMPTY_FOLDERS, details = "Empty directory without files"))
            return
        }
        for (child in children) {
            if (fileList.size >= maxItems) break
            if (child.isDirectory) {
                if (child.name != "Android" && !child.name.startsWith(".")) collectFilesAndDirectories(child, fileList, emptyFolderList, maxItems)
            } else fileList.add(child)
        }
    }

    protected fun getUniqueDestinationFile(parentDir: File, originalName: String): File {
        var file = File(parentDir, originalName)
        if (!file.exists()) return file
        val nameWithoutExt = originalName.substringBeforeLast('.')
        val ext = if (originalName.contains('.')) ".${originalName.substringAfterLast('.')}" else ""
        var index = 1
        while (file.exists()) { file = File(parentDir, "${nameWithoutExt}_($index)$ext"); index++ }
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
