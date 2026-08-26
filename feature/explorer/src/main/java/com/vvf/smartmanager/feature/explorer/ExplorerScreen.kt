package com.vvf.smartmanager.feature.explorer

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.vvf.smartmanager.core.common.R as CommonR
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vvf.smartmanager.core.model.FileCategory
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.FileSortOption
import com.vvf.smartmanager.core.model.FileViewMode
import com.vvf.smartmanager.feature.explorer.components.BreadcrumbBar
import com.vvf.smartmanager.feature.explorer.components.CreateFileDialog
import com.vvf.smartmanager.feature.explorer.components.CreateFolderDialog
import com.vvf.smartmanager.feature.explorer.components.DeleteConfirmDialog
import com.vvf.smartmanager.feature.explorer.components.FileDetailsDialog
import com.vvf.smartmanager.feature.explorer.components.FileGridItem
import com.vvf.smartmanager.feature.explorer.components.FileListItem
import com.vvf.smartmanager.feature.explorer.components.OperationProgressDialog
import com.vvf.smartmanager.feature.explorer.components.RecycleBinView
import com.vvf.smartmanager.feature.explorer.components.RenameDialog
import com.vvf.smartmanager.feature.explorer.components.StorageOverviewCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    viewModel: ExplorerViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }
    var showFabMenu by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    if (uiState.isTrashViewOpen) {
        RecycleBinView(
            trashFiles = uiState.trashFiles,
            onClose = { viewModel.closeTrashView() },
            onRestoreItem = { item -> viewModel.restoreTrashItems(listOf(item.path)) },
            onRestoreAll = { viewModel.restoreTrashItems(uiState.trashFiles.map { it.path }) },
            onEmptyTrash = { viewModel.emptyTrash() }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isSelectionMode) {
                        Text(
                            text = "${uiState.selectedPaths.size} Selected",
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 2.dp)
                        ) {
                            Surface(
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary),
                                shadowElevation = 2.dp,
                                modifier = Modifier.size(38.dp)
                            ) {
                                Image(
                                    painter = painterResource(id = CommonR.drawable.vvf_foundation_logo),
                                    contentDescription = "Vishva Vijayaa Foundation Logo",
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .padding(2.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "VVF Smart Manager",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "॥ विजया ददाति विजयम् ॥",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                },
                navigationIcon = {
                    if (uiState.isSelectionMode) {
                        IconButton(
                            onClick = { viewModel.clearSelection() },
                            modifier = Modifier.testTag("explorer_clear_selection_btn")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close Selection")
                        }
                    }
                },
                actions = {
                    if (uiState.isSelectionMode) {
                        IconButton(
                            onClick = { viewModel.selectAll() },
                            modifier = Modifier.testTag("explorer_select_all_btn")
                        ) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                    } else {
                        // View mode toggle (List vs Grid)
                        IconButton(
                            onClick = { viewModel.toggleViewMode() },
                            modifier = Modifier.testTag("explorer_toggle_view_mode_btn")
                        ) {
                            Icon(
                                imageVector = if (uiState.viewMode == FileViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                                contentDescription = "Toggle View Mode"
                            )
                        }

                        // Sort Menu
                        Box {
                            IconButton(
                                onClick = { showSortMenu = true },
                                modifier = Modifier.testTag("explorer_sort_btn")
                            ) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort Files")
                            }
                            DropdownMenu(
                                expanded = showSortMenu,
                                onDismissRequest = { showSortMenu = false }
                            ) {
                                FileSortOption.entries.forEach { option ->
                                    DropdownMenuItem(
                                        text = {
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                Text(
                                                    text = option.displayName,
                                                    fontWeight = if (uiState.sortOption == option) FontWeight.Bold else FontWeight.Normal,
                                                    color = if (uiState.sortOption == option) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                )
                                                if (uiState.sortOption == option) {
                                                    Spacer(modifier = Modifier.width(8.dp))
                                                    Icon(Icons.Default.Check, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                                }
                                            }
                                        },
                                        onClick = {
                                            viewModel.setSortOption(option)
                                            showSortMenu = false
                                        }
                                    )
                                }
                            }
                        }

                        // More Menu (Show Hidden, New Folder, Recycle Bin)
                        Box {
                            IconButton(
                                onClick = { showMoreMenu = true },
                                modifier = Modifier.testTag("explorer_more_menu_btn")
                            ) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                            }
                            DropdownMenu(
                                expanded = showMoreMenu,
                                onDismissRequest = { showMoreMenu = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("New Folder") },
                                    leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.showDialog(ExplorerDialogState.CreateFolder)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("New File") },
                                    leadingIcon = { Icon(Icons.Default.NoteAdd, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.showDialog(ExplorerDialogState.CreateFile)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (uiState.showHidden) "Hide Hidden Files" else "Show Hidden Files") },
                                    leadingIcon = {
                                        Icon(
                                            if (uiState.showHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = null
                                        )
                                    },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.toggleShowHidden()
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Recycle Bin") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = {
                                        showMoreMenu = false
                                        viewModel.openTrashView()
                                    }
                                )
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!uiState.isSelectionMode) {
                Column(horizontalAlignment = Alignment.End) {
                    if (uiState.clipboardSourcePaths.isNotEmpty()) {
                        ExtendedFloatingActionButton(
                            onClick = { viewModel.pasteClipboard() },
                            icon = { Icon(Icons.Default.ContentPaste, contentDescription = null) },
                            text = { Text("Paste (${uiState.clipboardSourcePaths.size})") },
                            containerColor = MaterialTheme.colorScheme.secondary,
                            contentColor = MaterialTheme.colorScheme.onSecondary,
                            modifier = Modifier
                                .padding(bottom = 12.dp)
                                .testTag("explorer_paste_fab")
                        )
                    }

                    FloatingActionButton(
                        onClick = { viewModel.showDialog(ExplorerDialogState.CreateFolder) },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("explorer_add_fab")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Create New Item")
                    }
                }
            }
        },
        bottomBar = {
            AnimatedVisibility(
                visible = uiState.isSelectionMode,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
            ) {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    shadowElevation = 8.dp,
                    shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
                    modifier = Modifier.fillMaxWidth().testTag("explorer_bottom_action_bar")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceAround,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(
                            onClick = { viewModel.copySelectedToClipboard() },
                            modifier = Modifier.testTag("action_copy_btn")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ContentCopy, contentDescription = "Copy")
                                Text("Copy", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(
                            onClick = { viewModel.cutSelectedToClipboard() },
                            modifier = Modifier.testTag("action_cut_btn")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ContentCut, contentDescription = "Cut/Move")
                                Text("Move", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                        IconButton(
                            onClick = {
                                val selectedFiles = uiState.files.filter { uiState.selectedPaths.contains(it.path) }
                                viewModel.showDialog(ExplorerDialogState.DeleteConfirm(selectedFiles))
                            },
                            modifier = Modifier.testTag("action_delete_btn")
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error)
                                Text("Delete", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }
                }
            }
        },
        modifier = modifier
            .fillMaxSize()
            .testTag("explorer_screen_root")
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp)
        ) {
            // Storage Health and Overview Banner
            StorageOverviewCard(
                storageBreakdown = uiState.storageBreakdown,
                selectedCategory = uiState.selectedCategory,
                onCategorySelected = { category -> viewModel.selectCategory(category) },
                modifier = Modifier.padding(top = 8.dp, bottom = 12.dp)
            )

            // Search input field (Google Material 3 Pill Search Bar)
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { 
                    Text(
                        "Search files, folders & docs...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    ) 
                },
                leadingIcon = { 
                    Icon(
                        Icons.Default.Search, 
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(22.dp)
                    ) 
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSearch() }) {
                            Icon(
                                Icons.Default.Clear, 
                                contentDescription = "Clear",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(28.dp),
                colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Transparent
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 10.dp)
                    .testTag("explorer_search_bar")
            )

            // Breadcrumb Navigation Bar (when in folder browsing mode)
            if (uiState.selectedCategory == FileCategory.ALL) {
                BreadcrumbBar(
                    breadcrumbs = uiState.breadcrumbs,
                    canNavigateUp = uiState.breadcrumbs.size > 1,
                    onNavigateUp = { viewModel.navigateUp() },
                    onBreadcrumbClicked = { path -> viewModel.loadDirectory(path) },
                    modifier = Modifier.padding(bottom = 10.dp)
                )
            }

            // File items / Loading / Empty state
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else if (uiState.filteredFiles.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Box(
                                modifier = Modifier
                                    .size(64.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.FolderOpen,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(32.dp)
                                )
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                text = if (uiState.searchQuery.isNotEmpty()) "No matching files found" else "This directory is empty",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Use '+' to create folders or files.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    if (uiState.viewMode == FileViewMode.LIST) {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 80.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("explorer_files_list")
                        ) {
                            items(uiState.filteredFiles, key = { it.path }) { item ->
                                FileListItem(
                                    file = item,
                                    isSelected = uiState.selectedPaths.contains(item.path),
                                    isSelectionMode = uiState.isSelectionMode,
                                    onItemClick = { clicked -> viewModel.navigateInto(clicked) },
                                    onItemLongClick = { clicked -> viewModel.toggleItemSelection(clicked.path) },
                                    onRenameClick = { clicked -> viewModel.showDialog(ExplorerDialogState.Rename(clicked)) },
                                    onDeleteClick = { clicked -> viewModel.showDialog(ExplorerDialogState.DeleteConfirm(listOf(clicked))) },
                                    onDetailsClick = { clicked -> viewModel.showDialog(ExplorerDialogState.FileDetails(clicked)) },
                                    onToggleFavorite = { clicked -> viewModel.toggleFavorite(clicked) }
                                )
                            }
                        }
                    } else {
                        LazyVerticalGrid(
                            columns = GridCells.Adaptive(minSize = 100.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                            contentPadding = PaddingValues(bottom = 80.dp),
                            modifier = Modifier
                                .fillMaxSize()
                                .testTag("explorer_files_grid")
                        ) {
                            items(uiState.filteredFiles, key = { it.path }) { item ->
                                FileGridItem(
                                    file = item,
                                    isSelected = uiState.selectedPaths.contains(item.path),
                                    isSelectionMode = uiState.isSelectionMode,
                                    onItemClick = { clicked -> viewModel.navigateInto(clicked) },
                                    onItemLongClick = { clicked -> viewModel.toggleItemSelection(clicked.path) },
                                    onRenameClick = { clicked -> viewModel.showDialog(ExplorerDialogState.Rename(clicked)) },
                                    onDeleteClick = { clicked -> viewModel.showDialog(ExplorerDialogState.DeleteConfirm(listOf(clicked))) },
                                    onDetailsClick = { clicked -> viewModel.showDialog(ExplorerDialogState.FileDetails(clicked)) },
                                    onToggleFavorite = { clicked -> viewModel.toggleFavorite(clicked) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // Modal Dialogs Handling
    when (val dialog = uiState.dialogState) {
        is ExplorerDialogState.CreateFolder -> {
            CreateFolderDialog(
                onDismiss = { viewModel.dismissDialog() },
                onConfirm = { name -> viewModel.createFolder(name) }
            )
        }
        is ExplorerDialogState.CreateFile -> {
            CreateFileDialog(
                onDismiss = { viewModel.dismissDialog() },
                onConfirm = { name -> viewModel.createFile(name) }
            )
        }
        is ExplorerDialogState.Rename -> {
            RenameDialog(
                file = dialog.file,
                onDismiss = { viewModel.dismissDialog() },
                onConfirm = { newName -> viewModel.renameFile(dialog.file, newName) }
            )
        }
        is ExplorerDialogState.DeleteConfirm -> {
            DeleteConfirmDialog(
                files = dialog.files,
                onDismiss = { viewModel.dismissDialog() },
                onConfirm = { permanent -> viewModel.deleteItems(dialog.files, permanent) }
            )
        }
        is ExplorerDialogState.FileDetails -> {
            FileDetailsDialog(
                file = dialog.file,
                onDismiss = { viewModel.dismissDialog() }
            )
        }
        is ExplorerDialogState.Progress -> {
            OperationProgressDialog(progress = dialog.progress)
        }
        is ExplorerDialogState.None -> {}
        else -> {}
    }
}
