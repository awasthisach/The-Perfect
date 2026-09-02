package com.vvf.smartmanager.feature.explorer

import com.vvf.smartmanager.core.model.FileCategory
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.FileOperationProgress
import com.vvf.smartmanager.core.model.FileSortOption
import com.vvf.smartmanager.core.model.FileViewMode
import com.vvf.smartmanager.core.model.StorageBreakdown

sealed interface ExplorerDialogState {
    data object None : ExplorerDialogState
    data object CreateFolder : ExplorerDialogState
    data object CreateFile : ExplorerDialogState
    data class Rename(val file: FileItem) : ExplorerDialogState
    data class FileDetails(val file: FileItem) : ExplorerDialogState
    data class DeleteConfirm(val files: List<FileItem>) : ExplorerDialogState
    data class SyncToCloudConfirm(val file: FileItem) : ExplorerDialogState
    data class PasteConfirm(val operation: String, val targetDir: String, val count: Int) : ExplorerDialogState
    data class Progress(val progress: FileOperationProgress) : ExplorerDialogState
}

data class ExplorerUiState(
    val currentPath: String = "",
    val breadcrumbs: List<Pair<String, String>> = emptyList(), // Pair(displayName, absolutePath)
    val files: List<FileItem> = emptyList(),
    val filteredFiles: List<FileItem> = emptyList(),
    val selectedCategory: FileCategory = FileCategory.ALL,
    val sortOption: FileSortOption = FileSortOption.NAME_ASC,
    val viewMode: FileViewMode = FileViewMode.LIST,
    val showHidden: Boolean = false,
    val searchQuery: String = "",
    val isSearchActive: Boolean = false,
    val selectedPaths: Set<String> = emptySet(),
    val isSelectionMode: Boolean = false,
    val storageBreakdown: StorageBreakdown? = null,
    val clipboardSourcePaths: List<String> = emptyList(),
    val isClipboardCut: Boolean = false,
    val isTrashViewOpen: Boolean = false,
    val trashFiles: List<FileItem> = emptyList(),
    val isLoading: Boolean = false,
    val dialogState: ExplorerDialogState = ExplorerDialogState.None,
    val userMessage: String? = null,
    val needsStoragePermission: Boolean = false,
    val permissionMessage: String? = null
)
