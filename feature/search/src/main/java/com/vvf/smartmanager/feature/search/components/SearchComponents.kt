package com.vvf.smartmanager.feature.search.components

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
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

private val BhagwaOrange = Color(0xFFF47B20)
private val CosmicBlue = Color(0xFF102B52)
private val EmeraldGreen = Color(0xFF3FA34D)
private val SkyCyan = Color(0xFF5BC0EB)
private val SoftGold = Color(0xFFD4A95A)

/**
 * Minimal SearchResultCard with SEMANTIC match badge support (P0).
 * Full component set remains on main; this keeps the module compiling while
 * semantic results are wired through SearchViewModel.
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(Modifier = Modifier.fillMaxWidth().padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Box(
                    modifier = Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)).background(iconTint.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(24.dp))
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileItem.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (fileItem.isDirectory) "Folder" else FormatUtils.formatBytes(fileItem.sizeBytes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { onToggleFavorite(fileItem) }, modifier = Modifier.size(36.dp)) {
                    Icon(
                        imageVector = if (fileItem.isFavorite) Icons.Default.Star else Icons.Default.StarBorder,
                        contentDescription = "Toggle Favorite",
                        tint = if (fileItem.isFavorite) SoftGold else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
                IconButton(onClick = { onShowDetails(fileItem) }, modifier = Modifier.size(36.dp)) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = "File Info", tint = CosmicBlue.copy(alpha = 0.7f))
                }
            }
            Spacer(modifier = Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                val badgeBg = when (resultItem.matchType) {
                    SearchMatchType.FILENAME -> BhagwaOrange.copy(alpha = 0.12f)
                    SearchMatchType.TAG -> EmeraldGreen.copy(alpha = 0.12f)
                    SearchMatchType.METADATA -> SkyCyan.copy(alpha = 0.15f)
                    SearchMatchType.FTS -> CosmicBlue.copy(alpha = 0.1f)
                    SearchMatchType.SEMANTIC -> BhagwaOrange.copy(alpha = 0.18f)
                }
                val badgeFg = when (resultItem.matchType) {
                    SearchMatchType.FILENAME, SearchMatchType.SEMANTIC -> BhagwaOrange
                    SearchMatchType.TAG -> EmeraldGreen
                    SearchMatchType.METADATA, SearchMatchType.FTS -> CosmicBlue
                }
                Surface(shape = RoundedCornerShape(4.dp), color = badgeBg) {
                    Text(
                        text = resultItem.matchType.displayName,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        color = badgeFg,
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
            if (fileItem.tags.isNotEmpty()) {
                Spacer(modifier = Modifier.height(6.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    fileItem.tags.take(6).forEach { tag ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = SoftGold.copy(alpha = 0.2f),
                            modifier = Modifier.clickable { onManageTags(fileItem) }
                        ) {
                            Text(text = "#$tag", fontSize = 11.sp, color = CosmicBlue, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
            }
        }
    }
}

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
        modifier = modifier.fillMaxWidth().horizontalScroll(scrollState).padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BadgedBox(badge = {
            if (filter.activeFilterCount > 0) {
                Badge(containerColor = BhagwaOrange, contentColor = Color.White) {
                    Text(filter.activeFilterCount.toString())
                }
            }
        }) {
            FilterChip(
                selected = filter.activeFilterCount > 0,
                onClick = onOpenFilterSheet,
                label = { Text("Filters", fontWeight = FontWeight.SemiBold) },
                leadingIcon = { Icon(Icons.Default.FilterList, contentDescription = null, modifier = Modifier.size(18.dp)) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = CosmicBlue,
                    selectedLabelColor = Color.White,
                    selectedLeadingIconColor = BhagwaOrange
                ),
                modifier = Modifier.testTag("search_filter_sheet_button")
            )
        }
        if (!filter.isDefault) {
            TextButton(onClick = onResetFilters, modifier = Modifier.testTag("reset_filters_chip_button")) {
                Text("Reset", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(Modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            Text("Filters", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = CosmicBlue)
            Spacer(modifier = Modifier.height(12.dp))
            Text("Categories", fontWeight = FontWeight.SemiBold)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FileCategory.entries.filter { it != FileCategory.ALL }.forEach { cat ->
                    FilterChip(
                        selected = filter.category == cat,
                        onClick = { onCategorySelected(cat) },
                        label = { Text(cat.name.lowercase().replaceFirstChar { it.uppercase() }) }
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onResetFilters) { Text("Reset all") }
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
fun FileDetailsDialog(item: FileItem, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(item.name, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Path: ${item.path}", fontFamily = FontFamily.Monospace, fontSize = 12.sp)
                Text("Size: ${FormatUtils.formatBytes(item.sizeBytes)}")
            }
        },
        confirmButton = {
            Button(onClick = onDismiss, colors = ButtonDefaults.buttonColors(containerColor = CosmicBlue)) {
                Text("Close", color = Color.White)
            }
        }
    )
}

@Composable
fun OfflineSearchInfoCard(modifier: Modifier = Modifier) {
    Card(modifier = modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = CosmicBlue.copy(alpha = 0.08f))) {
        Text(
            text = "Search works fully offline. Semantic matches appear with an orange badge.",
            modifier = Modifier.padding(12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = CosmicBlue
        )
    }
}

@Composable
fun QuickSearchCategoriesSection(onSelectCategory: (FileCategory) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(FileCategory.DOCUMENTS, FileCategory.IMAGES, FileCategory.VIDEOS, FileCategory.AUDIO).forEach { cat ->
            FilterChip(selected = false, onClick = { onSelectCategory(cat) }, label = { Text(cat.name.lowercase().replaceFirstChar { it.uppercase() }) })
        }
    }
}

@Composable
fun SearchHistorySection(
    history: List<String>,
    onHistoryItemClicked: (String) -> Unit,
    onDeleteHistoryItem: (String) -> Unit,
    onClearSearchHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (history.isEmpty()) return
    Column(modifier = modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Recent", fontWeight = FontWeight.SemiBold, color = CosmicBlue)
            TextButton(onClick = onClearSearchHistory) { Text("Clear") }
        }
        history.take(8).forEach { q ->
            Row(
                modifier = Modifier.fillMaxWidth().clickable { onHistoryItemClicked(q) }.padding(vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(q, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                IconButton(onClick = { onDeleteHistoryItem(q) }, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Close, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                }
            }
        }
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
    if (item.isDirectory) return Color(0xFF1A73E8)
    val ext = item.extension.lowercase()
    val mime = item.mimeType?.lowercase() ?: ""
    return when {
        mime.startsWith("image/") || ext in listOf("jpg", "jpeg", "png", "webp", "gif") -> Color(0xFF1E8E3E)
        mime.startsWith("video/") || ext in listOf("mp4", "mkv", "avi", "mov") -> Color(0xFFD93025)
        mime.startsWith("audio/") || ext in listOf("mp3", "wav", "aac", "flac") -> Color(0xFFF9AB00)
        else -> Color(0xFF1A73E8)
    }
}
