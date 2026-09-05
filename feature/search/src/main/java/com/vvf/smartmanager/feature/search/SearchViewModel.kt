package com.vvf.smartmanager.feature.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vvf.smartmanager.core.domain.AiIntelligenceUseCase
import com.vvf.smartmanager.core.domain.FileOperationsUseCase
import com.vvf.smartmanager.core.domain.SearchFilesUseCase
import com.vvf.smartmanager.core.domain.SearchHistoryUseCase
import com.vvf.smartmanager.core.domain.SearchIndexManagementUseCase
import com.vvf.smartmanager.core.domain.SemanticSearchUseCase
import com.vvf.smartmanager.core.domain.TagManagementUseCase
import com.vvf.smartmanager.core.model.AiSuggestedTag
import com.vvf.smartmanager.core.model.DateFilter
import com.vvf.smartmanager.core.model.FileCategory
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.FileSortOption
import com.vvf.smartmanager.core.model.SearchFilter
import com.vvf.smartmanager.core.model.SemanticSearchOptions
import com.vvf.smartmanager.core.model.SemanticSearchResult
import com.vvf.smartmanager.core.model.SizeFilter
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel orchestrating offline Core Search, multi-tier filters,
 * persistent search history, tag management, and on-demand AI Semantic Search plugin integration.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    private val searchFilesUseCase: SearchFilesUseCase,
    private val searchHistoryUseCase: SearchHistoryUseCase,
    private val tagManagementUseCase: TagManagementUseCase,
    private val fileOperationsUseCase: FileOperationsUseCase,
    private val searchIndexManagementUseCase: SearchIndexManagementUseCase? = null,
    private val semanticSearchUseCase: SemanticSearchUseCase? = null,
    private val aiIntelligenceUseCase: AiIntelligenceUseCase? = null
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    private val _searchFilter = MutableStateFlow(SearchFilter())
    private val _isFilterSheetVisible = MutableStateFlow(false)
    private val _tagDialogItem = MutableStateFlow<FileItem?>(null)
    private val _aiSuggestedTags = MutableStateFlow<List<AiSuggestedTag>>(emptyList())
    private val _detailsDialogItem = MutableStateFlow<FileItem?>(null)
    private val _snackbarMessage = MutableStateFlow<String?>(null)
    private val _isSearching = MutableStateFlow(false)
    private val _semanticResults = MutableStateFlow<List<SemanticSearchResult>>(emptyList())
    private val _isSemanticEnabled = MutableStateFlow(true)
    private val _semanticSimilarityThreshold = MutableStateFlow(0.80f)

    private val _searchHistory = searchHistoryUseCase.getHistory()
    private val _availableTags = tagManagementUseCase.getAvailableTags()
    private val _totalIndexedCount = searchIndexManagementUseCase?.getTotalIndexedCount() ?: flowOf(0)

    private val _searchResults = combine(_searchQuery, _searchFilter, _semanticSimilarityThreshold) { query, filter, threshold ->
        Triple(query, filter, threshold)
    }
        .debounce(150)
        .distinctUntilChanged()
        .flatMapLatest { (query, filter, threshold) ->
            if (query.isBlank() && filter.isDefault) {
                _isSearching.value = false
                _semanticResults.value = emptyList()
                flowOf(emptyList())
            } else {
                _isSearching.value = true
                triggerSemanticSearch(query, threshold)
                searchFilesUseCase(query, filter)
            }
        }

    private fun triggerSemanticSearch(query: String, threshold: Float = _semanticSimilarityThreshold.value) {
        if (query.isBlank() || semanticSearchUseCase == null || !_isSemanticEnabled.value) {
            _semanticResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            try {
                val results = semanticSearchUseCase.searchSemantically(
                    query = query,
                    options = SemanticSearchOptions(
                        similarityThreshold = threshold,
                        maxResults = 15
                    )
                )
                _semanticResults.value = results
            } catch (_: Throwable) {
                _semanticResults.value = emptyList()
            }
        }
    }

    val uiState: StateFlow<SearchUiState> = combine(
        _searchQuery,
        _searchFilter,
        _searchResults,
        _searchHistory,
        _availableTags,
        _isFilterSheetVisible,
        _tagDialogItem,
        _aiSuggestedTags,
        _semanticSimilarityThreshold,
        _isSemanticEnabled,
        _detailsDialogItem,
        _snackbarMessage,
        _totalIndexedCount
    ) { params: Array<Any?> ->
        val query = params[0] as String
        val filter = params[1] as SearchFilter
        val results = @Suppress("UNCHECKED_CAST") (params[2] as List<com.vvf.smartmanager.core.model.SearchResultItem>)
        val history = @Suppress("UNCHECKED_CAST") (params[3] as List<String>)
        val tags = @Suppress("UNCHECKED_CAST") (params[4] as List<String>)
        val isFilterOpen = params[5] as Boolean
        val tagItem = params[6] as FileItem?
        val suggestedTags = @Suppress("UNCHECKED_CAST") (params[7] as List<AiSuggestedTag>)
        val similarityThreshold = params[8] as Float
        val isSemanticOn = params[9] as Boolean
        val detailsItem = params[10] as FileItem?
        val snackbar = params[11] as String?
        val indexedCount = params[12] as Int

        SearchUiState(
            searchQuery = query,
            filter = filter,
            searchResults = results,
            searchHistory = history,
            availableTags = tags,
            isSearching = query.isNotBlank() || !filter.isDefault,
            isFilterSheetVisible = isFilterOpen,
            tagDialogItem = tagItem,
            aiSuggestedTags = suggestedTags,
            semanticSimilarityThreshold = similarityThreshold,
            isSemanticEnabled = isSemanticOn,
            detailsDialogItem = detailsItem,
            snackbarMessage = snackbar,
            totalIndexedFileCount = indexedCount
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = SearchUiState()
    )

    fun onQueryChanged(newQuery: String) {
        _searchQuery.value = newQuery.take(500)
    }

    fun onExecuteSearch(query: String) {
        val trimmed = query.trim().take(500)
        _searchQuery.value = trimmed
        if (trimmed.isNotEmpty()) {
            viewModelScope.launch {
                searchHistoryUseCase.saveQuery(trimmed)
            }
        }
    }

    fun setSemanticSimilarityThreshold(threshold: Float) {
        val clamped = threshold.coerceIn(0.70f, 0.95f)
        _semanticSimilarityThreshold.value = clamped
        if (_searchQuery.value.isNotBlank()) {
            triggerSemanticSearch(_searchQuery.value, clamped)
        }
    }

    fun setSemanticEnabled(enabled: Boolean) {
        _isSemanticEnabled.value = enabled
        if (!enabled) {
            _semanticResults.value = emptyList()
        } else if (_searchQuery.value.isNotBlank()) {
            triggerSemanticSearch(_searchQuery.value)
        }
    }

    fun onHistoryItemClicked(historyQuery: String) {
        _searchQuery.value = historyQuery.take(500)
        viewModelScope.launch {
            searchHistoryUseCase.saveQuery(historyQuery)
        }
    }

    fun onDeleteHistoryItem(historyQuery: String) {
        viewModelScope.launch {
            searchHistoryUseCase.deleteHistoryItem(historyQuery)
        }
    }

    fun onClearSearchHistory() {
        viewModelScope.launch {
            searchHistoryUseCase.clearHistory()
            _snackbarMessage.value = "Search history cleared"
        }
    }

    fun onCategorySelected(category: FileCategory) {
        _searchFilter.update {
            if (it.category == category) it.copy(category = FileCategory.ALL) else it.copy(category = category)
        }
    }

    fun onSizeFilterSelected(sizeFilter: SizeFilter) {
        _searchFilter.update {
            if (it.sizeFilter == sizeFilter) it.copy(sizeFilter = SizeFilter.ANY) else it.copy(sizeFilter = sizeFilter)
        }
    }

    fun onDateFilterSelected(dateFilter: DateFilter) {
        _searchFilter.update {
            if (it.dateFilter == dateFilter) it.copy(dateFilter = DateFilter.ANY) else it.copy(dateFilter = dateFilter)
        }
    }

    fun onTagToggled(tag: String) {
        _searchFilter.update { current ->
            val updated = current.selectedTags.toMutableSet()
            if (updated.contains(tag)) {
                updated.remove(tag)
            } else {
                updated.add(tag)
            }
            current.copy(selectedTags = updated)
        }
    }

    fun onSortOptionSelected(sortOption: FileSortOption) {
        _searchFilter.update { it.copy(sortOption = sortOption) }
    }

    fun onToggleIncludeHidden(includeHidden: Boolean) {
        _searchFilter.update { it.copy(includeHidden = includeHidden) }
    }

    fun resetFilters() {
        _searchFilter.value = SearchFilter()
        _snackbarMessage.value = "Search filters reset"
    }

    fun clearQuery() {
        _searchQuery.value = ""
    }

    fun setFilterSheetVisible(visible: Boolean) {
        _isFilterSheetVisible.value = visible
    }

    fun showTagDialog(item: FileItem?) {
        _tagDialogItem.value = item
        if (item != null && aiIntelligenceUseCase != null) {
            viewModelScope.launch {
                val suggestions = aiIntelligenceUseCase.suggestTags(item)
                _aiSuggestedTags.value = suggestions
            }
        } else {
            _aiSuggestedTags.value = emptyList()
        }
    }

    fun showDetailsDialog(item: FileItem?) {
        _detailsDialogItem.value = item
    }

    fun addTagToFile(path: String, tag: String) {
        viewModelScope.launch {
            val result = tagManagementUseCase.addTagToFile(path, tag)
            if (result.isSuccess) {
                _snackbarMessage.value = "Tag added: #$tag"
                _tagDialogItem.update { current ->
                    if (current?.path == path) {
                        current.copy(tags = (current.tags + tag.trim().lowercase()).distinct())
                    } else current
                }
            } else {
                _snackbarMessage.value = "Failed to add tag"
            }
        }
    }

    fun removeTagFromFile(path: String, tag: String) {
        viewModelScope.launch {
            val result = tagManagementUseCase.removeTagFromFile(path, tag)
            if (result.isSuccess) {
                _snackbarMessage.value = "Tag removed: #$tag"
                _tagDialogItem.update { current ->
                    if (current?.path == path) {
                        current.copy(tags = current.tags.filterNot { it.equals(tag.trim(), ignoreCase = true) })
                    } else current
                }
            } else {
                _snackbarMessage.value = "Failed to remove tag"
            }
        }
    }

    fun toggleFavorite(item: FileItem) {
        viewModelScope.launch {
            val newStatus = !item.isFavorite
            val res = fileOperationsUseCase.toggleFavorite(item.path, newStatus)
            if (res.isSuccess) {
                _snackbarMessage.value = if (newStatus) "Added to Favorites" else "Removed from Favorites"
            }
        }
    }

    fun dismissSnackbar() {
        _snackbarMessage.value = null
    }

    companion object {
        fun provideFactory(
            searchFilesUseCase: SearchFilesUseCase,
            searchHistoryUseCase: SearchHistoryUseCase,
            tagManagementUseCase: TagManagementUseCase,
            fileOperationsUseCase: FileOperationsUseCase,
            searchIndexManagementUseCase: SearchIndexManagementUseCase? = null,
            semanticSearchUseCase: SemanticSearchUseCase? = null,
            aiIntelligenceUseCase: AiIntelligenceUseCase? = null
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return SearchViewModel(
                    searchFilesUseCase = searchFilesUseCase,
                    searchHistoryUseCase = searchHistoryUseCase,
                    tagManagementUseCase = tagManagementUseCase,
                    fileOperationsUseCase = fileOperationsUseCase,
                    searchIndexManagementUseCase = searchIndexManagementUseCase,
                    semanticSearchUseCase = semanticSearchUseCase,
                    aiIntelligenceUseCase = aiIntelligenceUseCase
                ) as T
            }
        }
    }
}
