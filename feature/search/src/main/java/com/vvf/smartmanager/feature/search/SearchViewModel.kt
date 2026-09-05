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
        _totalIndexedCount,
        _isSearching,
        _semanticResults
    ) { values ->
        @Suppress("UNCHECKED_CAST")
        SearchUiState(
            query = values[0] as String,
            filter = values[1] as SearchFilter,
            results = values[2] as List<*>,
            history = values[3] as List<String>,
            availableTags = values[4] as List<String>,
            isFilterSheetVisible = values[5] as Boolean,
            tagDialogItem = values[6] as FileItem?,
            aiSuggestedTags = values[7] as List<AiSuggestedTag>,
            semanticSimilarityThreshold = values[8] as Float,
            isSemanticEnabled = values[9] as Boolean,
            detailsDialogItem = values[10] as FileItem?,
            snackbarMessage = values[11] as String?,
            totalIndexedCount = values[12] as Int,
            isSearching = values[13] as Boolean,
            semanticResults = values[14] as List<SemanticSearchResult>
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchUiState())

    fun onQueryChanged(newQuery: String) {
        // Cap length: red-team paste DoS / embedding ANR prevention
        _searchQuery.value = newQuery.take(500)
    }

    fun submitQuery() {
        val trimmed = _searchQuery.value.trim()
        if (trimmed.isNotEmpty()) {
            viewModelScope.launch { searchHistoryUseCase.saveQuery(trimmed) }
        }
    }

    fun onHistoryClick(historyQuery: String) {
        _searchQuery.value = historyQuery.take(500)
    }

    fun clearQuery() {
        _searchQuery.value = ""
    }

    fun updateFilter(filter: SearchFilter) {
        _searchFilter.value = filter
    }

    fun setSemanticEnabled(enabled: Boolean) {
        _isSemanticEnabled.value = enabled
        if (!enabled) _semanticResults.value = emptyList()
    }

    fun setSemanticThreshold(threshold: Float) {
        _semanticSimilarityThreshold.value = threshold.coerceIn(0.5f, 0.95f)
    }

    fun showFilterSheet(visible: Boolean) {
        _isFilterSheetVisible.value = visible
    }

    fun openTagDialog(item: FileItem) {
        _tagDialogItem.value = item
    }

    fun dismissTagDialog() {
        _tagDialogItem.value = null
    }

    fun openDetails(item: FileItem) {
        _detailsDialogItem.value = item
    }

    fun dismissDetails() {
        _detailsDialogItem.value = null
    }

    fun clearSnackbar() {
        _snackbarMessage.value = null
    }

    companion object {
        fun factory(
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
                    searchFilesUseCase,
                    searchHistoryUseCase,
                    tagManagementUseCase,
                    fileOperationsUseCase,
                    searchIndexManagementUseCase,
                    semanticSearchUseCase,
                    aiIntelligenceUseCase
                ) as T
            }
        }
    }
}

data class SearchUiState(
    val query: String = "",
    val filter: SearchFilter = SearchFilter(),
    val results: List<*> = emptyList<Any>(),
    val history: List<String> = emptyList(),
    val availableTags: List<String> = emptyList(),
    val isFilterSheetVisible: Boolean = false,
    val tagDialogItem: FileItem? = null,
    val aiSuggestedTags: List<AiSuggestedTag> = emptyList(),
    val semanticSimilarityThreshold: Float = 0.80f,
    val isSemanticEnabled: Boolean = true,
    val detailsDialogItem: FileItem? = null,
    val snackbarMessage: String? = null,
    val totalIndexedCount: Int = 0,
    val isSearching: Boolean = false,
    val semanticResults: List<SemanticSearchResult> = emptyList()
)
