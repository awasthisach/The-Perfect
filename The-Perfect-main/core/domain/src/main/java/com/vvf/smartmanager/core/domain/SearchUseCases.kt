package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.data.SearchRepository
import com.vvf.smartmanager.core.model.SearchFilter
import com.vvf.smartmanager.core.model.SearchResultItem
import kotlinx.coroutines.flow.Flow

/**
 * Domain Use Case for executing high-speed, multi-parameter offline file search.
 */
class SearchFilesUseCase(
    private val searchRepository: SearchRepository
) {
    operator fun invoke(
        query: String,
        filter: SearchFilter = SearchFilter()
    ): Flow<List<SearchResultItem>> {
        return searchRepository.searchFiles(query, filter)
    }
}

/**
 * Domain Use Case for managing persistent search query history.
 */
class SearchHistoryUseCase(
    private val searchRepository: SearchRepository
) {
    fun getHistory(): Flow<List<String>> = searchRepository.getSearchHistory()

    suspend fun saveQuery(query: String) = searchRepository.saveSearchQuery(query)

    suspend fun deleteHistoryItem(query: String) = searchRepository.deleteSearchHistoryItem(query)

    suspend fun clearHistory() = searchRepository.clearSearchHistory()
}

/**
 * Domain Use Case for organizing and querying file metadata tags.
 */
class TagManagementUseCase(
    private val searchRepository: SearchRepository
) {
    fun getAvailableTags(): Flow<List<String>> = searchRepository.getAvailableTags()

    suspend fun addTagToFile(path: String, tag: String): Result<Boolean> =
        searchRepository.addTagToFile(path, tag)

    suspend fun removeTagFromFile(path: String, tag: String): Result<Boolean> =
        searchRepository.removeTagFromFile(path, tag)
}

/**
 * Domain Use Case for getting total indexed file count and rebuilding FTS search index.
 */
class SearchIndexManagementUseCase(
    private val searchRepository: SearchRepository
) {
    fun getTotalIndexedCount(): Flow<Int> = searchRepository.getTotalIndexedCount()

    suspend fun rebuildFtsIndex() = searchRepository.rebuildFtsIndex()
}
