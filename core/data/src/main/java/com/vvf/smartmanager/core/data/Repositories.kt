package com.vvf.smartmanager.core.data

import com.vvf.smartmanager.core.model.CleanerScanResult
import com.vvf.smartmanager.core.model.DuplicateFileGroup
import com.vvf.smartmanager.core.model.DuplicateLevel
import com.vvf.smartmanager.core.model.FileCategory
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.FileOperationProgress
import com.vvf.smartmanager.core.model.FileSortOption
import com.vvf.smartmanager.core.model.SearchFilter
import com.vvf.smartmanager.core.model.SearchResultItem
import com.vvf.smartmanager.core.model.StorageBreakdown
import kotlinx.coroutines.flow.Flow

/**
 * Repository contract for managing device storage, file indexing, and categories.
 */
interface FileManagerRepository {
    fun getFiles(
        directoryPath: String,
        sortOption: FileSortOption = FileSortOption.NAME_ASC,
        showHidden: Boolean = false
    ): Flow<List<FileItem>>

    fun getCategorizedFiles(
        category: FileCategory,
        sortOption: FileSortOption = FileSortOption.DATE_DESC
    ): Flow<List<FileItem>>

    fun getStorageBreakdown(): Flow<StorageBreakdown>

    fun getDefaultStoragePath(): String

    suspend fun createDirectory(parentPath: String, directoryName: String): Result<FileItem>

    suspend fun createFile(parentPath: String, fileName: String, content: ByteArray = ByteArray(0)): Result<FileItem>

    suspend fun deleteFile(path: String, permanent: Boolean = false): Result<Boolean>

    suspend fun deleteFiles(paths: List<String>, permanent: Boolean = false): Result<Int>

    suspend fun restoreFromTrash(paths: List<String>): Result<Int>

    suspend fun emptyTrash(): Result<Boolean>

    fun getTrashFiles(): Flow<List<FileItem>>

    suspend fun renameFile(oldPath: String, newName: String): Result<FileItem>

    suspend fun copyFiles(
        sourcePaths: List<String>,
        destinationDirectory: String,
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Int>

    suspend fun moveFiles(
        sourcePaths: List<String>,
        destinationDirectory: String,
        onProgress: ((FileOperationProgress) -> Unit)? = null
    ): Result<Int>

    suspend fun toggleFavorite(path: String, isFavorite: Boolean): Result<Boolean>

    fun scanDuplicates(level: DuplicateLevel): Flow<List<DuplicateFileGroup>>

    fun scanJunk(): Flow<CleanerScanResult>

    suspend fun cleanJunkItems(
        selectedDuplicatePaths: List<String>,
        selectedJunkPaths: List<String>
    ): Result<Long>
}

/**
 * Repository contract for managing encrypted vault operations.
 */
interface VaultRepository {
    fun getVaultItems(): Flow<List<FileItem>>
    suspend fun lockFileInVault(sourcePath: String): Result<Boolean>
    suspend fun restoreFileFromVault(vaultItemId: String, destinationPath: String): Result<Boolean>
    suspend fun verifyVaultCredentials(pin: String): Boolean
    suspend fun setVaultCredentials(pin: String): Boolean
}

/**
 * Repository contract for high-speed offline Core Search (Filename, Metadata, Tags, FTS).
 */
interface SearchRepository {
    fun searchFiles(
        query: String,
        filter: SearchFilter = SearchFilter()
    ): Flow<List<SearchResultItem>>

    fun getSearchHistory(): Flow<List<String>>

    suspend fun saveSearchQuery(query: String)

    suspend fun deleteSearchHistoryItem(query: String)

    suspend fun clearSearchHistory()

    fun getAvailableTags(): Flow<List<String>>

    suspend fun addTagToFile(path: String, tag: String): Result<Boolean>

    suspend fun removeTagFromFile(path: String, tag: String): Result<Boolean>

    fun getTotalIndexedCount(): Flow<Int>

    suspend fun rebuildFtsIndex()

    /** Bounded recent files from Room index for semantic/AI candidates (no full storage walk). */
    suspend fun getRecentIndexedFiles(limit: Int = 400): List<FileItem>
}
