package com.vvf.smartmanager.feature.explorer.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vvf.smartmanager.core.common.FormatUtils
import com.vvf.smartmanager.core.model.FileCategory
import com.vvf.smartmanager.core.model.StorageBreakdown

// Google Material You Category Colors
val GoogleBlueColor = Color(0xFF1A73E8)
val GoogleGreenColor = Color(0xFF1E8E3E)
val GoogleYellowColor = Color(0xFFF9AB00)
val GoogleRedColor = Color(0xFFD93025)
val GooglePurpleColor = Color(0xFF9334E6)
val GoogleCyanColor = Color(0xFF12B5CB)

// Compatibility aliases
val BhagwaOrangeColor = GoogleYellowColor
val CosmicBlueColor = GoogleBlueColor
val SoftGoldColor = Color(0xFFFDD663)
val EmeraldGreenColor = GoogleGreenColor
val SkyCyanColor = GoogleCyanColor

@Composable
fun StorageOverviewCard(
    storageBreakdown: StorageBreakdown?,
    selectedCategory: FileCategory,
    onCategorySelected: (FileCategory) -> Unit,
    modifier: Modifier = Modifier
) {
    val usedBytes = storageBreakdown?.usedBytes ?: 0L
    val totalBytes = storageBreakdown?.totalBytes ?: (64L * 1024 * 1024 * 1024)
    val usedPct = storageBreakdown?.usedPercentage ?: 0.35f
    val pctText = FormatUtils.formatPercentage(usedPct)
    val usedFormatted = FormatUtils.formatBytes(usedBytes)
    val totalFormatted = FormatUtils.formatBytes(totalBytes)

    val animatedProgress by animateFloatAsState(
        targetValue = usedPct.coerceIn(0f, 1f),
        label = "storage_progress"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("storage_overview_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp)
        ) {
            // Storage Bar Header (Google Files Style)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Storage,
                            contentDescription = "Storage",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(14.dp))
                    Column {
                        Text(
                            text = "Internal Storage",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "$usedFormatted used of $totalFormatted",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                    modifier = Modifier.testTag("storage_pct_badge")
                ) {
                    Text(
                        text = pctText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Multi-segment Google 4-Color Progress Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(8.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(animatedProgress)
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(
                                    GoogleBlueColor,
                                    GoogleCyanColor,
                                    GoogleGreenColor,
                                    GoogleYellowColor
                                )
                            )
                        )
                )
            }

            Spacer(modifier = Modifier.height(18.dp))

            // Category Chips Row (Material You Filter Chips)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CategoryChipItem(
                    label = "All Files",
                    icon = Icons.Default.Folder,
                    isSelected = selectedCategory == FileCategory.ALL,
                    color = GoogleBlueColor,
                    onClick = { onCategorySelected(FileCategory.ALL) }
                )
                CategoryChipItem(
                    label = "Images",
                    icon = Icons.Default.Image,
                    isSelected = selectedCategory == FileCategory.IMAGES,
                    color = GoogleGreenColor,
                    onClick = { onCategorySelected(FileCategory.IMAGES) }
                )
                CategoryChipItem(
                    label = "Videos",
                    icon = Icons.Default.Movie,
                    isSelected = selectedCategory == FileCategory.VIDEOS,
                    color = GoogleRedColor,
                    onClick = { onCategorySelected(FileCategory.VIDEOS) }
                )
                CategoryChipItem(
                    label = "Audio",
                    icon = Icons.Default.Audiotrack,
                    isSelected = selectedCategory == FileCategory.AUDIO,
                    color = GoogleYellowColor,
                    onClick = { onCategorySelected(FileCategory.AUDIO) }
                )
                CategoryChipItem(
                    label = "Documents",
                    icon = Icons.Default.Description,
                    isSelected = selectedCategory == FileCategory.DOCUMENTS,
                    color = GooglePurpleColor,
                    onClick = { onCategorySelected(FileCategory.DOCUMENTS) }
                )
                CategoryChipItem(
                    label = "APKs",
                    icon = Icons.Default.Apps,
                    isSelected = selectedCategory == FileCategory.APKS,
                    color = GoogleGreenColor,
                    onClick = { onCategorySelected(FileCategory.APKS) }
                )
                CategoryChipItem(
                    label = "Archives",
                    icon = Icons.Default.Archive,
                    isSelected = selectedCategory == FileCategory.ARCHIVES,
                    color = GoogleYellowColor,
                    onClick = { onCategorySelected(FileCategory.ARCHIVES) }
                )
                CategoryChipItem(
                    label = "Favorites",
                    icon = Icons.Default.Star,
                    isSelected = selectedCategory == FileCategory.FAVORITES,
                    color = GoogleYellowColor,
                    onClick = { onCategorySelected(FileCategory.FAVORITES) }
                )
                CategoryChipItem(
                    label = "Recycle Bin",
                    icon = Icons.Default.Delete,
                    isSelected = selectedCategory == FileCategory.TRASH,
                    color = GoogleRedColor,
                    onClick = { onCategorySelected(FileCategory.TRASH) }
                )
            }
        }
    }
}

@Composable
private fun CategoryChipItem(
    label: String,
    icon: ImageVector,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit
) {
    FilterChip(
        selected = isSelected,
        onClick = onClick,
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
            )
        },
        leadingIcon = {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (isSelected) MaterialTheme.colorScheme.onPrimary else color,
                modifier = Modifier.size(18.dp)
            )
        },
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primary,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimary,
            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimary,
            containerColor = MaterialTheme.colorScheme.surface,
            labelColor = MaterialTheme.colorScheme.onSurfaceVariant
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = true,
            selected = isSelected,
            borderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
            selectedBorderColor = MaterialTheme.colorScheme.primary
        ),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.testTag("category_chip_${label.lowercase().replace(" ", "_")}")
    )
}
