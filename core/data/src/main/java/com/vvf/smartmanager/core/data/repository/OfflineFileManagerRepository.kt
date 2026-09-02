package com.vvf.smartmanager.core.data.repository

import com.vvf.smartmanager.core.data.FileManagerRepository
import com.vvf.smartmanager.core.data.permission.StoragePermissionGate
import com.vvf.smartmanager.core.data.storage.StorageManager
import com.vvf.smartmanager.core.database.dao.FileDao
import com.vvf.smartmanager.core.database.dao.SearchFtsDao
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import com.vvf.smartmanager.core.model.CleanerScanResult
import com.vvf.smartmanager.core.model.DuplicateFileGroup
import com.vvf.smartmanager.core.model.DuplicateLevel
import com.vvf.smartmanager.core.model.FileCategory
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.FileOperationProgress
import com.vvf.smartmanager.core.model.FileSortOption
import com.vvf.smartmanager.core.model.StorageBreakdown
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext

/**
 * Concrete repository mediating local database indexes and physical storage access.
 * PROD-007: optional [storagePermissionGate] refuses listing without adequate OS grants.
 */
class OfflineFileManagerRepository(
    private val storageManager: StorageManager,
    private val fileDao: FileDao,
    private val searchFtsDao: SearchFtsDao,
    private val storagePermissionGate: StoragePermissionGate? = null
) : FileManagerRepository {

    override fun getDefaultStoragePath(): String {
        return storageManager.getPrimaryStoragePath()
    }

    override fun getFiles(
        directoryPath: String,
        sortOption: FileSortOption,
        showHidden: Boolean
    ): Flow<List<FileItem>> = flow {
        storagePermissionGate?.requireBrowsePrimaryTree()
        val files = storageManager.listDirectory(directoryPath, sortOption, showHidden)
        emit(files)
    }.flowOn(Dispatchers.IO)

    override fun getCategorizedFiles(
        category: FileCategory,
        sortOption: FileSortOption
    ): Flow<List<FileItem>> = flow {
        if (category == FileCategory.FAVORITES) {
            fileDao.getFavorites().collect { entities ->
                val items = entities.map { entity ->
                    FileItem(
                        path = entity.path,
                        name = entity.name,
                        sizeBytes = entity.sizeBytes,
                        lastModified = entity.modifiedDate,
                        isDirectory = entity.isDirectory,
                        mimeType = entity.mimeType,
                        isFavorite = true
                    )
                }
                emit(items)
            }
        } else if (category == FileCategory.TRASH) {
            emit(storageManager.getTrashFiles())
        } else {
            storagePermissionGate?.requireListMedia()
            val list = storageManager.listCategorizedFiles(category, sortOption)
            emit(list)
        }
    }.flowOn(Dispatchers.IO)

    override fun getStorageBreakdown(): Flow<StorageBreakdown> = flow {
        val breakdown = storageManager.calculateStorageBreakdown()
        emit(breakdown)
    }.flowOn(Dispatchers.IO)

    override suspend fun createDirectory(
        parentPath: String,
        directoryName: String
    ): Result<FileItem> {
        return storageManager.createDirectory(parentPath, directoryName)
    }

    override suspend fun createFile(
        parentPath: String,
        fileName: String,
        content: ByteArray
    ): Result<FileItem> {
        return storageManager.createFile(parentPath, fileName, content)
    }

    override suspend fun deleteFile(path: String, permanent: Boolean): Result<Boolean> {
        return if (permanent) {
            storageManager.permanentDelete(listOf(path)).map { it > 0 }
        } else {
            storageManager.moveToRecycleBin(listOf(path)).map { it > 0 }
        }
    }

    override suspend fun deleteFiles(paths: List<String>, permanent: Boolean): Result<Int> {
        return if (permanent) {
            storageManager.permanentDelete(paths)
        } else {
            storageManager.moveToRecycleBin(paths)
        }
    }

    override suspend fun restoreFromTrash(paths: List<String>): Result<Int> {
        return storageManager.restoreFromRecycleBin(paths)
    }

    override suspend fun emptyTrash(): Result<Boolean> {
        return storageManager.emptyRecycleBin()
    }

    override fun getTrashFiles(): Flow<List<FileItem>> = flow {
        emit(storageManager.getTrashFiles())
    }.flowOn(Dispatchers.IO)

    override suspend fun renameFile(oldPath: String, newName: String): Result<FileItem> {
        return storageManager.rename(oldPath, newName)
    }

    override suspend fun copyFiles(
        sourcePaths: List<String>,
        destinationDirectory: String,
        onProgress: ((FileOperationProgress) -> Unit)?
    ): Result<Int> {
        return storageManager.copyFiles(sourcePaths, destinationDirectory, onProgress)
    }

    override suspend fun moveFiles(
        sourcePaths: List<String>,
        destinationDirectory: String,
        onProgress: ((FileOperationProgress) -> Unit)?
    ): Result<Int> {
        return storageManager.moveFiles(sourcePaths, destinationDirectory, onProgress)
    }

    override suspend fun toggleFavorite(path: String, isFavorite: Boolean): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            fileDao.setFavoriteStatusByPath(path, isFavorite)
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun scanDuplicates(level: DuplicateLevel): Flow<List<DuplicateFileGroup>> {
        return storageManager.scanDuplicatesFlow(level)
    }

    override fun scanJunk(): Flow<CleanerScanResult> {
        return storageManager.scanJunkFlow()
    }

    override suspend fun cleanJunkItems(
        selectedDuplicatePaths: List<String>,
        selectedJunkPaths: List<String>
    ): Result<Long> = withContext(Dispatchers.IO) {
        try {
            var totalReclaimed = 0L
            val allToDelete = (selectedDuplicatePaths + selectedJunkPaths).distinct()

            for (path in allToDelete) {
                val size = storageManager.getFileSize(path)
                val deleted = storageManager.deleteSafely(path)
                if (deleted.isSuccess) {
                    totalReclaimed += size
                    fileDao.deleteByPath(path)
                }
            }
            Result.success(totalReclaimed)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun searchFiles(query: String): Flow<List<FileMetadataEntity>> {
        val sanitizedQuery = query.trim()
        return if (sanitizedQuery.length >= 2 && !sanitizedQuery.contains("*")) {
            searchFtsDao.searchFilesFts("$sanitizedQuery*")
        } else {
            searchFtsDao.searchFilesFallback(sanitizedQuery)
        }
    }
}
