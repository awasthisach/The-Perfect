package com.vvf.smartmanager.core.data.storage

import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import android.webkit.MimeTypeMap
import com.vvf.smartmanager.core.database.dao.FileDao
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import com.vvf.smartmanager.core.model.CleanerScanResult
import com.vvf.smartmanager.core.model.DuplicateFileGroup
import com.vvf.smartmanager.core.model.DuplicateLevel
import com.vvf.smartmanager.core.model.FileCategory
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.FileOperationProgress
import com.vvf.smartmanager.core.model.FileOperationType
import com.vvf.smartmanager.core.model.FileSortOption
import com.vvf.smartmanager.core.model.JunkCategory
import com.vvf.smartmanager.core.model.JunkItem
import com.vvf.smartmanager.core.model.StorageBreakdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.util.UUID

/**
 * Storage Manager responsible for physical filesystem interactions, MediaStore queries,
 * hash calculation, soft delete trash management, and junk scanning.
 *
 * Security invariant: every physical path is canonicalized and must be contained by an
 * approved storage root. Failure to discover approved roots is fail-closed.
 */
class StorageManager(
    private val context: Context,
    private val fileDao: FileDao
) {
    private val trashDirectory: File by lazy {
        val dir = File(context.getExternalFilesDir(null) ?: context.filesDir, ".vvf_trash")
        if (!dir.exists()) dir.mkdirs()
        dir
    }

    fun getAllowedStorageRoots(): List<File> {
        val roots = mutableListOf<File>()
        fun addCanonical(file: File?) {
            if (file == null) return
            try {
                roots += file.canonicalFile
            } catch (_: Exception) {
                // A root that cannot be canonicalized is not approved.
            }
        }
        addCanonical(context.filesDir)
        addCanonical(context.cacheDir)
        context.getExternalFilesDirs(null).filterNotNull().forEach(::addCanonical)
        context.getExternalCacheDirs().filterNotNull().forEach(::addCanonical)
        try {
            addCanonical(Environment.getExternalStorageDirectory())
        } catch (_: Exception) {
            // External root unavailable: remain fail-closed.
        }
        return roots.distinctBy { it.absolutePath }
    }

    fun requireAllowedPhysicalPath(path: String): File {
        require(path.isNotBlank()) { "Physical path cannot be blank" }
        val candidate = File(path).canonicalFile
        val allowedRoots = getAllowedStorageRoots()
        require(allowedRoots.isNotEmpty()) { "Access denied: approved storage roots are unavailable" }
        val isAllowed = allowedRoots.any { root ->
            candidate.absolutePath == root.absolutePath ||
                candidate.absolutePath.startsWith(root.absolutePath + File.separator)
        }
        require(isAllowed) { "Access denied: Path '$path' is outside approved storage boundaries" }
        return candidate
    }

    fun isAllowedPhysicalPath(path: String): Boolean = try {
        requireAllowedPhysicalPath(path)
        true
    } catch (_: Exception) {
        false
    }

    fun getPrimaryStoragePath(): String = try {
        val extDir = Environment.getExternalStorageDirectory()
        if (extDir.exists() && extDir.canRead()) extDir.absolutePath
        else context.getExternalFilesDir(null)?.takeIf { it.exists() }?.absolutePath
            ?: context.filesDir.absolutePath
    } catch (_: Exception) {
        context.filesDir.absolutePath
    }

    fun getFileSize(path: String): Long = try {
        val f = File(path)
        if (f.exists() && !f.isDirectory) f.length() else 0L
    } catch (_: Exception) { 0L }

    fun calculateStorageBreakdown(): StorageBreakdown = try {
        val stat = StatFs(getPrimaryStoragePath())
        val blockSize = stat.blockSizeLong
        val totalBytes = stat.blockCountLong * blockSize
        val freeBytes = stat.availableBlocksLong * blockSize
        val usedBytes = (totalBytes - freeBytes).coerceAtLeast(0L)
        var imgBytes = 0L
        var vidBytes = 0L
        var audBytes = 0L
        var docBytes = 0L
        try {
            imgBytes = queryMediaStoreCategoryBytes(MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
            vidBytes = queryMediaStoreCategoryBytes(MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            audBytes = queryMediaStoreCategoryBytes(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                docBytes = queryMediaStoreCategoryBytes(
                    MediaStore.Files.getContentUri("external"),
                    "${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'application/pdf' OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'application/msword' OR ${MediaStore.Files.FileColumns.MIME_TYPE} LIKE 'application/vnd.openxmlformats-officedocument%'"
                )
            }
        } catch (_: Exception) { }
        StorageBreakdown(
            totalBytes = totalBytes, usedBytes = usedBytes, freeBytes = freeBytes,
            imagesBytes = imgBytes, videosBytes = vidBytes, audioBytes = audBytes,
            docsBytes = docBytes,
            systemBytes = (usedBytes - imgBytes - vidBytes - audBytes - docBytes).coerceAtLeast(0L),
            vaultBytes = 0L
        )
    } catch (_: Exception) {
        // Never fabricate storage statistics in production.
        StorageBreakdown(totalBytes = 0L, usedBytes = 0L, freeBytes = 0L)
    }

    private fun queryMediaStoreCategoryBytes(uri: Uri, selection: String? = null): Long {
        var total = 0L
        val projection = arrayOf(MediaStore.MediaColumns.SIZE)
        context.contentResolver.query(uri, projection, selection, null, null)?.use { cursor ->
            val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
            while (cursor.moveToNext()) total += cursor.getLong(sizeCol)
        }
        return total
    }

    fun listDirectory(directoryPath: String, sortOption: FileSortOption, showHidden: Boolean): List<FileItem> {
        val targetDir = try { requireAllowedPhysicalPath(directoryPath) } catch (_: Exception) { return emptyList() }
        if (!targetDir.exists() || !targetDir.isDirectory) return emptyList()
        val rawFiles = targetDir.listFiles() ?: return emptyList()
        val items = rawFiles.filter { file ->
            (showHidden || !file.name.startsWith(".")) && !file.absolutePath.contains(".vvf_trash")
        }.map { file ->
            FileItem(
                path = file.absolutePath,
                name = file.name,
                sizeBytes = if (file.isDirectory) 0L else file.length(),
                lastModified = file.lastModified(),
                isDirectory = file.isDirectory,
                mimeType = if (file.isDirectory) null else getMimeType(file.name),
                itemCount = if (file.isDirectory) file.listFiles()?.size ?: 0 else 0
            )
        }
        return sortFiles(items, sortOption)
    }

    fun listCategorizedFiles(category: FileCategory, sortOption: FileSortOption): List<FileItem> {
        val results = mutableListOf<FileItem>()
        when (category) {
            FileCategory.ALL -> return listDirectory(getPrimaryStoragePath(), sortOption, false)
            FileCategory.IMAGES -> results.addAll(queryMediaStoreFiles(MediaStore.Images.Media.EXTERNAL_CONTENT_URI))
            FileCategory.VIDEOS -> results.addAll(queryMediaStoreFiles(MediaStore.Video.Media.EXTERNAL_CONTENT_URI))
            FileCategory.AUDIO -> results.addAll(queryMediaStoreFiles(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI))
            FileCategory.DOCUMENTS -> scanDirectoryByExtensions(File(getPrimaryStoragePath()), setOf("pdf","doc","docx","xls","xlsx","ppt","pptx","txt","csv","epub"), results, maxDepth = 4)
            FileCategory.ARCHIVES -> scanDirectoryByExtensions(File(getPrimaryStoragePath()), setOf("zip","rar","7z","tar","gz","bz2","xz"), results, maxDepth = 4)
            FileCategory.APKS -> scanDirectoryByExtensions(File(getPrimaryStoragePath()), setOf("apk","xapk","apks"), results, maxDepth = 4)
            FileCategory.FAVORITES, FileCategory.TRASH, FileCategory.VAULT -> Unit
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
                    val path = if (dataCol != -1) cursor.getString(dataCol) else null
                    if (path != null && isAllowedPhysicalPath(path)) {
                        val name = if (nameCol != -1) cursor.getString(nameCol) else File(path).name
                        list += FileItem(path, name ?: File(path).name, if (sizeCol != -1) cursor.getLong(sizeCol) else 0L, if (dateCol != -1) cursor.getLong(dateCol) * 1000 else 0L, false, mimeType = if (mimeCol != -1) cursor.getString(mimeCol) else getMimeType(name ?: path))
                    }
                }
            }
        } catch (_: Exception) { }
        return list
    }

    private fun scanDirectoryByExtensions(dir: File, extensions: Set<String>, outList: MutableList<FileItem>, currentDepth: Int = 0, maxDepth: Int = 3) {
        if (currentDepth > maxDepth || !dir.exists() || !dir.isDirectory || dir.name.startsWith(".")) return
        val children = dir.listFiles() ?: return
        for (file in children) {
            if (file.isDirectory) {
                if (file.name != "Android" && !file.name.startsWith(".")) scanDirectoryByExtensions(file, extensions, outList, currentDepth + 1, maxDepth)
            } else if (extensions.contains(file.extension.lowercase()) && isAllowedPhysicalPath(file.absolutePath)) {
                outList += FileItem(file.absolutePath, file.name, file.length(), file.lastModified(), false, mimeType = getMimeType(file.name))
            }
        }
    }

    private fun sortFiles(files: List<FileItem>, sortOption: FileSortOption): List<FileItem> {
        val (dirs, nonDirs) = files.partition { it.isDirectory }
        fun sort(list: List<FileItem>) = when (sortOption) {
            FileSortOption.NAME_ASC -> list.sortedBy { it.name.lowercase() }
            FileSortOption.NAME_DESC -> list.sortedByDescending { it.name.lowercase() }
            FileSortOption.DATE_DESC -> list.sortedByDescending { it.lastModified }
            FileSortOption.DATE_ASC -> list.sortedBy { it.lastModified }
            FileSortOption.SIZE_DESC -> list.sortedByDescending { it.sizeBytes }
            FileSortOption.SIZE_ASC -> list.sortedBy { it.sizeBytes }
            FileSortOption.TYPE_ASC -> list.sortedBy { it.name.substringAfterLast('.', "").lowercase() }
        }
        return sort(dirs) + sort(nonDirs)
    }

    suspend fun createDirectory(parentPath: String, directoryName: String): Result<FileItem> = withContext(Dispatchers.IO) {
        try {
            val parent = requireAllowedPhysicalPath(parentPath)
            if (!parent.exists()) return@withContext Result.failure(IllegalArgumentException("Parent directory does not exist"))
            val newDir = requireAllowedPhysicalPath(File(parent, directoryName).absolutePath)
            if (newDir.exists()) return@withContext Result.failure(IllegalArgumentException("Folder already exists with name '$directoryName'"))
            require(newDir.mkdirs() || newDir.exists()) { "Failed to create folder" }
            val item = FileItem(newDir.absolutePath, newDir.name, 0L, newDir.lastModified(), true, itemCount = 0)
            fileDao.insertOrUpdate(FileMetadataEntity(item.path, item.name, parent.absolutePath, 0L, "inode/directory", true, item.lastModified))
            Result.success(item)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun createFile(parentPath: String, fileName: String, content: ByteArray): Result<FileItem> = withContext(Dispatchers.IO) {
        try {
            val parent = requireAllowedPhysicalPath(parentPath)
            if (!parent.exists()) return@withContext Result.failure(IllegalArgumentException("Parent directory does not exist"))
            val newFile = requireAllowedPhysicalPath(File(parent, fileName).absolutePath)
            FileOutputStream(newFile).use { it.write(content) }
            val item = FileItem(newFile.absolutePath, newFile.name, newFile.length(), newFile.lastModified(), false, mimeType = getMimeType(newFile.name))
            fileDao.insertOrUpdate(FileMetadataEntity(item.path, item.name, parent.absolutePath, item.sizeBytes, item.mimeType ?: "application/octet-stream", false, item.lastModified))
            Result.success(item)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun rename(oldPath: String, newName: String): Result<FileItem> = withContext(Dispatchers.IO) {
        try {
            val oldFile = requireAllowedPhysicalPath(oldPath)
            if (!oldFile.exists()) return@withContext Result.failure(IllegalArgumentException("File not found at $oldPath"))
            val parent = oldFile.parentFile ?: return@withContext Result.failure(IllegalStateException("No parent directory"))
            val targetFile = requireAllowedPhysicalPath(File(parent, newName).absolutePath)
            if (targetFile.exists()) return@withContext Result.failure(IllegalArgumentException("A file with name '$newName' already exists"))
            require(oldFile.renameTo(targetFile)) { "Failed to rename file" }
            fileDao.deleteByPath(oldPath)
            val item = FileItem(targetFile.absolutePath, targetFile.name, if (targetFile.isDirectory) 0L else targetFile.length(), targetFile.lastModified(), targetFile.isDirectory, mimeType = if (targetFile.isDirectory) null else getMimeType(targetFile.name))
            fileDao.insertOrUpdate(FileMetadataEntity(item.path, item.name, parent.absolutePath, item.sizeBytes, item.mimeType ?: "inode/directory", item.isDirectory, item.lastModified))
            Result.success(item)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun copyFiles(sourcePaths: List<String>, destinationDirectory: String, onProgress: ((FileOperationProgress) -> Unit)? = null): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val destDir = requireAllowedPhysicalPath(destinationDirectory)
            if (!destDir.exists()) return@withContext Result.failure(IllegalArgumentException("Destination directory does not exist"))
            var count = 0
            for ((index, srcPath) in sourcePaths.withIndex()) {
                val src = requireAllowedPhysicalPath(srcPath)
                if (!src.exists()) continue
                val dest = getUniqueDestinationFile(destDir, src.name)
                onProgress?.invoke(FileOperationProgress(FileOperationType.COPY, src.name, index, sourcePaths.size, if (sourcePaths.isNotEmpty()) index.toFloat() / sourcePaths.size else 0f))
                if (src.isDirectory) src.copyRecursively(dest, overwrite = true) else FileInputStream(src).use { input -> FileOutputStream(dest).use { output -> input.copyTo(output, 64 * 1024) } }
                fileDao.insertOrUpdate(FileMetadataEntity(dest.absolutePath, dest.name, destDir.absolutePath, dest.length(), getMimeType(dest.name), dest.isDirectory, dest.lastModified()))
                count++
            }
            onProgress?.invoke(FileOperationProgress(FileOperationType.COPY, processedCount = sourcePaths.size, totalCount = sourcePaths.size, progressPercentage = 1f, isCompleted = true))
            Result.success(count)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun moveFiles(sourcePaths: List<String>, destinationDirectory: String, onProgress: ((FileOperationProgress) -> Unit)? = null): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val destDir = requireAllowedPhysicalPath(destinationDirectory)
            if (!destDir.exists()) return@withContext Result.failure(IllegalArgumentException("Destination directory does not exist"))
            var count = 0
            for ((index, srcPath) in sourcePaths.withIndex()) {
                val src = requireAllowedPhysicalPath(srcPath)
                if (!src.exists()) continue
                val dest = getUniqueDestinationFile(destDir, src.name)
                onProgress?.invoke(FileOperationProgress(FileOperationType.MOVE, src.name, index, sourcePaths.size, if (sourcePaths.isNotEmpty()) index.toFloat() / sourcePaths.size else 0f))
                val moved = src.renameTo(dest)
                if (!moved) {
                    if (src.isDirectory) { src.copyRecursively(dest, overwrite = true); require(src.deleteRecursively()) { "Failed to remove source directory" } }
                    else { FileInputStream(src).use { input -> FileOutputStream(dest).use { output -> input.copyTo(output, 64 * 1024) } }; require(src.delete()) { "Failed to remove source file" } }
                }
                fileDao.deleteByPath(srcPath)
                fileDao.insertOrUpdate(FileMetadataEntity(dest.absolutePath, dest.name, destDir.absolutePath, dest.length(), getMimeType(dest.name), dest.isDirectory, dest.lastModified()))
                count++
            }
            onProgress?.invoke(FileOperationProgress(FileOperationType.MOVE, processedCount = sourcePaths.size, totalCount = sourcePaths.size, progressPercentage = 1f, isCompleted = true))
            Result.success(count)
        } catch (e: Exception) { Result.failure(e) }
    }

    fun deleteSafely(path: String): Result<Boolean> = try {
        val f = requireAllowedPhysicalPath(path)
        if (!f.exists() || !f.canWrite()) Result.failure(Exception("File not found or not writable"))
        else Result.success(if (f.isDirectory) f.deleteRecursively() else f.delete())
    } catch (e: Exception) { Result.failure(e) }

    suspend fun permanentDelete(paths: List<String>): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var count = 0
            for (path in paths) {
                val file = requireAllowedPhysicalPath(path)
                if (file.exists()) require(if (file.isDirectory) file.deleteRecursively() else file.delete()) { "Failed to delete $path" }
                fileDao.deleteByPath(path)
                count++
            }
            Result.success(count)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun moveToRecycleBin(paths: List<String>): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var count = 0
            val timestamp = System.currentTimeMillis()
            val trashRoot = requireAllowedPhysicalPath(trashDirectory.absolutePath)
            for (path in paths) {
                val src = requireAllowedPhysicalPath(path)
                if (!src.exists()) continue
                val trashFile = File(trashRoot, "${UUID.randomUUID().toString().take(8)}_${src.name}")
                val moved = src.renameTo(trashFile)
                if (!moved) {
                    if (src.isDirectory) { src.copyRecursively(trashFile, overwrite = true); require(src.deleteRecursively()) { "Failed to remove source directory" } }
                    else { FileInputStream(src).use { input -> FileOutputStream(trashFile).use { output -> input.copyTo(output) } }; require(src.delete()) { "Failed to remove source file" } }
                }
                fileDao.deleteByPath(path)
                fileDao.insertOrUpdate(FileMetadataEntity(trashFile.absolutePath, src.name, trashRoot.absolutePath, trashFile.length(), getMimeType(src.name), trashFile.isDirectory, timestamp, isTrash = true, originalPath = path, deletedTimestamp = timestamp))
                count++
            }
            Result.success(count)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun restoreFromRecycleBin(trashPaths: List<String>): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var count = 0
            for (trashPath in trashPaths) {
                val trashFile = requireAllowedPhysicalPath(trashPath)
                val entity = fileDao.getByPath(trashPath) ?: continue
                val originalPath = entity.originalPath ?: continue
                val targetFile = requireAllowedPhysicalPath(originalPath)
                val parent = targetFile.parentFile ?: continue
                if (!parent.exists()) parent.mkdirs()
                val restoreTarget = getUniqueDestinationFile(parent, targetFile.name)
                val restored = trashFile.renameTo(restoreTarget)
                if (!restored) {
                    if (trashFile.isDirectory) { trashFile.copyRecursively(restoreTarget, overwrite = true); require(trashFile.deleteRecursively()) { "Failed to remove trash directory" } }
                    else { FileInputStream(trashFile).use { input -> FileOutputStream(restoreTarget).use { output -> input.copyTo(output) } }; require(trashFile.delete()) { "Failed to remove trash file" } }
                }
                fileDao.deleteByPath(trashPath)
                fileDao.insertOrUpdate(FileMetadataEntity(restoreTarget.absolutePath, restoreTarget.name, parent.absolutePath, restoreTarget.length(), getMimeType(restoreTarget.name), restoreTarget.isDirectory, restoreTarget.lastModified(), isTrash = false))
                count++
            }
            Result.success(count)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun emptyRecycleBin(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            trashDirectory.listFiles()?.forEach { file -> require(if (file.isDirectory) file.deleteRecursively() else file.delete()) { "Failed to empty recycle bin" } }
            fileDao.emptyTrash()
            Result.success(true)
        } catch (e: Exception) { Result.failure(e) }
    }

    fun getTrashFiles(): List<FileItem> = (trashDirectory.listFiles() ?: emptyArray()).map { file ->
        FileItem(file.absolutePath, file.name.substringAfter("_", file.name), file.length(), file.lastModified(), file.isDirectory, isTrash = true, originalPath = file.absolutePath)
    }.sortedByDescending { it.lastModified }

    fun scanDuplicatesFlow(level: DuplicateLevel): Flow<List<DuplicateFileGroup>> = flow {
        val rootDir = try { requireAllowedPhysicalPath(getPrimaryStoragePath()) } catch (_: Exception) { emit(emptyList()); return@flow }
        val allFiles = mutableListOf<File>()
        collectFiles(rootDir, allFiles, 3000)
        val candidateFiles = allFiles.filter { it.isFile && it.length() > 0 && !it.absolutePath.contains(".vvf_trash") }
        val sizeGroups = candidateFiles.groupBy { it.length() }.filter { it.value.size > 1 }
        if (level == DuplicateLevel.LEVEL_1_SIZE) {
            emit(sizeGroups.map { (size, files) ->
                val items = files.map { FileItem(it.absolutePath, it.name, it.length(), it.lastModified(), false, mimeType = getMimeType(it.name)) }.sortedBy { it.lastModified }
                DuplicateFileGroup("size_$size", "$size bytes", DuplicateLevel.LEVEL_1_SIZE, size, items, items.drop(1).map { it.path }.toSet())
            })
            return@flow
        }
        val groups = mutableListOf<DuplicateFileGroup>()
        for ((size, files) in sizeGroups) for ((_, partials) in files.groupBy(::calculatePartialHash).filter { it.value.size > 1 }) for ((hash, identical) in partials.groupBy(::calculateFullSha256).filter { it.value.size > 1 }) {
            val items = identical.map { FileItem(it.absolutePath, it.name, it.length(), it.lastModified(), false, mimeType = getMimeType(it.name), md5Hash = hash, sha256Hash = hash) }.sortedBy { it.lastModified }
            groups += DuplicateFileGroup("hash_${hash.take(12)}", "SHA-256: ${hash.take(12)}...", DuplicateLevel.LEVEL_2_HASH, size, items, items.drop(1).map { it.path }.toSet())
        }
        emit(groups.sortedByDescending { it.sizePerFile * it.duplicateCount })
    }.flowOn(Dispatchers.IO)

    fun scanJunkFlow(): Flow<CleanerScanResult> = flow {
        val rootDir = try { requireAllowedPhysicalPath(getPrimaryStoragePath()) } catch (_: Exception) { emit(CleanerScanResult(isScanning = false, scanProgress = 1f)); return@flow }
        val allFiles = mutableListOf<File>(); val emptyFolders = mutableListOf<JunkItem>(); val largeFiles = mutableListOf<JunkItem>(); val tempFiles = mutableListOf<JunkItem>(); val apkFiles = mutableListOf<JunkItem>()
        emit(CleanerScanResult(isScanning = true, scanProgress = 0.1f))
        collectFilesAndDirectories(rootDir, allFiles, emptyFolders, 4000)
        emit(CleanerScanResult(isScanning = true, scanProgress = 0.5f, emptyFolders = emptyFolders))
        val tempExtensions = setOf("tmp", "temp", "log", "cache", "bak", "old"); val largeThresholdBytes = 100L * 1024 * 1024
        for (file in allFiles) {
            if (!file.isFile || file.absolutePath.contains(".vvf_trash")) continue
            val ext = file.extension.lowercase(); val size = file.length()
            when {
                tempExtensions.contains(ext) || file.name.startsWith("~") || file.name.equals(".DS_Store", true) || file.name.equals("Thumbs.db", true) -> tempFiles += JunkItem(file.absolutePath, file.name, size, JunkCategory.TEMP_CACHE_FILES, "Temporary cache file")
                ext == "apk" -> apkFiles += JunkItem(file.absolutePath, file.name, size, JunkCategory.APK_FILES, "Standalone Android package installer")
                size >= largeThresholdBytes -> largeFiles += JunkItem(file.absolutePath, file.name, size, JunkCategory.LARGE_FILES, "File size exceeds 100 MB")
            }
        }
        emit(CleanerScanResult(emptyFolders = emptyFolders, largeFiles = largeFiles, tempFiles = tempFiles, apkFiles = apkFiles, isScanning = false, scanProgress = 1f, totalScannedCount = allFiles.size + emptyFolders.size))
    }.flowOn(Dispatchers.IO)

    private fun calculatePartialHash(file: File): String = try {
        if (file.length() <= 64 * 1024) return calculateFullSha256(file)
        val md = MessageDigest.getInstance("SHA-256"); val sampleSize = 8 * 1024; val buffer = ByteArray(sampleSize)
        FileInputStream(file).use { fis ->
            var read = fis.read(buffer); if (read > 0) md.update(buffer, 0, read)
            fis.channel.position(file.length() / 2); read = fis.read(buffer); if (read > 0) md.update(buffer, 0, read)
            fis.channel.position((file.length() - sampleSize).coerceAtLeast(0L)); read = fis.read(buffer); if (read > 0) md.update(buffer, 0, read)
        }
        bytesToHex(md.digest())
    } catch (_: Exception) { "${file.length()}_${file.lastModified()}" }

    private fun calculateFullSha256(file: File): String = try {
        val md = MessageDigest.getInstance("SHA-256"); val buffer = ByteArray(64 * 1024)
        FileInputStream(file).use { fis -> var read: Int; while (fis.read(buffer).also { read = it } != -1) md.update(buffer, 0, read) }
        bytesToHex(md.digest())
    } catch (_: Exception) { "${file.length()}_${file.lastModified()}" }

    private fun bytesToHex(bytes: ByteArray): String {
        val chars = CharArray(bytes.size * 2); val digits = "0123456789abcdef"
        for (i in bytes.indices) { val v = bytes[i].toInt() and 0xFF; chars[i * 2] = digits[v ushr 4]; chars[i * 2 + 1] = digits[v and 0x0F] }
        return String(chars)
    }

    private fun collectFiles(dir: File, list: MutableList<File>, maxFiles: Int) {
        if (!dir.exists() || !dir.isDirectory || dir.name.startsWith(".") || list.size >= maxFiles) return
        dir.listFiles()?.forEach { child ->
            if (list.size >= maxFiles) return@forEach
            if (child.isDirectory) { if (child.name != "Android" && !child.name.startsWith(".")) collectFiles(child, list, maxFiles) }
            else if (isAllowedPhysicalPath(child.absolutePath)) list += child
        }
    }

    private fun collectFilesAndDirectories(dir: File, fileList: MutableList<File>, emptyFolderList: MutableList<JunkItem>, maxItems: Int) {
        if (!dir.exists() || !dir.isDirectory || dir.name.startsWith(".") || fileList.size >= maxItems) return
        val children = dir.listFiles() ?: return
        if (children.isEmpty() && dir.absolutePath != getPrimaryStoragePath()) { emptyFolderList += JunkItem(dir.absolutePath, dir.name, 0L, JunkCategory.EMPTY_FOLDERS, "Empty directory without files"); return }
        children.forEach { child ->
            if (fileList.size >= maxItems) return@forEach
            if (child.isDirectory) { if (child.name != "Android" && !child.name.startsWith(".")) collectFilesAndDirectories(child, fileList, emptyFolderList, maxItems) }
            else if (isAllowedPhysicalPath(child.absolutePath)) fileList += child
        }
    }

    private fun getUniqueDestinationFile(parentDir: File, originalName: String): File {
        var file = File(parentDir, originalName); if (!file.exists()) return file
        val nameWithoutExt = originalName.substringBeforeLast('.'); val ext = if (originalName.contains('.')) ".${originalName.substringAfterLast('.')}" else ""; var index = 1
        while (file.exists()) { file = File(parentDir, "${nameWithoutExt}_($index)$ext"); index++ }
        return file
    }

    private fun getMimeType(fileName: String): String {
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
