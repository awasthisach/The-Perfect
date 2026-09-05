package com.vvf.smartmanager.core.data.repository

import android.content.Context
import com.vvf.smartmanager.core.data.SearchRepository
import com.vvf.smartmanager.core.data.storage.StorageManager
import com.vvf.smartmanager.core.database.dao.FileDao
import com.vvf.smartmanager.core.database.dao.SearchFtsDao
import com.vvf.smartmanager.core.model.FileCategory
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.SearchFilter
import com.vvf.smartmanager.core.model.SearchResultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Concrete implementation of SearchRepository providing high-performance,
 * offline Core Search (FTS filename/metadata/tags) backed by Room + SQLCipher.
 */
class OfflineSearchRepository(
    private val context: Context,
    private val searchFtsDao: SearchFtsDao,
    private val fileDao: FileDao,
    private val storageManager: StorageManager
) : SearchRepository {

    override fun searchFiles(
        query: String,
        filter: SearchFilter
    ): Flow<List<SearchResultItem>> = flow {
        val safeQuery = query.trim().take(500)
        if (safeQuery.isBlank() && filter.isDefault) {
            emit(emptyList())
            return@flow
        }
        val entitiesFlow = when {
            safeQuery.isNotBlank() -> searchFtsDao.search(safeQuery)
            filter.category != FileCategory.ALL -> {
                val mimePrefix = when (filter.category) {
                    FileCategory.IMAGES -> "image/"
                    FileCategory.VIDEOS -> "video/"
                    FileCategory.AUDIO -> "audio/"
                    FileCategory.DOCUMENTS -> "application/"
                    FileCategory.APKS -> "application/vnd.android.package-archive"
                    else -> ""
                }
                if (mimePrefix.isNotEmpty()) {
                    fileDao.getFilesByType(mimePrefix)
                } else {
                    fileDao.getRecentFiles(500)
                }
            }
            else -> fileDao.getRecentFiles(300)
        }
        entitiesFlow.collect { rawEntities ->
            val results = rawEntities.mapNotNull { entity ->
                val tagList = entity.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val fileItem = FileItem(
                    path = entity.path,
                    name = entity.name,
                    sizeBytes = entity.sizeBytes,
                    lastModified = entity.modifiedDate,
                    isDirectory = entity.isDirectory,
                    mimeType = entity.mimeType,
                    isFavorite = entity.isFavorite,
                    isTrash = entity.isTrash,
                    originalPath = entity.originalPath,
                    deletedTimestamp = entity.deletedTimestamp,
                    md5Hash = entity.md5Hash,
                    tags = tagList
                )
                if (!filter.includeHidden && fileItem.name.startsWith(".")) return@mapNotNull null
                SearchResultItem(fileItem = fileItem, matchedField = "indexed")
            }
            emit(results)
        }
    }

    override fun getSearchHistory(): Flow<List<String>> = searchFtsDao.getSearchHistory()

    override suspend fun saveSearchQuery(query: String) = withContext(Dispatchers.IO) {
        val q = query.trim().take(500)
        if (q.isNotEmpty()) searchFtsDao.insertSearchHistory(q)
    }

    override suspend fun deleteSearchHistoryItem(query: String) = withContext(Dispatchers.IO) {
        searchFtsDao.deleteSearchHistoryItem(query)
    }

    override suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        searchFtsDao.clearSearchHistory()
    }

    override fun getAvailableTags(): Flow<List<String>> = searchFtsDao.getAllTags()

    override suspend fun addTagToFile(path: String, tag: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val entity = fileDao.getByPath(path) ?: return@withContext Result.failure(IllegalArgumentException("not found"))
            val tags = entity.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
            tags.add(tag.trim())
            fileDao.insertOrUpdate(entity.copy(tags = tags.joinToString(",")))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeTagFromFile(path: String, tag: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val entity = fileDao.getByPath(path) ?: return@withContext Result.failure(IllegalArgumentException("not found"))
            val tags = entity.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() && it != tag }.toMutableList()
            fileDao.insertOrUpdate(entity.copy(tags = tags.joinToString(",")))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getTotalIndexedCount(): Flow<Int> = flow {
        emit(fileDao.getTotalFileCount())
    }

    override suspend fun rebuildFtsIndex() = withContext(Dispatchers.IO) {
        searchFtsDao.rebuildFtsIndex()
    }

    override suspend fun getRecentIndexedFiles(limit: Int): List<FileItem> = withContext(Dispatchers.IO) {
        val entities = fileDao.getRecentFiles(limit.coerceIn(1, 2_000)).first()
        entities.map { entity ->
            val tagList = entity.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            FileItem(
                path = entity.path,
                name = entity.name,
                sizeBytes = entity.sizeBytes,
                lastModified = entity.modifiedDate,
                isDirectory = entity.isDirectory,
                mimeType = entity.mimeType,
                isFavorite = entity.isFavorite,
                isTrash = entity.isTrash,
                originalPath = entity.originalPath,
                deletedTimestamp = entity.deletedTimestamp,
                md5Hash = entity.md5Hash,
                tags = tagList
            )
        }
    }
}
