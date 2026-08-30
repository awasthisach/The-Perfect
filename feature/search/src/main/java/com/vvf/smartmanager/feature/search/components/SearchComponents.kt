package com.vvf.smartmanager.feature.search.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Label
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.Tag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vvf.smartmanager.core.common.FormatUtils
import com.vvf.smartmanager.core.model.DateFilter
import com.vvf.smartmanager.core.model.FileCategory
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.FileSortOption
import com.vvf.smartmanager.core.model.SearchFilter
import com.vvf.smartmanager.core.model.SearchMatchType
import com.vvf.smartmanager.core.model.SearchResultItem
import com.vvf.smartmanager.core.model.SizeFilter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BhagwaOrange = Color(0xFFF47B20)
private val CosmicBlue = Color(0xFF102B52)
private val EmeraldGreen = Color(0xFF3FA34D)
private val SkyCyan = Color(0xFF5BC0EB)
private val SoftGold = Color(0xFFD4A95A)

/**
 * Filter bar with quick chips and bottom sheet trigger.
 */
@Composable
fun SearchFilterChipsRow(
    filter: SearchFilter,
    availableTags: List<String>,
    onOpenFilterSheet: () -> Unit,
    onCategorySelected: (FileCategory) -> Unit,
    onDateFilterSelected: (DateFilter) -> Unit,
    onSizeFilterSelected: (SizeFilter) -> Unit,
    onTagToggled: (String) -> Unit,
    onResetFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Master Filter Button with Active Count Badge
        BadgedBox(
            badge = {
                if (filter.activeFilterCount > 0) {
                    Badge(
                        containerColor = BhagwaOrange,
                        contentColor = Color.White
                    ) {
                        Text(filter.activeFilterCount.toString())
                    }
                }
            }
        ) {
            FilterChip(
                selected = filter.activeFilterCount > 0,
                onClick = onOpenFilterSheet,
                label = { Text("Filters", fontWeight = FontWeight.SemiBold) },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = "Open Filters",
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CosmicBlue,
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = BhagwaOrange
                ),
                modifier = Modifier.testTag("search_filter_sheet_button")
            )
        }

        // Active Category Chip
        if (filter.category != FileCategory.ALL) {
            FilterChip(
                selected = true,
                onClick = { onCategorySelected(FileCategory.ALL) },
                label = { Text(filter.category.name.lowercase().replaceFirstChar { it.uppercase() }) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove category filter",
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = BhagwaOrange.copy(alpha = 0.15f),
                    selectedLabelColor = BhagwaOrange
                )
            )
        }

        // Active Size Chip
        if (filter.sizeFilter != SizeFilter.ANY) {
            FilterChip(
                selected = true,
                onClick = { onSizeFilterSelected(SizeFilter.ANY) },
                label = { Text(filter.sizeFilter.displayName) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove size filter",
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SkyCyan.copy(alpha = 0.2f),
                    selectedLabelColor = CosmicBlue
                )
            )
        }

        // Active Date Chip
        if (filter.dateFilter != DateFilter.ANY) {
            FilterChip(
                selected = true,
                onClick = { onDateFilterSelected(DateFilter.ANY) },
                label = { Text(filter.dateFilter.displayName) },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove date filter",
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = EmeraldGreen.copy(alpha = 0.18f),
                    selectedLabelColor = EmeraldGreen
                )
            )
        }

        // Active Tags Chips
        filter.selectedTags.forEach { tag ->
            FilterChip(
                selected = true,
                onClick = { onTagToggled(tag) },
                label = { Text("#$tag") },
                trailingIcon = {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Remove tag $tag",
                        modifier = Modifier.size(16.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = SoftGold.copy(alpha = 0.25f),
                    selectedLabelColor = CosmicBlue
                )
            )
        }

        // Quick Category shortcuts when no category filter is applied
        if (filter.category == FileCategory.ALL) {
            val quickCategories = listOf(
                FileCategory.DOCUMENTS to "Docs",
                FileCategory.IMAGES to "Images",
                FileCategory.VIDEOS to "Videos",
                FileCategory.AUDIO to "Audio",
                FileCategory.APKS to "APKs"
            )
            quickCategories.forEach { (cat, label) ->
                FilterChip(
                    selected = false,
                    onClick = { onCategorySelected(cat) },
                    label = { Text(label) },
                    colors = FilterChipDefaults.filterChipColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                )
            }
        }

        // Reset all active filters
        if (!filter.isDefault) {
            TextButton(
                onClick = onResetFilters,
                modifier = Modifier.testTag("reset_filters_chip_button")
            ) {
                Text("Reset", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        }
    }
}

/**
 * Result Card displaying matching file with origin badges and metadata.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchResultCard(
    resultItem: SearchResultItem,
    onItemClick: (FileItem) -> Unit,
    onToggleFavorite: (FileItem) -> Unit,
    onManageTags: (FileItem) -> Unit,
    onShowDetails: (FileItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val fileItem = resultItem.fileItem
    val icon = getFileCategoryIcon(fileItem)
    val iconTint = getFileCategoryColor(fileItem)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onItemClick(fileItem) }
            .testTag("search_result_card_${fileItem.name}"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                // File Type Icon
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconTint,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                // Name and match details
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileItem.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (fileItem.isDirectory) "Folder" else FormatUtils.formatBytes(fileItem.sizeBytes),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = FormatUtils.formatDate(fileItem.lastModified),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Quick Favorite & Info actions
                IconButton(
                    onClick = { onToggleFavorite(fileItem) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = if (fileItem.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Toggle Favorite",
                        tint = if (fileItem.isFavorite) SoftGold else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.size(20.dp)
                    )
                }

                IconButton(
                    onClick = { onShowDetails(fileItem) },
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = "File Info",
                        tint = CosmicBlue.copy(alpha = 0.7f),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            // Match type badge & snippet
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                // Match type indicator
                Surface(
                    shape = RoundedCornerShape(4.dp),
                    color = when (resultItem.matchType) {
                        SearchMatchType.FILENAME -> BhagwaOrange.copy(alpha = 0.12f)
                        SearchMatchType.TAG -> EmeraldGreen.copy(alpha = 0.12f)
                        SearchMatchType.METADATA -> SkyCyan.copy(alpha = 0.15f)
                        SearchMatchType.FTS -> CosmicBlue.copy(alpha = 0.1f)
                    }
                ) {
                    Text(
                        text = resultItem.matchType.displayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = when (resultItem.matchType) {
                            SearchMatchType.FILENAME -> BhagwaOrange
                            SearchMatchType.TAG -> EmeraldGreen
                            SearchMatchType.METADATA -> CosmicBlue
                            SearchMatchType.FTS -> CosmicBlue
                        },
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                    )
                }

                resultItem.matchedSnippet?.let { snippet ->
                    Text(
                        text = snippet,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Tags row with inline tag manager
            if (fileItem.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    fileItem.tags.forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Text(
                                text = "#$tag",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                            )
                        }
                    }

                    // Add tag button
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = CosmicBlue.copy(alpha = 0.08f),
                        modifier = Modifier
                            .clickable { onManageTags(fileItem) }
                            .padding(vertical = 2.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Add tag",
                                tint = CosmicBlue,
                                modifier = Modifier.size(12.dp)
                            )
                            Spacer(modifier = Modifier.width(2.dp))
                            Text("Tag", fontSize = 11.sp, color = CosmicBlue)
                        }
                    }
                }
            }
        }
    }
}

/**
 * Search History chips section.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchHistorySection(
    history: List<String>,
    onItemClick: (String) -> Unit,
    onDeleteItem: (String) -> Unit,
    onClearAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (history.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.History,
                    contentDescription = null,
                    tint = CosmicBlue,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Recent Searches",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = CosmicBlue
                )
            }

            TextButton(onClick = onClearAll) {
                Text(
                    text = "Clear All",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }
        }

        Spacer(modifier = Modifier.height(4.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            history.forEach { item ->
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier.clip(RoundedCornerShape(16.dp))
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .clickable { onItemClick(item) }
                            .padding(start = 10.dp, end = 4.dp, top = 4.dp, bottom = 4.dp)
                    ) {
                        Text(
                            text = item,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        IconButton(
                            onClick = { onDeleteItem(item) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Delete search $item",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Explore files by available tags.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SearchTagBrowseSection(
    tags: List<String>,
    selectedTags: Set<String>,
    onTagClick: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (tags.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = Icons.Default.Label,
                contentDescription = null,
                tint = EmeraldGreen,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Explore by Tags",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = CosmicBlue
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            tags.forEach { tag ->
                val isSelected = selectedTags.contains(tag)
                FilterChip(
                    selected = isSelected,
                    onClick = { onTagClick(tag) },
                    label = { Text("#$tag") },
                    leadingIcon = if (isSelected) {
                        {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                modifier = Modifier.size(14.dp)
                            )
                        }
                    } else null,
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = EmeraldGreen,
                        selectedLabelColor = Color.White,
                        selectedLeadingIconColor = Color.White
                    )
                )
            }
        }
    }
}

/**
 * Advanced Multi-Dimensional Filter Modal Bottom Sheet.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SearchFilterBottomSheet(
    filter: SearchFilter,
    availableTags: List<String>,
    onDismiss: () -> Unit,
    onCategorySelected: (FileCategory) -> Unit,
    onSizeFilterSelected: (SizeFilter) -> Unit,
    onDateFilterSelected: (DateFilter) -> Unit,
    onTagToggled: (String) -> Unit,
    onSortOptionSelected: (FileSortOption) -> Unit,
    onToggleIncludeHidden: (Boolean) -> Unit,
    onResetFilters: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 28.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Search Filters",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = CosmicBlue
                )
                TextButton(onClick = onResetFilters) {
                    Text("Reset All", color = MaterialTheme.colorScheme.error)
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            // 1. Categories
            Text(
                text = "File Category",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = BhagwaOrange
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                val categories = listOf(
                    FileCategory.ALL to "All Files",
                    FileCategory.DOCUMENTS to "Documents",
                    FileCategory.IMAGES to "Images",
                    FileCategory.VIDEOS to "Videos",
                    FileCategory.AUDIO to "Audio",
                    FileCategory.APKS to "APKs",
                    FileCategory.ARCHIVES to "Archives",
                    FileCategory.FAVORITES to "Favorites"
                )
                categories.forEach { (cat, label) ->
                    FilterChip(
                        selected = filter.category == cat,
                        onClick = { onCategorySelected(cat) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = BhagwaOrange,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 2. File Size
            Text(
                text = "File Size",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = CosmicBlue
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                SizeFilter.values().forEach { size ->
                    FilterChip(
                        selected = filter.sizeFilter == size,
                        onClick = { onSizeFilterSelected(size) },
                        label = { Text(size.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CosmicBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 3. Date Modified
            Text(
                text = "Date Modified",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = CosmicBlue
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                DateFilter.values().forEach { date ->
                    FilterChip(
                        selected = filter.dateFilter == date,
                        onClick = { onDateFilterSelected(date) },
                        label = { Text(date.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = EmeraldGreen,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            if (availableTags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))

                // 4. Tags Filter
                Text(
                    text = "Filter by Tag",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = CosmicBlue
                )
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    availableTags.forEach { tag ->
                        val isSelected = filter.selectedTags.contains(tag)
                        FilterChip(
                            selected = isSelected,
                            onClick = { onTagToggled(tag) },
                            label = { Text("#$tag") },
                            leadingIcon = if (isSelected) {
                                {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            } else null,
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = SoftGold,
                                selectedLabelColor = CosmicBlue,
                                selectedLeadingIconColor = CosmicBlue
                            )
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 5. Sort Order
            Text(
                text = "Sort Results By",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = CosmicBlue
            )
            Spacer(modifier = Modifier.height(6.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                FileSortOption.values().forEach { sort ->
                    FilterChip(
                        selected = filter.sortOption == sort,
                        onClick = { onSortOptionSelected(sort) },
                        label = { Text(sort.displayName) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = CosmicBlue,
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 6. Include Hidden Files Switch
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Include Hidden Files (.*)",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Switch(
                    checked = filter.includeHidden,
                    onCheckedChange = onToggleIncludeHidden,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BhagwaOrange,
                        checkedTrackColor = BhagwaOrange.copy(alpha = 0.5f)
                    )
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            Button(
                onClick = onDismiss,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                colors = ButtonDefaults.buttonColors(containerColor = CosmicBlue),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text("Apply Filters (${filter.activeFilterCount})", color = Color.White, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/**
 * In-place Tag Management Dialog for any file item.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TagManagementDialog(
    fileItem: FileItem,
    aiSuggestedTags: List<com.vvf.smartmanager.core.model.AiSuggestedTag> = emptyList(),
    onDismiss: () -> Unit,
    onAddTag: (String, String) -> Unit,
    onRemoveTag: (String, String) -> Unit
) {
    var newTagText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Tag,
                    contentDescription = null,
                    tint = BhagwaOrange
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Manage Tags", fontWeight = FontWeight.Bold, color = CosmicBlue)
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = fileItem.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(12.dp))

                // AI Suggested Tags Section
                if (aiSuggestedTags.isNotEmpty()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = BhagwaOrange,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "AI Suggested Tags:",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = BhagwaOrange
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        aiSuggestedTags.forEach { suggested ->
                            val alreadyHas = fileItem.tags.any { it.equals(suggested.tagName, ignoreCase = true) }
                            Surface(
                                shape = RoundedCornerShape(12.dp),
                                color = if (alreadyHas) BhagwaOrange.copy(alpha = 0.2f) else BhagwaOrange.copy(alpha = 0.08f),
                                border = androidx.compose.foundation.BorderStroke(1.dp, BhagwaOrange.copy(alpha = 0.4f)),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable(enabled = !alreadyHas) {
                                        onAddTag(fileItem.path, suggested.tagName)
                                    }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        text = "+ #${suggested.tagName}",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = if (alreadyHas) MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f) else BhagwaOrange
                                    )
                                    if (suggested.confidenceScore > 0f) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text(
                                            text = "${(suggested.confidenceScore * 100).toInt()}%",
                                            fontSize = 10.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Current Tags
                if (fileItem.tags.isNotEmpty()) {
                    Text(
                        text = "Current Tags:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        fileItem.tags.forEach { tag ->
                            Surface(
                                shape = RoundedCornerShape(14.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                modifier = Modifier.clip(RoundedCornerShape(14.dp))
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(start = 8.dp, end = 4.dp, top = 2.dp, bottom = 2.dp)
                                ) {
                                    Text("#$tag", fontSize = 12.sp)
                                    Spacer(modifier = Modifier.width(4.dp))
                                    IconButton(
                                        onClick = { onRemoveTag(fileItem.path, tag) },
                                        modifier = Modifier.size(18.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Remove tag",
                                            modifier = Modifier.size(12.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                }

                // Add Tag Field
                OutlinedTextField(
                    value = newTagText,
                    onValueChange = { newTagText = it.replace(" ", "").replace("#", "") },
                    label = { Text("New Tag (e.g. invoice, urgent)") },
                    singleLine = true,
                    trailingIcon = {
                        if (newTagText.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    onAddTag(fileItem.path, newTagText)
                                    newTagText = ""
                                }
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add Tag",
                                    tint = BhagwaOrange
                                )
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (newTagText.isNotBlank()) {
                        onAddTag(fileItem.path, newTagText)
                    }
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CosmicBlue)
            ) {
                Text("Done", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

/**
 * File Details Dialog showing comprehensive metadata.
 */
@Composable
fun SearchFileDetailsDialog(
    fileItem: FileItem,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = CosmicBlue
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("File Details", fontWeight = FontWeight.Bold, color = CosmicBlue)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow("Name", fileItem.name)
                DetailRow("Path", fileItem.path)
                DetailRow("Size", FormatUtils.formatBytes(fileItem.sizeBytes))
                DetailRow("Modified", SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(fileItem.lastModified)))
                DetailRow("Type", fileItem.mimeType ?: if (fileItem.isDirectory) "Directory" else "Unknown")
                DetailRow("Extension", fileItem.extension.ifEmpty { "None" })
                fileItem.md5Hash?.let { hash ->
                    DetailRow("MD5 Hash", hash)
                }
                if (fileItem.tags.isNotEmpty()) {
                    DetailRow("Tags", fileItem.tags.joinToString(", ") { "#$it" })
                }
                DetailRow("Favorite", if (fileItem.isFavorite) "Yes ⭐" else "No")
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CosmicBlue)
            ) {
                Text("Close", color = Color.White)
            }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = BhagwaOrange
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface,
            fontFamily = if (label == "Path" || label == "MD5 Hash") FontFamily.Monospace else FontFamily.Default
        )
    }
}

