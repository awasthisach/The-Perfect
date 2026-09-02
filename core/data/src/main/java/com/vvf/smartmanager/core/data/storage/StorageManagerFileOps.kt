package com.vvf.smartmanager.core.data.storage

import android.content.Context
import com.vvf.smartmanager.core.database.dao.FileDao
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.FileOperationProgress
import com.vvf.smartmanager.core.model.FileOperationType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.UUID

/**
 * Create / rename / copy / move / delete / trash operations.
 */
open class StorageManagerFileOps(
    context: Context,
    fileDao: FileDao
) : StorageManagerImpl(context, fileDao) {

    suspend fun createDirectory(parentPath: String, directoryName: String): Result<FileItem> = withContext(Dispatchers.IO) {
        try {
            val parent = requireAllowedPhysicalPath(parentPath)
            if (!parent.exists()) parent.mkdirs()
            val newDir = requireAllowedPhysicalPath(File(parent, directoryName).absolutePath)
            if (newDir.exists()) return@withContext Result.failure(IllegalArgumentException("Folder already exists with name '$directoryName'"))
            if (!(newDir.mkdirs() || newDir.exists())) return@withContext Result.failure(IllegalStateException("Failed to create folder"))
            val item = FileItem(path = newDir.absolutePath, name = newDir.name, sizeBytes = 0L, lastModified = newDir.lastModified(), isDirectory = true, itemCount = 0)
            fileDao.insertOrUpdate(FileMetadataEntity(path = item.path, name = item.name, parentPath = parent.absolutePath, sizeBytes = 0L, mimeType = "inode/directory", isDirectory = true, modifiedDate = item.lastModified))
            Result.success(item)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun createFile(parentPath: String, fileName: String, content: ByteArray): Result<FileItem> = withContext(Dispatchers.IO) {
        try {
            val parent = requireAllowedPhysicalPath(parentPath)
            if (!parent.exists()) parent.mkdirs()
            val newFile = requireAllowedPhysicalPath(File(parent, fileName).absolutePath)
            FileOutputStream(newFile).use { it.write(content) }
            val item = FileItem(path = newFile.absolutePath, name = newFile.name, sizeBytes = newFile.length(), lastModified = newFile.lastModified(), isDirectory = false, mimeType = getMimeType(newFile.name))
            fileDao.insertOrUpdate(FileMetadataEntity(path = item.path, name = item.name, parentPath = parent.absolutePath, sizeBytes = item.sizeBytes, mimeType = item.mimeType ?: "application/octet-stream", isDirectory = false, modifiedDate = item.lastModified))
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
            if (!oldFile.renameTo(targetFile)) return@withContext Result.failure(IllegalStateException("Failed to rename file"))
            fileDao.deleteByPath(oldPath)
            val item = FileItem(path = targetFile.absolutePath, name = targetFile.name, sizeBytes = if (targetFile.isDirectory) 0L else targetFile.length(), lastModified = targetFile.lastModified(), isDirectory = targetFile.isDirectory, mimeType = if (targetFile.isDirectory) null else getMimeType(targetFile.name))
            fileDao.insertOrUpdate(FileMetadataEntity(path = item.path, name = item.name, parentPath = parent.absolutePath, sizeBytes = item.sizeBytes, mimeType = item.mimeType ?: "inode/directory", isDirectory = item.isDirectory, modifiedDate = item.lastModified))
            Result.success(item)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun copyFiles(sourcePaths: List<String>, destinationDirectory: String, onProgress: ((FileOperationProgress) -> Unit)? = null): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val destDir = requireAllowedPhysicalPath(destinationDirectory)
            if (!destDir.exists()) destDir.mkdirs()
            var count = 0
            val total = sourcePaths.size
            for ((index, srcPath) in sourcePaths.withIndex()) {
                val src = requireAllowedPhysicalPath(srcPath)
                if (!src.exists()) continue
                val dest = getUniqueDestinationFile(destDir, src.name)
                onProgress?.invoke(FileOperationProgress(operation = FileOperationType.COPY, currentFileName = src.name, processedCount = index, totalCount = total, progressPercentage = if (total > 0) index.toFloat() / total else 0f))
                if (src.isDirectory) src.copyRecursively(dest, overwrite = true)
                else FileInputStream(src).use { input -> FileOutputStream(dest).use { output -> input.copyTo(output, bufferSize = 64 * 1024) } }
                fileDao.insertOrUpdate(FileMetadataEntity(path = dest.absolutePath, name = dest.name, parentPath = destDir.absolutePath, sizeBytes = dest.length(), mimeType = getMimeType(dest.name), isDirectory = dest.isDirectory, modifiedDate = dest.lastModified()))
                count++
            }
            onProgress?.invoke(FileOperationProgress(operation = FileOperationType.COPY, processedCount = total, totalCount = total, progressPercentage = 1f, isCompleted = true))
            Result.success(count)
        } catch (e: Exception) {
            onProgress?.invoke(FileOperationProgress(operation = FileOperationType.COPY, isCompleted = true, errorMessage = e.localizedMessage))
            Result.failure(e)
        }
    }

    suspend fun moveFiles(sourcePaths: List<String>, destinationDirectory: String, onProgress: ((FileOperationProgress) -> Unit)? = null): Result<Int> = withContext(Dispatchers.IO) {
        try {
            val destDir = requireAllowedPhysicalPath(destinationDirectory)
            if (!destDir.exists()) destDir.mkdirs()
            var count = 0
            val total = sourcePaths.size
            for ((index, srcPath) in sourcePaths.withIndex()) {
                val src = requireAllowedPhysicalPath(srcPath)
                if (!src.exists()) continue
                val dest = getUniqueDestinationFile(destDir, src.name)
                onProgress?.invoke(FileOperationProgress(operation = FileOperationType.MOVE, currentFileName = src.name, processedCount = index, totalCount = total, progressPercentage = if (total > 0) index.toFloat() / total else 0f))
                val renamed = src.renameTo(dest)
                if (!renamed) {
                    if (src.isDirectory) { src.copyRecursively(dest, overwrite = true); src.deleteRecursively() }
                    else { FileInputStream(src).use { input -> FileOutputStream(dest).use { output -> input.copyTo(output, bufferSize = 64 * 1024) } }; src.delete() }
                }
                fileDao.deleteByPath(srcPath)
                fileDao.insertOrUpdate(FileMetadataEntity(path = dest.absolutePath, name = dest.name, parentPath = destDir.absolutePath, sizeBytes = dest.length(), mimeType = getMimeType(dest.name), isDirectory = dest.isDirectory, modifiedDate = dest.lastModified()))
                count++
            }
            onProgress?.invoke(FileOperationProgress(operation = FileOperationType.MOVE, processedCount = total, totalCount = total, progressPercentage = 1f, isCompleted = true))
            Result.success(count)
        } catch (e: Exception) {
            onProgress?.invoke(FileOperationProgress(operation = FileOperationType.MOVE, isCompleted = true, errorMessage = e.localizedMessage))
            Result.failure(e)
        }
    }

    fun deleteSafely(path: String): Result<Boolean> = try {
        val f = requireAllowedPhysicalPath(path)
        if (f.exists() && f.canWrite()) {
            val deleted = if (f.isDirectory) f.deleteRecursively() else f.delete()
            if (deleted) Result.success(true) else Result.failure(Exception("Failed to delete"))
        } else Result.failure(Exception("File not found or not writable"))
    } catch (e: Exception) { Result.failure(e) }

    suspend fun permanentDelete(paths: List<String>): Result<Int> = withContext(Dispatchers.IO) {
        try {
            var count = 0
            for (path in paths) {
                val file = requireAllowedPhysicalPath(path)
                if (file.exists()) { if (file.isDirectory) file.deleteRecursively() else file.delete() }
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
            for (path in paths) {
                val src = requireAllowedPhysicalPath(path)
                if (!src.exists()) continue
                val trashId = UUID.randomUUID().toString().take(8)
                val trashFile = File(trashDirectory, "${trashId}_${src.name}")
                val moved = src.renameTo(trashFile)
                if (!moved) {
                    if (src.isDirectory) { src.copyRecursively(trashFile, overwrite = true); src.deleteRecursively() }
                    else { FileInputStream(src).use { input -> FileOutputStream(trashFile).use { output -> input.copyTo(output) } }; src.delete() }
                }
                fileDao.deleteByPath(path)
                fileDao.insertOrUpdate(FileMetadataEntity(path = trashFile.absolutePath, name = src.name, parentPath = trashDirectory.absolutePath, sizeBytes = trashFile.length(), mimeType = getMimeType(src.name), isDirectory = trashFile.isDirectory, modifiedDate = timestamp, isTrash = true, originalPath = path, deletedTimestamp = timestamp))
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
                val entity = fileDao.getByPath(trashPath)
                val originalPath = entity?.originalPath ?: continue
                val targetFile = requireAllowedPhysicalPath(originalPath)
                val parent = targetFile.parentFile
                if (parent != null && !parent.exists()) parent.mkdirs()
                val restoreTarget = getUniqueDestinationFile(parent ?: File(getPrimaryStoragePath()), targetFile.name)
                val restored = trashFile.renameTo(restoreTarget)
                if (!restored) {
                    if (trashFile.isDirectory) { trashFile.copyRecursively(restoreTarget, overwrite = true); trashFile.deleteRecursively() }
                    else { FileInputStream(trashFile).use { input -> FileOutputStream(restoreTarget).use { output -> input.copyTo(output) } }; trashFile.delete() }
                }
                fileDao.deleteByPath(trashPath)
                fileDao.insertOrUpdate(FileMetadataEntity(path = restoreTarget.absolutePath, name = restoreTarget.name, parentPath = restoreTarget.parentFile?.absolutePath ?: "", sizeBytes = restoreTarget.length(), mimeType = getMimeType(restoreTarget.name), isDirectory = restoreTarget.isDirectory, modifiedDate = restoreTarget.lastModified(), isTrash = false, originalPath = null, deletedTimestamp = null))
                count++
            }
            Result.success(count)
        } catch (e: Exception) { Result.failure(e) }
    }

    suspend fun emptyRecycleBin(): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val files = trashDirectory.listFiles() ?: emptyArray()
            for (file in files) { if (file.isDirectory) file.deleteRecursively() else file.delete() }
            fileDao.emptyTrash()
            Result.success(true)
        } catch (e: Exception) { Result.failure(e) }
    }

    fun getTrashFiles(): List<FileItem> {
        val trashFiles = trashDirectory.listFiles() ?: emptyArray()
        return trashFiles.map { file ->
            val cleanName = if (file.name.contains("_")) file.name.substringAfter("_") else file.name
            FileItem(path = file.absolutePath, name = cleanName, sizeBytes = file.length(), lastModified = file.lastModified(), isDirectory = file.isDirectory, isTrash = true, originalPath = file.absolutePath)
        }.sortedByDescending { it.lastModified }
    }
}
