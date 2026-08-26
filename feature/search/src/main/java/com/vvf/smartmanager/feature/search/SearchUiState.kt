package com.vvf.smartmanager.feature.search

import com.vvf.smartmanager.core.model.AiSuggestedTag
import com.vvf.smartmanager.core.model.FileCategory
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.SearchFilter
import com.vvf.smartmanager.core.model.SearchResultItem

/**
 * UI State for the Core Search screen.
 */
data class SearchUiState(
    val searchQuery: String = "",
    val filter: SearchFilter = SearchFilter(),
    val searchResults: List<SearchResultItem> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val availableTags: List<String> = emptyList(),
    val isSearching: Boolean = false,
    val isFilterSheetVisible: Boolean = false,
    val tagDialogItem: FileItem? = null,
    val aiSuggestedTags: List<AiSuggestedTag> = emptyList(),
    val semanticSimilarityThreshold: Float = 0.80f,
    val isSemanticEnabled: Boolean = true,
    val detailsDialogItem: FileItem? = null,
    val snackbarMessage: String? = null,
    val totalIndexedFileCount: Int = 0
) {
    val hasActiveQueryOrFilter: Boolean
        get() = searchQuery.isNotBlank() || !filter.isDefault

    val resultCount: Int
        get() = searchResults.size
}
