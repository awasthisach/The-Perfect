package com.vvf.smartmanager.feature.explorer

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.material3.Button
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
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Widgets
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
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
import com.vvf.smartmanager.feature.explorer.components.OcrResultDialog
import com.vvf.smartmanager.feature.explorer.components.RecycleBinView
import com.vvf.smartmanager.feature.explorer.components.RenameDialog
import com.vvf.smartmanager.feature.explorer.components.StorageOverviewCard
import com.vvf.smartmanager.feature.explorer.components.SyncToCloudDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExplorerScreen(
    viewModel: ExplorerViewModel,
    onNavigateToSettings: (() -> Unit)? = null,
    onNavigateToPlugins: (() -> Unit)? = null,
    onStorageAccessGranted: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val requestLegacyReadPermission = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            onStorageAccessGranted()
            viewModel.reloadCurrentLocation()
        }
    }

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

    if (uiState.needsStoragePermission) {
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && Environment.isExternalStorageManager()) {
                        onStorageAccessGranted()
                    }
                    viewModel.reloadCurrentLocation()
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
        StoragePermissionRequiredScreen(
            onGrantAccess = {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    openAllFilesAccessSettings(context)
                } else {
                    requestLegacyReadPermission.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
            },
            onRetry = { viewModel.reloadCurrentLocation() }
        )
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isSelectionMode) {
                        Text(text = "${uiState.selectedPaths.size} Selected", fontWeight = FontWeight.Bold)
                    } else {
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
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
                                    modifier = Modifier.fillMaxSize().padding(2.dp)
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
                        IconButton(onClick = { viewModel.clearSelection() }, modifier = Modifier.testTag("explorer_clear_selection_btn")) {
                            Icon(Icons.Default.Close, contentDescription = "Close Selection")
                        }
                    }
                },
                actions = {
                    if (uiState.isSelectionMode) {
                        IconButton(onClick = { viewModel.selectAll() }, modifier = Modifier.testTag("explorer_select_all_btn")) {
                            Icon(Icons.Default.SelectAll, contentDescription = "Select All")
                        }
                    } else {
                        IconButton(onClick = { viewModel.toggleViewMode() }, modifier = Modifier.testTag("explorer_toggle_view_mode_btn")) {
                            Icon(
                                imageVector = if (uiState.viewMode == FileViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                                contentDescription = "Toggle View Mode"
                            )
                        }
                        Box {
                            IconButton(onClick = { showSortMenu = true }, modifier = Modifier.testTag("explorer_sort_btn")) {
                                Icon(Icons.AutoMirrored.Filled.Sort, contentDescription = "Sort Files")
                            }
                            DropdownMenu(expanded = showSortMenu, onDismissRequest = { showSortMenu = false }) {
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
                                        onClick = { viewModel.setSortOption(option); showSortMenu = false }
                                    )
                                }
                            }
                        }
                        Box {
                            IconButton(onClick = { showMoreMenu = true }, modifier = Modifier.testTag("explorer_more_menu_btn")) {
                                Icon(Icons.Default.MoreVert, contentDescription = "More Options")
                            }
                            DropdownMenu(expanded = showMoreMenu, onDismissRequest = { showMoreMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text("New Folder") },
                                    leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                                    onClick = { showMoreMenu = false; viewModel.showDialog(ExplorerDialogState.CreateFolder) }
                                )
                                DropdownMenuItem(
                                    text = { Text("New File") },
                                    leadingIcon = { Icon(Icons.Default.NoteAdd, contentDescription = null) },
                                    onClick = { showMoreMenu = false; viewModel.showDialog(ExplorerDialogState.CreateFile) }
                                )
                                DropdownMenuItem(
                                    text = { Text(if (uiState.showHidden) "Hide Hidden Files" else "Show Hidden Files") },
                                    leadingIcon = { Icon(if (uiState.showHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = null) },
                                    onClick = { showMoreMenu = false; viewModel.toggleShowHidden() }
                                )
                                DropdownMenuItem(
                                    text = { Text("Recycle Bin") },
                                    leadingIcon = { Icon(Icons.Default.Delete, contentDescription = null) },
                                    onClick = { showMoreMenu = false; viewModel.openTrashView() }
                                )
                                if (onNavigateToPlugins != null || onNavigateToSettings != null) {
                                    androidx.compose.material3.HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                                }
                                onNavigateToPlugins?.let { navPlugins ->
                                    DropdownMenuItem(
                                        text = { Text("Plugins & Addons") },
                                        leadingIcon = { Icon(Icons.Default.Widgets, contentDescription = null) },
                                        onClick = { showMoreMenu = false; navPlugins() }
                                    )
                                }
                                onNavigateToSettings?.let { navSettings ->
                                    DropdownMenuItem(
                                        text = { Text("Settings & Privacy") },
                                        leadingIcon = { Icon(Icons.Default.Settings, contentDescription = null) },
                                        onClick = { showMoreMenu = false; navSettings() }
                                    )
                                }
                            }
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        floatingActionButton = {
            if (!uiState.isSelectionMode) {
                Box {
                    FloatingActionButton(
                        onClick = { showFabMenu = !showFabMenu },
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.testTag("explorer_fab")
                    ) {
                        Icon(if (showFabMenu) Icons.Default.Close else Icons.Default.Add, contentDescription = "Actions")
                    }
                }
            }
        },
        modifier = modifier.fillMaxSize().testTag("explorer_screen")
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            uiState.storageBreakdown?.let { breakdown ->
                StorageOverviewCard(breakdown = breakdown, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp))
            }
            if (uiState.selectedCategory == FileCategory.ALL) {
                BreadcrumbBar(
                    breadcrumbs = uiState.breadcrumbs,
                    onNavigate = { path -> viewModel.loadDirectory(path) },
                    modifier = Modifier.padding(horizontal = 8.dp)
                )
            }
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.setSearchQuery(it) },
                placeholder = { Text("Search files…") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.clearSearch() }) {
                            Icon(Icons.Default.Clear, contentDescription = "Clear")
                        }
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp).testTag("explorer_search")
            )
            if (uiState.isLoading) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else if (uiState.filteredFiles.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(8.dp))
                        Text("No files here", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            } else if (uiState.viewMode == FileViewMode.LIST) {
                LazyColumn(
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.filteredFiles, key = { it.path }) { file ->
                        FileListItem(
                            file = file,
                            isSelected = file.path in uiState.selectedPaths,
                            isSelectionMode = uiState.isSelectionMode,
                            onItemClick = { viewModel.navigateInto(it) },
                            onItemLongClick = { viewModel.toggleItemSelection(it.path) },
                            onRenameClick = { viewModel.showDialog(ExplorerDialogState.Rename(it)) },
                            onDeleteClick = { viewModel.showDialog(ExplorerDialogState.DeleteConfirm(listOf(it))) },
                            onDetailsClick = { viewModel.showDialog(ExplorerDialogState.FileDetails(it)) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onSyncToCloud = { viewModel.showDialog(ExplorerDialogState.SyncToCloudConfirm(it)) }
                        )
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Adaptive(120.dp),
                    contentPadding = PaddingValues(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxSize()
                ) {
                    items(uiState.filteredFiles, key = { it.path }) { file ->
                        FileGridItem(
                            file = file,
                            isSelected = file.path in uiState.selectedPaths,
                            isSelectionMode = uiState.isSelectionMode,
                            onItemClick = { viewModel.navigateInto(it) },
                            onItemLongClick = { viewModel.toggleItemSelection(it.path) },
                            onRenameClick = { viewModel.showDialog(ExplorerDialogState.Rename(it)) },
                            onDeleteClick = { viewModel.showDialog(ExplorerDialogState.DeleteConfirm(listOf(it))) },
                            onDetailsClick = { viewModel.showDialog(ExplorerDialogState.FileDetails(it)) },
                            onToggleFavorite = { viewModel.toggleFavorite(it) },
                            onSyncToCloud = { viewModel.showDialog(ExplorerDialogState.SyncToCloudConfirm(it)) }
                        )
                    }
                }
            }
        }
    }

    when (val dialog = uiState.dialogState) {
        is ExplorerDialogState.None -> {}
        is ExplorerDialogState.CreateFolder -> {
            CreateFolderDialog(onDismiss = { viewModel.dismissDialog() }, onConfirm = { viewModel.createFolder(it) })
        }
        is ExplorerDialogState.CreateFile -> {
            CreateFileDialog(onDismiss = { viewModel.dismissDialog() }, onConfirm = { viewModel.createFile(it) })
        }
        is ExplorerDialogState.Rename -> {
            RenameDialog(file = dialog.file, onDismiss = { viewModel.dismissDialog() }, onConfirm = { viewModel.renameFile(dialog.file, it) })
        }
        is ExplorerDialogState.DeleteConfirm -> {
            DeleteConfirmDialog(files = dialog.files, onDismiss = { viewModel.dismissDialog() }, onConfirm = { viewModel.deleteItems(dialog.files, it) })
        }
        is ExplorerDialogState.SyncToCloudConfirm -> {
            SyncToCloudDialog(file = dialog.file, onDismiss = { viewModel.dismissDialog() }, onConfirm = { viewModel.syncFileToCloud(dialog.file) })
        }
        is ExplorerDialogState.FileDetails -> {
            FileDetailsDialog(
                file = dialog.file,
                onDismiss = { viewModel.dismissDialog() },
                onOpen = {
                    viewModel.dismissDialog()
                    viewModel.navigateInto(dialog.file)
                },
                onOcr = { viewModel.requestOcr(dialog.file) }
            )
        }
        is ExplorerDialogState.OcrInProgress -> {
            androidx.compose.material3.AlertDialog(
                onDismissRequest = {},
                title = { Text("Scanning OCR…") },
                text = { Text("Extracting English + Hindi text…") },
                confirmButton = {}
            )
        }
        is ExplorerDialogState.OcrResult -> {
            OcrResultDialog(
                fileName = dialog.fileName,
                text = dialog.text,
                onDismiss = { viewModel.dismissDialog() },
                onCopy = {
                    val cm = context.getSystemService(ClipboardManager::class.java)
                    cm?.setPrimaryClip(ClipData.newPlainText("OCR", dialog.text))
                }
            )
        }
        is ExplorerDialogState.Progress -> {
            OperationProgressDialog(progress = dialog.progress)
        }
        is ExplorerDialogState.PasteConfirm -> {}
    }
}

@Composable
private fun StoragePermissionRequiredScreen(
    onGrantAccess: () -> Unit,
    onRetry: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(24.dp)
        ) {
            Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(56.dp), tint = MaterialTheme.colorScheme.primary)
            Text("Storage access required", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(
                "Grant All Files Access so VVF can browse, open, and manage files including SD card.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Button(onClick = onGrantAccess, modifier = Modifier.testTag("explorer_grant_storage_access_btn")) {
                Text("Grant All Files Access")
            }
            androidx.compose.material3.TextButton(onClick = onRetry, modifier = Modifier.testTag("explorer_retry_storage_access_btn")) {
                Text("Try again")
            }
        }
    }
}

private fun openAllFilesAccessSettings(context: Context) {
    val appSettingsIntent = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )
    try {
        context.startActivity(appSettingsIntent)
    } catch (_: Exception) {
        context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
    }
}
