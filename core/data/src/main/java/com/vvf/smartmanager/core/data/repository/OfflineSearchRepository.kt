package com.vvf.smartmanager.core.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.vvf.smartmanager.core.data.SearchRepository
import com.vvf.smartmanager.core.data.storage.StorageManager
import com.vvf.smartmanager.core.database.dao.FileDao
import com.vvf.smartmanager.core.database.dao.SearchFtsDao
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import com.vvf.smartmanager.core.model.FileCategory
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.FileSortOption
import com.vvf.smartmanager.core.model.SearchFilter
import com.vvf.smartmanager.core.model.SearchResultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Concrete implementation of SearchRepository providing high-performance,
 * offline Core Search (Filename, Metadata, Tags, FTS) backed by Room + SQLCipher.
 */
class OfflineSearchRepository(
    private val context: Context,
    private val searchFtsDao: SearchFtsDao,
    private val fileDao: FileDao,
    private val storageManager: StorageManager
) : SearchRepository {

    private val historyPrefs: SharedPreferences =
        context.getSharedPreferences("vvf_search_history", Context.MODE_PRIVATE)

    private val _historyFlow = MutableStateFlow<List<String>>(loadHistoryFromPrefs())

    private fun loadHistoryFromPrefs(): List<String> {
        val raw = historyPrefs.getString("history", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return raw.split("\u0001").map { it.trim() }.filter { it.isNotEmpty() }.take(50)
    }

    private fun saveHistoryToPrefs(history: List<String>) {
        historyPrefs.edit().putString("history", history.joinToString("\u0001")).apply()
        _historyFlow.value = history
    }

    override fun getSearchHistory(): Flow<List<String>> = _historyFlow.asStateFlow()

    override suspend fun saveSearchQuery(query: String) = withContext(Dispatchers.IO) {
        val trimmed = query.trim().take(500)
        if (trimmed.isEmpty()) return@withContext
        val current = _historyFlow.value.toMutableList()
        current.remove(trimmed)
        current.add(0, trimmed)
        saveHistoryToPrefs(current.take(50))
    }

    override suspend fun deleteSearchHistoryItem(query: String) = withContext(Dispatchers.IO) {
        val current = _historyFlow.value.toMutableList()
        current.remove(query)
        saveHistoryToPrefs(current)
    }

    override suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        saveHistoryToPrefs(emptyList())
    }

    override fun getAvailableTags(): Flow<List<String>> {
        return searchFtsDao.getAllTags().map { rawTagList ->
            rawTagList.flatMap { it.split(",") }
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .distinct()
                .sorted()
        }
    }

    override suspend fun addTagToFile(path: String, tag: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val entity = fileDao.getByPath(path) ?: return@withContext Result.failure(IllegalArgumentException("File not found"))
            val tags = entity.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }.toMutableSet()
            tags.add(tag.trim())
            val updatedTags = tags.joinToString(",")
            searchFtsDao.updateTagsByPath(path, updatedTags)
            fileDao.insertOrUpdate(entity.copy(tags = updatedTags))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeTagFromFile(path: String, tag: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val entity = fileDao.getByPath(path) ?: return@withContext Result.failure(IllegalArgumentException("File not found"))
            val tags = entity.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() && it != tag }
            val updatedTags = tags.joinToString(",")
            searchFtsDao.updateTagsByPath(path, updatedTags)
            fileDao.insertOrUpdate(entity.copy(tags = updatedTags))
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun getTotalIndexedCount(): Flow<Int> {
        return searchFtsDao.getTotalIndexedCount().flowOn(Dispatchers.IO)
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

    override fun searchFiles(query: String, filter: SearchFilter): Flow<List<SearchResultItem>> = flow {
        val trimmedQuery = query.trim().take(500)
        val entitiesFlow: Flow<List<FileMetadataEntity>> = when {
            trimmedQuery.isNotBlank() -> {
                val sanitizedFtsQuery = trimmedQuery
                    .replace("\"", " ")
                    .split(Regex("\\s+"))
                    .filter { it.isNotBlank() }
                    .joinToString(" ") { token ->
                        val clean = token.replace(Regex("[^\\w]"), "")
                        if (clean.isNotEmpty()) "$clean*" else token
                    }
                if (sanitizedFtsQuery.isNotBlank()) {
                    searchFtsDao.searchFilesFts(sanitizedFtsQuery)
                } else {
                    searchFtsDao.searchFilesFallback(trimmedQuery)
                }
            }
            filter.selectedTags.isNotEmpty() -> {
                val firstTag = filter.selectedTags.first()
                searchFtsDao.searchByTag(firstTag)
            }
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
            else -> {
                fileDao.getRecentFiles(300)
            }
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
                if (!filter.includeHidden && fileItem.name.startsWith(".")) {
                    return@mapNotNull null
                }
                if (filter.selectedTags.isNotEmpty() && filter.selectedTags.none { t -> tagList.any { it.equals(t, true) } }) {
                    return@mapNotNull null
                }
                SearchResultItem(fileItem = fileItem, matchedField = if (trimmedQuery.isNotBlank()) "fts" else "filter")
            }
            emit(sortSearchResults(results, filter.sortOption))
        }
    }.flowOn(Dispatchers.IO)

    private fun sortSearchResults(
        list: List<SearchResultItem>,
        sortOption: FileSortOption
    ): List<SearchResultItem> {
        return when (sortOption) {
            FileSortOption.NAME_ASC -> list.sortedWith(compareBy({ !it.fileItem.isDirectory }, { it.fileItem.name.lowercase() }))
            FileSortOption.NAME_DESC -> list.sortedWith(compareBy({ !it.fileItem.isDirectory }, { it.fileItem.name.lowercase().reversed() }))
            FileSortOption.DATE_DESC -> list.sortedWith(compareBy({ !it.fileItem.isDirectory }, { -it.fileItem.lastModified }))
            FileSortOption.DATE_ASC -> list.sortedWith(compareBy({ !it.fileItem.isDirectory }, { it.fileItem.lastModified }))
            FileSortOption.SIZE_DESC -> list.sortedWith(compareBy({ !it.fileItem.isDirectory }, { -it.fileItem.sizeBytes }))
            FileSortOption.SIZE_ASC -> list.sortedWith(compareBy({ !it.fileItem.isDirectory }, { it.fileItem.sizeBytes }))
            FileSortOption.TYPE_ASC -> list.sortedWith(compareBy({ !it.fileItem.isDirectory }, { it.fileItem.name.lowercase() }))
        }
    }
}
