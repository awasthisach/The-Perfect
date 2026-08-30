package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.data.FileManagerRepository
import com.vvf.smartmanager.core.model.CleanerScanResult
import com.vvf.smartmanager.core.model.DuplicateFileGroup
import com.vvf.smartmanager.core.model.DuplicateLevel
import com.vvf.smartmanager.core.model.FileCategory
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.FileOperationProgress
import com.vvf.smartmanager.core.model.FileSortOption
import com.vvf.smartmanager.core.model.StorageBreakdown
import kotlinx.coroutines.flow.Flow

/**
 * Domain use case for live storage metrics and partition breakdown.
 */
class GetStorageOverviewUseCase(
    private val repository: FileManagerRepository
) {
    operator fun invoke(): Flow<StorageBreakdown> = repository.getStorageBreakdown()
}

/**
 * Domain use case for listing directory contents with hierarchy navigation.
 */
class GetDirectoryFilesUseCase(
    private val repository: FileManagerRepository
) {
    operator fun invoke(
        directoryPath: String,
        sortOption: FileSortOption = FileSortOption.NAME_ASC,
        showHidden: Boolean = false
    ): Flow<List<FileItem>> =
        repository.getFiles(directoryPath, sortOption, showHidden)

    fun getDefaultStoragePath(): String = repository.getDefaultStoragePath()
}

/**
 * Domain use case for categorized media and documents.
 */
class GetCategorizedFilesUseCase(
    private val repository: FileManagerRepository
) {
    operator fun invoke(
        category: FileCategory,
        sortOption: FileSortOption = FileSortOption.DATE_DESC
    ): Flow<List<FileItem>> =
        repository.getCategorizedFiles(category, sortOption)
}

/**
 * Domain use case for core file operations: copy, move, rename, create, delete, favorite.
 */
class FileOperationsUseCase(
    private val repository: FileManagerRepository
) {
    suspend fun createDirectory(parentPath: String, directoryName: String): Result<FileItem> =
        repository.createDirectory(parentPath, directoryName)

    suspend fun createFile(parentPath: String, fileName: String, content: ByteArray = ByteArray(0)): Result<FileItem> =
        repository.createFile(parentPath, fileName, content)

    suspend fun renameFile(oldPath: String, newName: String): Result<FileItem> =
        repository.renameFile(oldPath, newName)

    suspend fun copyFiles(
        sourcePaths: List<String>,
        destinationDirectory: String,
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Int> = repository.copyFiles(sourcePaths, destinationDirectory, onProgress)

    suspend fun moveFiles(
        sourcePaths: List<String>,
        destinationDirectory: String,
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Int> = repository.moveFiles(sourcePaths, destinationDirectory, onProgress)

    suspend fun deleteFiles(paths: List<String>, permanent: Boolean = false): Result<Int> =
        repository.deleteFiles(paths, permanent)

    suspend fun toggleFavorite(path: String, isFavorite: Boolean): Result<Boolean> =
        repository.toggleFavorite(path, isFavorite)
}

/**
 * Domain use case for Recycle Bin (Soft Delete, Restore, Empty Trash).
 */
class RecycleBinUseCase(
    private val repository: FileManagerRepository
) {
    fun getTrashFiles(): Flow<List<FileItem>> = repository.getTrashFiles()

    suspend fun moveToTrash(paths: List<String>): Result<Int> =
        repository.deleteFiles(paths, permanent = false)

    suspend fun restoreFromTrash(paths: List<String>): Result<Int> =
        repository.restoreFromTrash(paths)

    suspend fun emptyTrash(): Result<Boolean> =
        repository.emptyTrash()
}

/**
 * Domain use case for Level 1 & Level 2 duplicate detection.
 */
class DuplicateCleanerUseCase(
    private val repository: FileManagerRepository
) {
    operator fun invoke(level: DuplicateLevel): Flow<List<DuplicateFileGroup>> =
        repository.scanDuplicates(level)

    fun scanDuplicates(level: DuplicateLevel): Flow<List<DuplicateFileGroup>> =
        repository.scanDuplicates(level)
}

/**
 * Domain use case for Junk cleaning (Empty folders, cache/temp, APKs, large files).
 */
class JunkCleanerUseCase(
    private val repository: FileManagerRepository
) {
    fun scanJunkFiles(): Flow<CleanerScanResult> = repository.scanJunk()

    suspend fun cleanFiles(paths: List<String>): Result<Long> =
        repository.cleanJunkItems(paths, paths)
}
