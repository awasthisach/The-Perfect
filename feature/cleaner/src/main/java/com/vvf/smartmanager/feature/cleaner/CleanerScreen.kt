package com.vvf.smartmanager.feature.cleaner

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Android
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vvf.smartmanager.core.common.FormatUtils
import com.vvf.smartmanager.core.model.DuplicateAutoSelectStrategy
import com.vvf.smartmanager.core.model.DuplicateFileGroup
import com.vvf.smartmanager.core.model.DuplicateLevel
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.JunkItem

val BhagwaOrange = Color(0xFFF47B20)
val CosmicBlue = Color(0xFF102B52)
val EmeraldGreen = Color(0xFF3FA34D)
val SkyCyan = Color(0xFF5BC0EB)
val SoftGold = Color(0xFFD4A95A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CleanerScreen(
    viewModel: CleanerViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.vvf.smartmanager.core.common.R.drawable.vvf_foundation_logo),
                            contentDescription = "Vishva Vijayaa Foundation Logo",
                            modifier = Modifier
                                .size(34.dp)
                                .padding(end = 8.dp)
                        )
                        Column {
                            Text(
                                text = "Smart Cleaner",
                                fontWeight = FontWeight.Bold,
                                color = BhagwaOrange
                            )
                            Text(
                                text = "॥ विजया ददाति विजयम् ॥",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = SoftGold
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.startScan() },
                        enabled = !uiState.isScanning,
                        modifier = Modifier.testTag("cleaner_refresh_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Re-scan Storage"
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            if (uiState.totalSelectedItemsCount > 0) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
                    modifier = Modifier.fillMaxWidth().testTag("cleaner_bottom_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = "Selected ${uiState.totalSelectedItemsCount} items",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Reclaim ${FormatUtils.formatBytes(uiState.totalSelectedBytes)}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = BhagwaOrange
                            )
                        }

                        Button(
                            onClick = { viewModel.showCleanConfirmation() },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("cleaner_execute_clean_btn")
                        ) {
                            Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Clean Now", fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .testTag("cleaner_screen_root")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Top Storage Gauge Card
            val wastedFromDuplicates = uiState.duplicateGroups.sumOf { it.wastedBytes }
            val wastedFromJunk = uiState.scanResult?.wastedBytes ?: 0L

            CleanerHeroCard(
                totalWastedBytes = wastedFromJunk + wastedFromDuplicates,
                isScanning = uiState.isScanning,
                scanProgressPct = uiState.scanProgressPct,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Primary Tabs: Duplicates vs Junk Files
            TabRow(
                selectedTabIndex = if (uiState.activeTab == CleanerTab.DUPLICATES) 0 else 1,
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .padding(bottom = 12.dp)
            ) {
                Tab(
                    selected = uiState.activeTab == CleanerTab.DUPLICATES,
                    onClick = { viewModel.setActiveTab(CleanerTab.DUPLICATES) },
                    text = {
                        Text(
                            text = "Duplicates (${uiState.duplicateGroups.size})",
                            fontWeight = if (uiState.activeTab == CleanerTab.DUPLICATES) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = { Icon(Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("tab_duplicates")
                )
                Tab(
                    selected = uiState.activeTab == CleanerTab.JUNK_FILES,
                    onClick = { viewModel.setActiveTab(CleanerTab.JUNK_FILES) },
                    text = {
                        val junkCount = (uiState.scanResult?.emptyFolders?.size ?: 0) + (uiState.scanResult?.tempFiles?.size ?: 0) + (uiState.scanResult?.largeFiles?.size ?: 0) + (uiState.scanResult?.apkFiles?.size ?: 0)
                        Text(
                            text = "Junk & Cache ($junkCount)",
                            fontWeight = if (uiState.activeTab == CleanerTab.JUNK_FILES) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    icon = { Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp)) },
                    modifier = Modifier.testTag("tab_junk")
                )
            }

            if (uiState.activeTab == CleanerTab.DUPLICATES) {
                // Duplicate Level 1, Level 2, Level 3 Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    FilterChip(
                        selected = uiState.duplicateLevel == DuplicateLevel.LEVEL_1_SIZE,
                        onClick = { viewModel.setDuplicateLevel(DuplicateLevel.LEVEL_1_SIZE) },
                        label = { Text("L1: Size", style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = { Icon(Icons.Default.Layers, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BhagwaOrange.copy(alpha = 0.2f), selectedLabelColor = BhagwaOrange),
                        modifier = Modifier.weight(1f).testTag("filter_level_1")
                    )
                    FilterChip(
                        selected = uiState.duplicateLevel == DuplicateLevel.LEVEL_2_HASH,
                        onClick = { viewModel.setDuplicateLevel(DuplicateLevel.LEVEL_2_HASH) },
                        label = { Text("L2: Hash", style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = { Icon(Icons.Default.Fingerprint, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BhagwaOrange.copy(alpha = 0.2f), selectedLabelColor = BhagwaOrange),
                        modifier = Modifier.weight(1f).testTag("filter_level_2")
                    )
                    FilterChip(
                        selected = uiState.duplicateLevel == DuplicateLevel.LEVEL_3_SIMILARITY,
                        onClick = { viewModel.setDuplicateLevel(DuplicateLevel.LEVEL_3_SIMILARITY) },
                        label = { Text("L3: AI Similarity", style = MaterialTheme.typography.labelMedium) },
                        leadingIcon = { Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(14.dp)) },
                        colors = FilterChipDefaults.filterChipColors(selectedContainerColor = BhagwaOrange.copy(alpha = 0.2f), selectedLabelColor = BhagwaOrange),
                        modifier = Modifier.weight(1.3f).testTag("filter_level_3")
                    )
                }

                // AI Level 3 Similarity Slider (70% - 95%)
                if (uiState.duplicateLevel == DuplicateLevel.LEVEL_3_SIMILARITY) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 10.dp)
                            .testTag("similarity_slider_card"),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.AutoAwesome,
                                        contentDescription = null,
                                        tint = BhagwaOrange,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "AI Similarity Threshold",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = CosmicBlue
                                    )
                                }
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = BhagwaOrange.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "${(uiState.similarityThreshold * 100).toInt()}% Match",
                                        color = BhagwaOrange,
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.labelMedium,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Detects near-duplicate documents, images, and texts using on-device TFLite vector embeddings.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )

                            Spacer(modifier = Modifier.height(6.dp))
                            Slider(
                                value = uiState.similarityThreshold,
                                onValueChange = { viewModel.setSimilarityThreshold(it) },
                                valueRange = 0.70f..0.95f,
                                steps = 24, // step sizes of 1% from 70% to 95%
                                colors = SliderDefaults.colors(
                                    thumbColor = BhagwaOrange,
                                    activeTrackColor = BhagwaOrange,
                                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("similarity_threshold_slider")
                            )

                            // Quick preset buttons
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                listOf(0.70f to "70% (Broad)", 0.80f to "80% (Balanced)", 0.90f to "90% (Strict)", 0.95f to "95% (Exact)").forEach { (thresh, label) ->
                                    val isSelected = ((uiState.similarityThreshold * 100).toInt()) == ((thresh * 100).toInt())
                                    TextButton(
                                        onClick = { viewModel.setSimilarityThreshold(thresh) },
                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                                        modifier = Modifier.testTag("preset_${(thresh * 100).toInt()}")
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isSelected) BhagwaOrange else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                // Quick Auto Select Chips
                if (uiState.duplicateGroups.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        OutlinedButton(
                            onClick = { viewModel.applyDuplicateAutoSelect(DuplicateAutoSelectStrategy.KEEP_OLDEST) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("auto_select_keep_oldest")
                        ) {
                            Text("Keep Oldest", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { viewModel.applyDuplicateAutoSelect(DuplicateAutoSelectStrategy.KEEP_NEWEST) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("auto_select_keep_newest")
                        ) {
                            Text("Keep Newest", style = MaterialTheme.typography.labelSmall)
                        }
                        OutlinedButton(
                            onClick = { viewModel.applyDuplicateAutoSelect(DuplicateAutoSelectStrategy.DESELECT_ALL) },
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.testTag("auto_select_clear")
                        ) {
                            Text("Clear", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }

                // Duplicates Group List
                if (uiState.duplicateGroups.isEmpty() && !uiState.isScanning) {
                    EmptyCleanState(
                        title = "No Duplicates Found",
                        message = "Your storage has no identical duplicate files."
                    )
                } else {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 90.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        itemsIndexed(uiState.duplicateGroups, key = { _, g -> g.matchKey }) { index, group ->
                            DuplicateGroupCard(
                                group = group,
                                groupIndex = index + 1,
                                selectedPaths = uiState.selectedDuplicatePaths,
                                onToggleItem = { path -> viewModel.toggleDuplicateSelection(path) }
                            )
                        }
                    }
                }
            } else {
                // Junk Files Content
                val scan = uiState.scanResult
                if (scan == null && uiState.isScanning) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = BhagwaOrange)
                    }
                } else if (scan != null) {
                    LazyColumn(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        contentPadding = PaddingValues(bottom = 90.dp),
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // 1. Empty Directories
                        if (scan.emptyFolders.isNotEmpty()) {
                            item {
                                JunkSectionCard(
                                    title = "Empty Directories",
                                    subtitle = "${scan.emptyFolders.size} zero-byte empty folders found",
                                    icon = Icons.Default.FolderOpen,
                                    color = BhagwaOrange,
                                    items = scan.emptyFolders,
                                    selectedPaths = uiState.selectedEmptyFolderPaths,
                                    onToggleItem = { viewModel.toggleEmptyFolderSelection(it) }
                                )
                            }
                        }

                        // 2. Temp & Cache Files
                        if (scan.tempFiles.isNotEmpty()) {
                            item {
                                JunkSectionCard(
                                    title = "Temporary & Cache Files",
                                    subtitle = "${scan.tempFiles.size} files (${FormatUtils.formatBytes(scan.tempFiles.sumOf { it.sizeBytes })})",
                                    icon = Icons.Default.DeleteSweep,
                                    color = EmeraldGreen,
                                    items = scan.tempFiles,
                                    selectedPaths = uiState.selectedTempFilePaths,
                                    onToggleItem = { viewModel.toggleTempFileSelection(it) }
                                )
                            }
                        }

                        // 3. Large Files (>100MB)
                        if (scan.largeFiles.isNotEmpty()) {
                            item {
                                JunkSectionCard(
                                    title = "Large Files (>100 MB)",
                                    subtitle = "${scan.largeFiles.size} files taking ${FormatUtils.formatBytes(scan.largeFiles.sumOf { it.sizeBytes })})",
                                    icon = Icons.Default.Warning,
                                    color = CosmicBlue,
                                    items = scan.largeFiles,
                                    selectedPaths = uiState.selectedLargeFilePaths,
                                    onToggleItem = { viewModel.toggleLargeFileSelection(it) }
                                )
                            }
                        }

                        // 4. APK Files
                        if (scan.apkFiles.isNotEmpty()) {
                            item {
                                JunkSectionCard(
                                    title = "APK Installers",
                                    subtitle = "${scan.apkFiles.size} installation files (${FormatUtils.formatBytes(scan.apkFiles.sumOf { it.sizeBytes })})",
                                    icon = Icons.Default.Android,
                                    color = SoftGold,
                                    items = scan.apkFiles,
                                    selectedPaths = uiState.selectedApkPaths,
                                    onToggleItem = { viewModel.toggleApkSelection(it) }
                                )
                            }
                        }

                        if (scan.emptyFolders.isEmpty() && scan.tempFiles.isEmpty() && scan.largeFiles.isEmpty() && scan.apkFiles.isEmpty()) {
                            item {
                                EmptyCleanState(
                                    title = "Storage is Clean",
                                    message = "No temporary cache or empty junk folders found."
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation Modal
    if (uiState.showCleanConfirmDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissCleanConfirmation() },
            icon = {
                Icon(
                    imageVector = Icons.Default.CleaningServices,
                    contentDescription = null,
                    tint = BhagwaOrange,
                    modifier = Modifier.size(28.dp)
                )
            },
            title = { Text("Confirm Cleanup", fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        text = "You are about to delete ${uiState.totalSelectedItemsCount} selected item(s).",
                        style = MaterialTheme.typography.bodyMedium
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Total Space Reclaimed: ${FormatUtils.formatBytes(uiState.totalSelectedBytes)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = BhagwaOrange
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.executeClean() },
                    colors = ButtonDefaults.buttonColors(containerColor = BhagwaOrange),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.testTag("confirm_clean_action_btn")
                ) {
                    Text("Proceed & Clean", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissCleanConfirmation() }) {
                    Text("Cancel")
                }
            },
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier.testTag("clean_confirm_dialog")
        )
    }
}

@Composable
private fun CleanerHeroCard(
    totalWastedBytes: Long,
    isScanning: Boolean,
    scanProgressPct: Float,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("cleaner_hero_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicBlue),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(BhagwaOrange.copy(alpha = 0.2f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = BhagwaOrange,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Cleanable Storage",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = if (isScanning) "Analyzing file checksums..." else "Ready to optimize",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.8f)
                        )
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = BhagwaOrange
                ) {
                    Text(
                        text = FormatUtils.formatBytes(totalWastedBytes),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            if (isScanning) {
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { scanProgressPct },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp)),
                    color = BhagwaOrange,
                    trackColor = Color.White.copy(alpha = 0.2f)
                )
            }
        }
    }
}

@Composable
private fun DuplicateGroupCard(
    group: DuplicateFileGroup,
    groupIndex: Int,
    selectedPaths: Set<String>,
    onToggleItem: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("duplicate_group_$groupIndex")
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = BhagwaOrange.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "#$groupIndex",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = BhagwaOrange,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = group.files.firstOrNull()?.name ?: "Duplicate File",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = "${FormatUtils.formatBytes(group.fileSizeBytes)} each (${group.fileCount} copies)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            group.files.forEachIndexed { i, file ->
                val isSelected = selectedPaths.contains(file.path)
                val isFirst = i == 0

                Surface(
                    color = if (isSelected) MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 3.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .clickable { onToggleItem(file.path) }
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.RadioButtonUnchecked,
                            contentDescription = null,
                            tint = if (isSelected) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.path,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "Modified: ${FormatUtils.formatShortDate(file.lastModified)} ${if (isFirst) "(Original / Oldest)" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (isFirst) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun JunkSectionCard(
    title: String,
    subtitle: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    items: List<JunkItem>,
    selectedPaths: Set<String>,
    onToggleItem: (String) -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(color.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = icon, contentDescription = null, tint = color, modifier = Modifier.size(20.dp))
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                    Text(text = subtitle, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            items.take(5).forEach { junk ->
                val isSelected = selectedPaths.contains(junk.path)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onToggleItem(junk.path) }
                        .padding(horizontal = 4.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = isSelected,
                        onCheckedChange = { onToggleItem(junk.path) }
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = junk.name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = if (junk.isDirectory) junk.path else "${FormatUtils.formatBytes(junk.sizeBytes)} • ${junk.path}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            if (items.size > 5) {
                Text(
                    text = "+ ${items.size - 5} more items",
                    style = MaterialTheme.typography.labelSmall,
                    color = BhagwaOrange,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun EmptyCleanState(
    title: String,
    message: String
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(CircleShape)
                    .background(EmeraldGreen.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = EmeraldGreen,
                    modifier = Modifier.size(32.dp)
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            Text(text = title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(text = message, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