private fun getFileCategoryIcon(item: FileItem): ImageVector {
    if (item.isDirectory) return Icons.Default.Folder
    val ext = item.extension.lowercase()
    val mime = item.mimeType?.lowercase() ?: ""
    return when {
        mime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "gif", "svg") -> Icons.Default.Image
        mime.startsWith("video/") || ext in listOf("mp4", "mkv", "avi", "mov", "webm") -> Icons.Default.Movie
        mime.startsWith("audio/") || ext in listOf("mp3", "wav", "aac", "flac", "ogg") -> Icons.Default.MusicNote
        else -> Icons.Default.Description
    }
}

private fun getFileCategoryColor(item: FileItem): Color {
    if (item.isDirectory) return Color(0xFF1A73E8) // Google Blue for folders
    val ext = item.extension.lowercase()
    val mime = item.mimeType?.lowercase() ?: ""
    return when {
        mime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "gif") -> Color(0xFF1E8E3E) // Google Green
        mime.startsWith("video/") || ext in listOf("mp4", "mkv", "avi", "mov") -> Color(0xFFD93025) // Google Red
        mime.startsWith("audio/") || ext in listOf("mp3", "wav", "aac", "flac") -> Color(0xFFF9AB00) // Google Yellow
        ext in listOf("pdf", "doc", "docx", "txt", "xlsx") -> Color(0xFF1A73E8) // Google Blue
        else -> Color(0xFF9334E6) // Google Purple
    }
}
