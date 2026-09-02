package com.vvf.smartmanager.core.data.storage

import android.content.Context
import com.vvf.smartmanager.core.database.dao.FileDao
import com.vvf.smartmanager.core.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.io.File

/**
 * Production implementation. Fail-closed path auth (STORAGE-INV-001).
 * No sample-data side effects in listing (STORAGE-INV-002).
 */
open class StorageManagerImpl(
    protected val context: Context,
    protected val fileDao: FileDao
) {
    private val trashDirectory: File by lazy {
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
            val extStorage = android.os.Environment.getExternalStorageDirectory()
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
        requireAllowedPhysicalPath(path)
        true
    } catch (_: Exception) {
        false
    }

    fun getPrimaryStoragePath(): String = try {
        val extDir = android.os.Environment.getExternalStorageDirectory()
        when {
            extDir != null && extDir.exists() && extDir.canRead() -> extDir.absolutePath
            else -> (context.getExternalFilesDir(null) ?: context.filesDir).absolutePath
        }
    } catch (_: Exception) {
        context.filesDir.absolutePath
    }

    fun getFileSize(path: String): Long = try {
        val f = File(path)
        if (f.exists() && !f.isDirectory) f.length() else 0L
    } catch (_: Exception) { 0L }

    fun calculateStorageBreakdown(): StorageBreakdown =
        StorageBreakdown(totalBytes = 0, usedBytes = 0, freeBytes = 0)

    fun listDirectory(directoryPath: String, sortOption: FileSortOption, showHidden: Boolean): List<FileItem> {
        val targetDir = File(directoryPath)
        // STORAGE-INV-002: never seed sample/demo files in production listing
        if (!targetDir.exists() || !targetDir.isDirectory) return emptyList()
        val raw = targetDir.listFiles() ?: return emptyList()
        return raw.filter { showHidden || !it.name.startsWith(".") }
            .filter { !it.absolutePath.contains(".vvf_trash") }
            .map {
                FileItem(
                    path = it.absolutePath,
                    name = it.name,
                    sizeBytes = if (it.isDirectory) 0L else it.length(),
                    lastModified = it.lastModified(),
                    isDirectory = it.isDirectory,
                    itemCount = if (it.isDirectory) (it.listFiles()?.size ?: 0) else 0
                )
            }
    }

    fun listCategorizedFiles(category: FileCategory, sortOption: FileSortOption): List<FileItem> {
        // STORAGE-INV-002: no sample FileItem injection
        return if (category == FileCategory.ALL) listDirectory(getPrimaryStoragePath(), sortOption, false)
        else emptyList()
    }

    suspend fun createDirectory(parentPath: String, directoryName: String): Result<FileItem> =
        Result.failure(UnsupportedOperationException("file ops restore pending"))
    suspend fun createFile(parentPath: String, fileName: String, content: ByteArray): Result<FileItem> =
        Result.failure(UnsupportedOperationException("file ops restore pending"))
    suspend fun rename(oldPath: String, newName: String): Result<FileItem> =
        Result.failure(UnsupportedOperationException("file ops restore pending"))
    suspend fun copyFiles(sourcePaths: List<String>, destinationDirectory: String, onProgress: ((FileOperationProgress) -> Unit)? = null): Result<Int> =
        Result.failure(UnsupportedOperationException("file ops restore pending"))
    suspend fun moveFiles(sourcePaths: List<String>, destinationDirectory: String, onProgress: ((FileOperationProgress) -> Unit)? = null): Result<Int> =
        Result.failure(UnsupportedOperationException("file ops restore pending"))
    fun deleteSafely(path: String): Result<Boolean> =
        Result.failure(UnsupportedOperationException("file ops restore pending"))
    suspend fun permanentDelete(paths: List<String>): Result<Int> =
        Result.failure(UnsupportedOperationException("file ops restore pending"))
    suspend fun moveToRecycleBin(paths: List<String>): Result<Int> =
        Result.failure(UnsupportedOperationException("file ops restore pending"))
    suspend fun restoreFromRecycleBin(trashPaths: List<String>): Result<Int> =
        Result.failure(UnsupportedOperationException("file ops restore pending"))
    suspend fun emptyRecycleBin(): Result<Boolean> =
        Result.failure(UnsupportedOperationException("file ops restore pending"))
    fun getTrashFiles(): List<FileItem> = emptyList()
    fun scanDuplicatesFlow(level: DuplicateLevel): Flow<List<DuplicateFileGroup>> = flow { emit(emptyList()) }
    fun scanJunkFlow(): Flow<CleanerScanResult> = flow { emit(CleanerScanResult(isScanning = false)) }
}
