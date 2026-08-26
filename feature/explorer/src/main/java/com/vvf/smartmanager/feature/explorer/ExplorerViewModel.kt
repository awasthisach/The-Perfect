package com.vvf.smartmanager.feature.explorer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vvf.smartmanager.core.domain.FileOperationsUseCase
import com.vvf.smartmanager.core.domain.GetCategorizedFilesUseCase
import com.vvf.smartmanager.core.domain.GetDirectoryFilesUseCase
import com.vvf.smartmanager.core.domain.GetStorageOverviewUseCase
import com.vvf.smartmanager.core.domain.RecycleBinUseCase
import com.vvf.smartmanager.core.model.FileCategory
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.FileSortOption
import com.vvf.smartmanager.core.model.FileViewMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class ExplorerViewModel(
    private val getDirectoryFilesUseCase: GetDirectoryFilesUseCase,
    private val getCategorizedFilesUseCase: GetCategorizedFilesUseCase,
    private val getStorageOverviewUseCase: GetStorageOverviewUseCase,
    private val fileOperationsUseCase: FileOperationsUseCase,
    private val recycleBinUseCase: RecycleBinUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExplorerUiState())
    val uiState: StateFlow<ExplorerUiState> = _uiState.asStateFlow()

    init {
        val rootPath = getDirectoryFilesUseCase.getDefaultStoragePath()
        _uiState.update { it.copy(currentPath = rootPath) }
        loadStorageOverview()
        loadDirectory(rootPath)
    }

    fun loadStorageOverview() {
        viewModelScope.launch {
            getStorageOverviewUseCase().collectLatest { breakdown ->
                _uiState.update { it.copy(storageBreakdown = breakdown) }
            }
        }
    }

    fun loadDirectory(path: String) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    currentPath = path,
                    breadcrumbs = buildBreadcrumbs(path),
                    selectedCategory = FileCategory.ALL,
                    selectedPaths = emptySet(),
                    isSelectionMode = false,
                    searchQuery = "",
                    isSearchActive = false
                )
            }

            getDirectoryFilesUseCase(
                directoryPath = path,
                sortOption = _uiState.value.sortOption,
                showHidden = _uiState.value.showHidden
            ).collectLatest { fileList ->
                _uiState.update {
                    it.copy(
                        files = fileList,
                        filteredFiles = applyFilterAndSearch(fileList, it.searchQuery),
                        isLoading = false
                    )
                }
            }
        }
    }

    fun selectCategory(category: FileCategory) {
        if (category == FileCategory.ALL) {
            loadDirectory(_uiState.value.currentPath.ifEmpty { getDirectoryFilesUseCase.getDefaultStoragePath() })
            return
        }

        if (category == FileCategory.TRASH) {
            openTrashView()
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isLoading = true,
                    selectedCategory = category,
                    selectedPaths = emptySet(),
                    isSelectionMode = false,
                    searchQuery = "",
                    isSearchActive = false
                )
            }

            getCategorizedFilesUseCase(
                category = category,
                sortOption = _uiState.value.sortOption
            ).collectLatest { fileList ->
                _uiState.update {
                    it.copy(
                        files = fileList,
                        filteredFiles = applyFilterAndSearch(fileList, it.searchQuery),
                        isLoading = false
                    )
                }
            }
        }
    }

    fun navigateInto(item: FileItem) {
        if (item.isDirectory) {
            loadDirectory(item.path)
        } else {
            showDialog(ExplorerDialogState.FileDetails(item))
        }
    }

    fun navigateUp(): Boolean {
        if (_uiState.value.selectedCategory != FileCategory.ALL) {
            selectCategory(FileCategory.ALL)
            return true
        }

        val current = File(_uiState.value.currentPath)
        val parent = current.parentFile
        val rootPath = getDirectoryFilesUseCase.getDefaultStoragePath()

        return if (parent != null && current.absolutePath != rootPath && parent.canRead()) {
            loadDirectory(parent.absolutePath)
            true
        } else {
            false
        }
    }

    fun setSortOption(sortOption: FileSortOption) {
        _uiState.update { it.copy(sortOption = sortOption) }
        if (_uiState.value.selectedCategory == FileCategory.ALL) {
            loadDirectory(_uiState.value.currentPath)
        } else {
            selectCategory(_uiState.value.selectedCategory)
        }
    }

    fun toggleViewMode() {
        _uiState.update {
            it.copy(viewMode = if (it.viewMode == FileViewMode.LIST) FileViewMode.GRID else FileViewMode.LIST)
        }
    }

    fun toggleShowHidden() {
        val newShow = !_uiState.value.showHidden
        _uiState.update { it.copy(showHidden = newShow) }
        loadDirectory(_uiState.value.currentPath)
    }

    fun setSearchQuery(query: String) {
        _uiState.update {
            it.copy(
                searchQuery = query,
                isSearchActive = query.isNotEmpty(),
                filteredFiles = applyFilterAndSearch(it.files, query)
            )
        }
    }

    fun clearSearch() {
        _uiState.update {
            it.copy(
                searchQuery = "",
                isSearchActive = false,
                filteredFiles = it.files
            )
        }
    }

    // -------------------------------------------------------------
    // SELECTION MANAGEMENT
    // -------------------------------------------------------------

    fun toggleItemSelection(path: String) {
        _uiState.update { state ->
            val updated = state.selectedPaths.toMutableSet()
            if (updated.contains(path)) {
                updated.remove(path)
            } else {
                updated.add(path)
            }
            state.copy(
                selectedPaths = updated,
                isSelectionMode = updated.isNotEmpty()
            )
        }
    }

    fun selectAll() {
        _uiState.update { state ->
            val allPaths = state.filteredFiles.map { it.path }.toSet()
            state.copy(
                selectedPaths = allPaths,
                isSelectionMode = allPaths.isNotEmpty()
            )
        }
    }

    fun clearSelection() {
        _uiState.update {
            it.copy(
                selectedPaths = emptySet(),
                isSelectionMode = false
            )
        }
    }

    // -------------------------------------------------------------
    // CLIPBOARD & FILE ACTIONS
    // -------------------------------------------------------------

    fun copySelectedToClipboard() {
        val selected = _uiState.value.selectedPaths.toList()
        if (selected.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    clipboardSourcePaths = selected,
                    isClipboardCut = false,
                    selectedPaths = emptySet(),
                    isSelectionMode = false,
                    userMessage = "${selected.size} items copied to clipboard"
                )
            }
        }
    }

    fun cutSelectedToClipboard() {
        val selected = _uiState.value.selectedPaths.toList()
        if (selected.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    clipboardSourcePaths = selected,
                    isClipboardCut = true,
                    selectedPaths = emptySet(),
                    isSelectionMode = false,
                    userMessage = "${selected.size} items cut to clipboard"
                )
            }
        }
    }

    fun pasteClipboard() {
        val sources = _uiState.value.clipboardSourcePaths
        val destDir = _uiState.value.currentPath
        val isCut = _uiState.value.isClipboardCut

        if (sources.isEmpty() || destDir.isEmpty()) return

        viewModelScope.launch(Dispatchers.IO) {
            val result = if (isCut) {
                fileOperationsUseCase.moveFiles(sources, destDir) { progress ->
                    _uiState.update { it.copy(dialogState = ExplorerDialogState.Progress(progress)) }
                }
            } else {
                fileOperationsUseCase.copyFiles(sources, destDir) { progress ->
                    _uiState.update { it.copy(dialogState = ExplorerDialogState.Progress(progress)) }
                }
            }

            result.onSuccess { count ->
                _uiState.update {
                    it.copy(
                        clipboardSourcePaths = emptyList(),
                        isClipboardCut = false,
                        dialogState = ExplorerDialogState.None,
                        userMessage = "Successfully transferred $count item(s)"
                    )
                }
                loadDirectory(destDir)
                loadStorageOverview()
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        dialogState = ExplorerDialogState.None,
                        userMessage = "Transfer failed: ${error.localizedMessage}"
                    )
                }
            }
        }
    }

    fun createFolder(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val result = fileOperationsUseCase.createDirectory(_uiState.value.currentPath, name.trim())
            result.onSuccess {
                dismissDialog()
                loadDirectory(_uiState.value.currentPath)
                _uiState.update { state -> state.copy(userMessage = "Folder '$name' created") }
            }.onFailure { error ->
                _uiState.update { state -> state.copy(userMessage = "Failed to create folder: ${error.localizedMessage}") }
            }
        }
    }

    fun createFile(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            val result = fileOperationsUseCase.createFile(_uiState.value.currentPath, name.trim())
            result.onSuccess {
                dismissDialog()
                loadDirectory(_uiState.value.currentPath)
                _uiState.update { state -> state.copy(userMessage = "File '$name' created") }
            }.onFailure { error ->
                _uiState.update { state -> state.copy(userMessage = "Failed to create file: ${error.localizedMessage}") }
            }
        }
    }

    fun renameFile(item: FileItem, newName: String) {
        if (newName.isBlank() || newName == item.name) {
            dismissDialog()
            return
        }
        viewModelScope.launch {
            val result = fileOperationsUseCase.renameFile(item.path, newName.trim())
            result.onSuccess {
                dismissDialog()
                loadDirectory(_uiState.value.currentPath)
                _uiState.update { state -> state.copy(userMessage = "Renamed to '$newName'") }
            }.onFailure { error ->
                _uiState.update { state -> state.copy(userMessage = "Rename failed: ${error.localizedMessage}") }
            }
        }
    }

    fun deleteItems(items: List<FileItem>, permanent: Boolean) {
        val paths = items.map { it.path }
        if (paths.isEmpty()) return

        viewModelScope.launch {
            val result = fileOperationsUseCase.deleteFiles(paths, permanent)
            result.onSuccess { count ->
                dismissDialog()
                clearSelection()
                loadDirectory(_uiState.value.currentPath)
                loadStorageOverview()
                val msg = if (permanent) "Permanently deleted $count item(s)" else "Moved $count item(s) to Recycle Bin"
                _uiState.update { it.copy(userMessage = msg) }
            }.onFailure { error ->
                _uiState.update { it.copy(userMessage = "Delete failed: ${error.localizedMessage}") }
            }
        }
    }

    fun toggleFavorite(item: FileItem) {
        viewModelScope.launch {
            fileOperationsUseCase.toggleFavorite(item.path, !item.isFavorite)
            _uiState.update { state ->
                val updated = state.files.map {
                    if (it.path == item.path) it.copy(isFavorite = !item.isFavorite) else it
                }
                state.copy(
                    files = updated,
                    filteredFiles = applyFilterAndSearch(updated, state.searchQuery)
                )
            }
        }
    }

    // -------------------------------------------------------------
    // RECYCLE BIN ACTIONS
    // -------------------------------------------------------------

    fun openTrashView() {
        viewModelScope.launch {
            _uiState.update { it.copy(isTrashViewOpen = true, isLoading = true) }
            recycleBinUseCase.getTrashFiles().collectLatest { trashItems ->
                _uiState.update {
                    it.copy(
                        trashFiles = trashItems,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun closeTrashView() {
        _uiState.update { it.copy(isTrashViewOpen = false) }
    }

    fun restoreTrashItems(paths: List<String>) {
        viewModelScope.launch {
            val result = recycleBinUseCase.restoreFromTrash(paths)
            result.onSuccess { count ->
                _uiState.update { it.copy(userMessage = "Restored $count item(s)") }
                openTrashView()
                loadDirectory(_uiState.value.currentPath)
                loadStorageOverview()
            }.onFailure { error ->
                _uiState.update { it.copy(userMessage = "Restore failed: ${error.localizedMessage}") }
            }
        }
    }

    fun emptyTrash() {
        viewModelScope.launch {
            val result = recycleBinUseCase.emptyTrash()
            result.onSuccess {
                _uiState.update { it.copy(userMessage = "Recycle bin emptied", trashFiles = emptyList()) }
                loadStorageOverview()
            }.onFailure { error ->
                _uiState.update { it.copy(userMessage = "Failed to empty trash: ${error.localizedMessage}") }
            }
        }
    }

    // -------------------------------------------------------------
    // DIALOG CONTROLS
    // -------------------------------------------------------------

    fun showDialog(dialog: ExplorerDialogState) {
        _uiState.update { it.copy(dialogState = dialog) }
    }

    fun dismissDialog() {
        _uiState.update { it.copy(dialogState = ExplorerDialogState.None) }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    private fun applyFilterAndSearch(files: List<FileItem>, query: String): List<FileItem> {
        if (query.isBlank()) return files
        return files.filter { it.name.contains(query, ignoreCase = true) }
    }

    private fun buildBreadcrumbs(fullPath: String): List<Pair<String, String>> {
        val rootPath = getDirectoryFilesUseCase.getDefaultStoragePath()
        val crumbs = mutableListOf<Pair<String, String>>()
        crumbs.add(Pair("Internal Storage", rootPath))

        if (fullPath == rootPath || !fullPath.startsWith(rootPath)) {
            return crumbs
        }

        val relative = fullPath.removePrefix(rootPath).trim('/')
        if (relative.isEmpty()) return crumbs

        val segments = relative.split('/')
        var cumulative = rootPath
        for (seg in segments) {
            cumulative = "$cumulative/$seg"
            crumbs.add(Pair(seg, cumulative))
        }
        return crumbs
    }

    companion object {
        fun provideFactory(
            getDirectoryFilesUseCase: GetDirectoryFilesUseCase,
            getCategorizedFilesUseCase: GetCategorizedFilesUseCase,
            getStorageOverviewUseCase: GetStorageOverviewUseCase,
            fileOperationsUseCase: FileOperationsUseCase,
            recycleBinUseCase: RecycleBinUseCase
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ExplorerViewModel(
                    getDirectoryFilesUseCase,
                    getCategorizedFilesUseCase,
                    getStorageOverviewUseCase,
                    fileOperationsUseCase,
                    recycleBinUseCase
                ) as T
            }
        }
    }
}
