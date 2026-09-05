package com.vvf.smartmanager.core.data.repository

import android.content.Context
import com.vvf.smartmanager.core.data.SearchRepository
import com.vvf.smartmanager.core.data.storage.StorageManager
import com.vvf.smartmanager.core.database.dao.FileDao
import com.vvf.smartmanager.core.database.dao.SearchFtsDao
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import com.vvf.smartmanager.core.model.DateFilter
import com.vvf.smartmanager.core.model.FileCategory
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.FileSortOption
import com.vvf.smartmanager.core.model.SearchFilter
import com.vvf.smartmanager.core.model.SearchMatchType
import com.vvf.smartmanager.core.model.SearchResultItem
import com.vvf.smartmanager.core.model.SizeFilter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

class OfflineSearchRepository(
    private val context: Context,
    private val searchFtsDao: SearchFtsDao,
    private val fileDao: FileDao,
    private val storageManager: StorageManager
) : SearchRepository {

    private val prefs = context.getSharedPreferences("vvf_search_history_prefs", Context.MODE_PRIVATE)
    private val _historyFlow = MutableStateFlow<List<String>>(loadHistoryFromPrefs())

    private fun loadHistoryFromPrefs(): List<String> {
        val raw = prefs.getString("history_items", "") ?: ""
        return if (raw.isBlank()) emptyList() else raw.split("\u001F").filter { it.isNotBlank() }
    }

    private fun saveHistoryToPrefs(history: List<String>) {
        val raw = history.joinToString("\u001F")
        prefs.edit().putString("history_items", raw).apply()
        _historyFlow.value = history
    }

    override fun getSearchHistory(): Flow<List<String>> = _historyFlow.asStateFlow()

    override suspend fun saveSearchQuery(query: String) = withContext(Dispatchers.IO) {
        val clean = query.trim().take(500)
        if (clean.isBlank()) return@withContext
        val current = _historyFlow.value.toMutableList()
        current.remove(clean)
        current.add(0, clean)
        saveHistoryToPrefs(current.take(25))
    }

    override suspend fun deleteSearchHistoryItem(query: String) = withContext(Dispatchers.IO) {
        val current = _historyFlow.value.toMutableList()
        current.remove(query.trim())
        saveHistoryToPrefs(current)
    }

    override suspend fun clearSearchHistory() = withContext(Dispatchers.IO) {
        saveHistoryToPrefs(emptyList())
    }

    override fun getAvailableTags(): Flow<List<String>> {
        return searchFtsDao.getAllTags().map { rawTagList ->
            rawTagList.flatMap { raw ->
                raw.split(",").map { it.trim() }.filter { it.isNotEmpty() }
            }.distinct().sorted()
        }.flowOn(Dispatchers.IO)
    }

    override suspend fun addTagToFile(path: String, tag: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val cleanTag = tag.trim().lowercase()
            if (cleanTag.isEmpty()) return@withContext Result.success(true)
            val entity = fileDao.getByPath(path)
            if (entity != null) {
                val existingTags = entity.tags.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toMutableSet()
                existingTags.add(cleanTag)
                searchFtsDao.updateTagsByPath(path, existingTags.joinToString(","))
            }
            Result.success(true)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun removeTagFromFile(path: String, tag: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val cleanTag = tag.trim().lowercase()
            val entity = fileDao.getByPath(path)
            if (entity != null) {
                val existingTags = entity.tags.split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }.toMutableSet()
                existingTags.remove(cleanTag)
                searchFtsDao.updateTagsByPath(path, existingTags.joinToString(","))
            }
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
        val entitiesFlow = when {
            trimmedQuery.isNotEmpty() -> {
                val sanitizedFtsQuery = sanitizeFtsQuery(trimmedQuery)
                val ftsFlow = if (sanitizedFtsQuery.isNotEmpty()) {
                    searchFtsDao.searchFilesFts(sanitizedFtsQuery)
                } else {
                    searchFtsDao.searchFilesFallback(trimmedQuery)
                }
                val fallbackFlow = searchFtsDao.searchFilesFallback(trimmedQuery)
                ftsFlow.combine(fallbackFlow) { ftsList, fallbackList ->
                    (ftsList + fallbackList).distinctBy { it.path }
                }
            }
            filter.selectedTags.isNotEmpty() -> searchFtsDao.searchByTag(filter.selectedTags.first())
            filter.category != FileCategory.ALL -> {
                val mimePrefix = when (filter.category) {
                    FileCategory.IMAGES -> "image/"
                    FileCategory.VIDEOS -> "video/"
                    FileCategory.AUDIO -> "audio/"
                    FileCategory.DOCUMENTS -> "application/"
                    FileCategory.APKS -> "application/vnd.android.package-archive"
                    else -> ""
                }
                if (mimePrefix.isNotEmpty()) fileDao.getFilesByType(mimePrefix) else fileDao.getRecentFiles(500)
            }
            else -> fileDao.getRecentFiles(300)
        }

        entitiesFlow.collect { rawEntities ->
            val now = System.currentTimeMillis()
            val dayMillis = 24 * 60 * 60 * 1000L
            val results = rawEntities.mapNotNull { entity ->
                val file = File(entity.path)
                val exists = file.exists()
                val sizeBytes = if (exists && !entity.isDirectory) file.length() else entity.sizeBytes
                val lastModified = if (exists) file.lastModified() else entity.modifiedDate
                val tagList = entity.tags.split(",").map { it.trim() }.filter { it.isNotEmpty() }
                val fileItem = FileItem(
                    path = entity.path,
                    name = entity.name,
                    sizeBytes = sizeBytes,
                    lastModified = lastModified,
                    isDirectory = entity.isDirectory,
                    mimeType = entity.mimeType,
                    isFavorite = entity.isFavorite,
                    isTrash = entity.isTrash,
                    originalPath = entity.originalPath,
                    deletedTimestamp = entity.deletedTimestamp,
                    md5Hash = entity.md5Hash,
                    tags = tagList
                )
                if (!filter.includeHidden && fileItem.isHidden) return@mapNotNull null
                if (!matchesCategory(fileItem, filter.category)) return@mapNotNull null
                if (filter.sizeFilter != SizeFilter.ANY) {
                    if (fileItem.sizeBytes < filter.sizeFilter.minBytes || fileItem.sizeBytes > filter.sizeFilter.maxBytes) {
                        return@mapNotNull null
                    }
                }
                if (filter.dateFilter != DateFilter.ANY) {
                    val cutoff = when (filter.dateFilter) {
                        DateFilter.TODAY -> now - dayMillis
                        DateFilter.LAST_7_DAYS -> now - (7 * dayMillis)
                        DateFilter.LAST_30_DAYS -> now - (30 * dayMillis)
                        DateFilter.LAST_YEAR -> now - (365 * dayMillis)
                        DateFilter.ANY -> 0L
                    }
                    if (fileItem.lastModified < cutoff) return@mapNotNull null
                }
                if (filter.selectedTags.isNotEmpty()) {
                    val fileTagSet = tagList.map { it.lowercase() }.toSet()
                    val requiredTagSet = filter.selectedTags.map { it.lowercase() }.toSet()
                    if (!fileTagSet.containsAll(requiredTagSet)) return@mapNotNull null
                }
                val matchType = determineMatchType(fileItem, trimmedQuery)
                SearchResultItem(
                    fileItem = fileItem,
                    matchType = matchType,
                    matchedSnippet = generateMatchSnippet(fileItem, trimmedQuery, matchType)
                )
            }
            emit(sortSearchResults(results, filter.sortOption))
        }
    }.flowOn(Dispatchers.IO)

    private fun sanitizeFtsQuery(query: String): String {
        val clean = query.replace(Regex("[^a-zA-Z0-9_\\s]"), " ").trim()
        if (clean.isBlank()) return ""
        val tokens = clean.split("\\s+".toRegex()).filter { it.length >= 2 }
        if (tokens.isEmpty()) return ""
        return tokens.joinToString(" ") { "$it*" }
    }

    private fun matchesCategory(item: FileItem, category: FileCategory): Boolean {
        if (category == FileCategory.ALL) return true
        if (category == FileCategory.FAVORITES) return item.isFavorite
        if (category == FileCategory.TRASH) return item.isTrash
        val ext = item.extension.lowercase()
        val mime = item.mimeType?.lowercase() ?: ""
        return when (category) {
            FileCategory.IMAGES -> mime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "gif", "bmp", "svg", "heic")
            FileCategory.VIDEOS -> mime.startsWith("video/") || ext in listOf("mp4", "mkv", "avi", "mov", "webm", "3gp", "flv")
            FileCategory.AUDIO -> mime.startsWith("audio/") || ext in listOf("mp3", "wav", "aac", "flac", "ogg", "m4a", "wma")
            FileCategory.DOCUMENTS -> mime.startsWith("text/") || mime.contains("pdf") || mime.contains("document") || mime.contains("sheet") || mime.contains("presentation") || ext in listOf("pdf", "doc", "docx", "txt", "xlsx", "xls", "pptx", "ppt", "csv", "json", "xml", "epub", "md")
            FileCategory.ARCHIVES -> mime.contains("zip") || mime.contains("compressed") || mime.contains("tar") || ext in listOf("zip", "rar", "7z", "tar", "gz", "bz2", "xz")
            FileCategory.APKS -> mime.contains("package-archive") || ext in listOf("apk", "xapk", "apks")
            FileCategory.VAULT -> item.isEncrypted
            else -> true
        }
    }

    private fun determineMatchType(item: FileItem, query: String): SearchMatchType {
        if (query.isEmpty()) return SearchMatchType.METADATA
        val lowerQuery = query.lowercase()
        if (item.name.lowercase().contains(lowerQuery)) return SearchMatchType.FILENAME
        if (item.tags.any { it.lowercase().contains(lowerQuery) }) return SearchMatchType.TAG
        if (item.extension.lowercase().contains(lowerQuery) || (item.mimeType?.lowercase()?.contains(lowerQuery) == true)) return SearchMatchType.METADATA
        return SearchMatchType.FTS
    }

    private fun generateMatchSnippet(item: FileItem, query: String, matchType: SearchMatchType): String? {
        if (query.isEmpty()) return null
        return when (matchType) {
            SearchMatchType.FILENAME -> "Matched in filename: ${item.name}"
            SearchMatchType.TAG -> "Tagged with: ${item.tags.joinToString(", ")}"
            SearchMatchType.METADATA -> "Metadata/Type: ${item.mimeType ?: item.extension}"
            SearchMatchType.FTS -> "Matched in indexed content"
        }
    }

    private fun sortSearchResults(list: List<SearchResultItem>, sortOption: FileSortOption): List<SearchResultItem> {
        return when (sortOption) {
            FileSortOption.NAME_ASC -> list.sortedWith(compareBy({ !it.fileItem.isDirectory }, { it.fileItem.name.lowercase() }))
            FileSortOption.NAME_DESC -> list.sortedWith(compareBy<SearchResultItem> { !it.fileItem.isDirectory }.thenByDescending { it.fileItem.name.lowercase() })
            FileSortOption.DATE_DESC -> list.sortedWith(compareBy<SearchResultItem> { !it.fileItem.isDirectory }.thenByDescending { it.fileItem.lastModified })
            FileSortOption.DATE_ASC -> list.sortedWith(compareBy<SearchResultItem> { !it.fileItem.isDirectory }.thenBy { it.fileItem.lastModified })
            FileSortOption.SIZE_DESC -> list.sortedWith(compareBy<SearchResultItem> { !it.fileItem.isDirectory }.thenByDescending { it.fileItem.sizeBytes })
            FileSortOption.SIZE_ASC -> list.sortedWith(compareBy<SearchResultItem> { !it.fileItem.isDirectory }.thenBy { it.fileItem.sizeBytes })
            FileSortOption.TYPE_ASC -> list.sortedWith(compareBy({ !it.fileItem.isDirectory }, { it.fileItem.extension }, { it.fileItem.name.lowercase() }))
        }
    }
}
