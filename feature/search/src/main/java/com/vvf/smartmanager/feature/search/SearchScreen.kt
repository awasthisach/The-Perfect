package com.vvf.smartmanager.feature.search

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SearchOff
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vvf.smartmanager.core.model.FileCategory
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.FileSortOption
import com.vvf.smartmanager.feature.search.components.SearchFileDetailsDialog
import com.vvf.smartmanager.feature.search.components.SearchFilterBottomSheet
import com.vvf.smartmanager.feature.search.components.SearchFilterChipsRow
import com.vvf.smartmanager.feature.search.components.SearchHistorySection
import com.vvf.smartmanager.feature.search.components.SearchResultCard
import com.vvf.smartmanager.feature.search.components.SearchTagBrowseSection
import com.vvf.smartmanager.feature.search.components.TagManagementDialog

private val BhagwaOrange = Color(0xFFF47B20)
private val CosmicBlue = Color(0xFF102B52)
private val EmeraldGreen = Color(0xFF3FA34D)
private val SkyCyan = Color(0xFF5BC0EB)
private val SoftGold = Color(0xFFD4A95A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    viewModel: SearchViewModel,
    onOpenFile: ((FileItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val snackbarHostState = remember { SnackbarHostState() }
    var showSortMenu by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissSnackbar()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("search_screen_root"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(top = 8.dp, bottom = 8.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.vvf.smartmanager.core.common.R.drawable.vvf_foundation_logo),
                        contentDescription = "Vishva Vijayaa Foundation Logo",
                        modifier = Modifier
                            .size(32.dp)
                            .padding(end = 8.dp)
                    )
                    Text(
                        text = "VVF Smart Search",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "॥ विजया ददाति विजयम् ॥",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                // Main Search Input Bar (Google Pill Style)
                OutlinedTextField(
                    value = uiState.searchQuery,
                    onValueChange = { viewModel.onQueryChanged(it) },
                    placeholder = {
                        Text(
                            text = "Search files, tags (#doc), or extensions...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Default.Search,
                            contentDescription = "Search",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    },
                    trailingIcon = {
                        if (uiState.searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.clearQuery() }) {
                                Icon(
                                    imageVector = Icons.Default.Clear,
                                    contentDescription = "Clear search query",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(
                        onSearch = {
                            viewModel.onExecuteSearch(uiState.searchQuery)
                            focusManager.clearFocus()
                        }
                    ),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = MaterialTheme.colorScheme.onSurface,
                        unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                        cursorColor = MaterialTheme.colorScheme.primary
                    ),
                    shape = RoundedCornerShape(28.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp)
                        .testTag("search_input_field")
                )

                // Subtitle badge row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "100% Offline Core Search (FTS4 + Tags + Metadata)",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp
                    )
                }
            }
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background)
        ) {
            // Horizontal Filter Chips & Bottom Sheet Launcher
            SearchFilterChipsRow(
                filter = uiState.filter,
                availableTags = uiState.availableTags,
                onOpenFilterSheet = { viewModel.setFilterSheetVisible(true) },
                onCategorySelected = { viewModel.onCategorySelected(it) },
                onDateFilterSelected = { viewModel.onDateFilterSelected(it) },
                onSizeFilterSelected = { viewModel.onSizeFilterSelected(it) },
                onTagToggled = { viewModel.onTagToggled(it) },
                onResetFilters = { viewModel.resetFilters() }
            )

            // Dynamic Body Content
            if (!uiState.hasActiveQueryOrFilter) {
                // Empty state: Show History, Tag Discovery & Category Explorer
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("search_discovery_view"),
                    contentPadding = PaddingValues(bottom = 24.dp)
                ) {
                    // Recent Searches
                    item {
                        SearchHistorySection(
                            history = uiState.searchHistory,
                            onItemClick = { viewModel.onHistoryItemClicked(it) },
                            onDeleteItem = { viewModel.onDeleteHistoryItem(it) },
                            onClearAll = { viewModel.onClearSearchHistory() }
                        )
                    }

                    // Available Tags
                    item {
                        SearchTagBrowseSection(
                            tags = uiState.availableTags,
                            selectedTags = uiState.filter.selectedTags,
                            onTagClick = { viewModel.onTagToggled(it) }
                        )
                    }

                    // Category Shortcuts
                    item {
                        QuickSearchCategoriesSection(
                            onSelectCategory = { viewModel.onCategorySelected(it) }
                        )
                    }

                    // Offline Guarantee Card
                    item {
                        OfflineSearchInfoCard()
                    }
                }
            } else if (uiState.searchResults.isEmpty()) {
                // Active Search with No Matches
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp)
                        .testTag("search_no_results_view"),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Surface(
                            shape = CircleShape,
                            color = BhagwaOrange.copy(alpha = 0.12f),
                            modifier = Modifier.size(72.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.SearchOff,
                                    contentDescription = null,
                                    tint = BhagwaOrange,
                                    modifier = Modifier.size(36.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "No Matching Files Found",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = CosmicBlue
                        )

                        Spacer(modifier = Modifier.height(6.dp))

                        Text(
                            text = if (uiState.searchQuery.isNotBlank()) {
                                "No files match \"${uiState.searchQuery}\" with current filters."
                            } else {
                                "No files match the selected filter criteria."
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (uiState.searchQuery.isNotBlank()) {
                                TextButton(onClick = { viewModel.clearQuery() }) {
                                    Text("Clear Query", color = BhagwaOrange)
                                }
                            }
                            if (!uiState.filter.isDefault) {
                                TextButton(onClick = { viewModel.resetFilters() }) {
                                    Text("Reset Filters", color = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            } else {
                // Active Search Results List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .testTag("search_results_list"),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp)
                ) {
                    // Result Header Row: Count and Sort Dropdown
                    item {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "${uiState.resultCount} ${if (uiState.resultCount == 1) "result" else "results"} found",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = CosmicBlue
                            )

                            Box {
                                TextButton(
                                    onClick = { showSortMenu = true },
                                    modifier = Modifier.testTag("search_sort_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sort,
                                        contentDescription = "Sort",
                                        tint = CosmicBlue,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text(
                                        text = uiState.filter.sortOption.displayName,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CosmicBlue
                                    )
                                }

                                DropdownMenu(
                                    expanded = showSortMenu,
                                    onDismissRequest = { showSortMenu = false }
                                ) {
                                    FileSortOption.values().forEach { option ->
                                        DropdownMenuItem(
                                            text = { Text(option.displayName) },
                                            onClick = {
                                                viewModel.onSortOptionSelected(option)
                                                showSortMenu = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Result Cards
                    items(
                        items = uiState.searchResults,
                        key = { it.fileItem.path }
                    ) { resultItem ->
                        SearchResultCard(
                            resultItem = resultItem,
                            onItemClick = { item ->
                                viewModel.onExecuteSearch(uiState.searchQuery)
                                onOpenFile?.invoke(item)
                            },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onManageTags = { viewModel.showTagDialog(it) },
                            onShowDetails = { viewModel.showDetailsDialog(it) }
                        )
                    }
                }
            }
        }
    }

    // Advanced Filter Modal Bottom Sheet
    if (uiState.isFilterSheetVisible) {
        SearchFilterBottomSheet(
            filter = uiState.filter,
            availableTags = uiState.availableTags,
            onDismiss = { viewModel.setFilterSheetVisible(false) },
            onCategorySelected = { viewModel.onCategorySelected(it) },
            onSizeFilterSelected = { viewModel.onSizeFilterSelected(it) },
            onDateFilterSelected = { viewModel.onDateFilterSelected(it) },
            onTagToggled = { viewModel.onTagToggled(it) },
            onSortOptionSelected = { viewModel.onSortOptionSelected(it) },
            onToggleIncludeHidden = { viewModel.onToggleIncludeHidden(it) },
            onResetFilters = { viewModel.resetFilters() }
        )
    }

    // In-place Tag Management Dialog
    uiState.tagDialogItem?.let { targetItem ->
        TagManagementDialog(
            fileItem = targetItem,
            aiSuggestedTags = uiState.aiSuggestedTags,
            onDismiss = { viewModel.showTagDialog(null) },
            onAddTag = { path, tag -> viewModel.addTagToFile(path, tag) },
            onRemoveTag = { path, tag -> viewModel.removeTagFromFile(path, tag) }
        )
    }

    // Comprehensive Metadata Details Dialog
    uiState.detailsDialogItem?.let { targetItem ->
        SearchFileDetailsDialog(
            fileItem = targetItem,
            onDismiss = { viewModel.showDetailsDialog(null) }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun QuickSearchCategoriesSection(
    onSelectCategory: (FileCategory) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Text(
            text = "Browse Categories",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = CosmicBlue
        )
        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            val items = listOf(
                Triple("Documents", FileCategory.DOCUMENTS, Icons.Default.Description),
                Triple("Images", FileCategory.IMAGES, Icons.Default.Image),
                Triple("Videos", FileCategory.VIDEOS, Icons.Default.Movie),
                Triple("Audio", FileCategory.AUDIO, Icons.Default.MusicNote),
                Triple("APKs", FileCategory.APKS, Icons.Default.Folder)
            )

            items.forEach { (label, category, icon) ->
                Card(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onSelectCategory(category) },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
                    ) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = BhagwaOrange,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(label, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineSearchInfoCard() {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicBlue.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = BhagwaOrange.copy(alpha = 0.15f),
                modifier = Modifier.size(38.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = BhagwaOrange,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "High-Speed Offline Search Engine",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = CosmicBlue
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Room SQLite FTS4 virtual table index enables sub-millisecond queries across filenames, tags, and metadata without battery or network drain.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
