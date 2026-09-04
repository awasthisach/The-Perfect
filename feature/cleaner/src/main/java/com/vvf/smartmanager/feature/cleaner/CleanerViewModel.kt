package com.vvf.smartmanager.feature.cleaner

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vvf.smartmanager.core.domain.AiIntelligenceUseCase
import com.vvf.smartmanager.core.domain.DuplicateCleanerUseCase
import com.vvf.smartmanager.core.domain.JunkCleanerUseCase
import com.vvf.smartmanager.core.model.DuplicateAutoSelectStrategy
import com.vvf.smartmanager.core.model.DuplicateFileGroup
import com.vvf.smartmanager.core.model.DuplicateLevel
import com.vvf.smartmanager.core.model.FileItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class CleanerViewModel(
    private val duplicateCleanerUseCase: DuplicateCleanerUseCase,
    private val junkCleanerUseCase: JunkCleanerUseCase,
    private val aiIntelligenceUseCase: AiIntelligenceUseCase? = null,
    private val canScanPrimaryStorage: () -> Boolean = { true }
) : ViewModel() {

    private val _uiState = MutableStateFlow(CleanerUiState())
    val uiState: StateFlow<CleanerUiState> = _uiState.asStateFlow()

    init {
        startScan(_uiState.value.duplicateLevel)
    }

    fun setActiveTab(tab: CleanerTab) {
        _uiState.update { it.copy(activeTab = tab) }
    }

    fun setDuplicateLevel(level: DuplicateLevel) {
        if (_uiState.value.duplicateLevel != level) {
            _uiState.update { it.copy(duplicateLevel = level) }
            startScan(level)
        }
    }

    fun setSimilarityThreshold(threshold: Float) {
        val clamped = threshold.coerceIn(0.70f, 0.95f)
        _uiState.update { it.copy(similarityThreshold = clamped) }
        if (_uiState.value.duplicateLevel == DuplicateLevel.LEVEL_3_SIMILARITY) {
            startScan(DuplicateLevel.LEVEL_3_SIMILARITY)
        }
    }

    fun startScan(level: DuplicateLevel = _uiState.value.duplicateLevel) {
        if (!canScanPrimaryStorage()) {
            _uiState.update {
                it.copy(
                    isScanning = false,
                    duplicateGroups = emptyList(),
                    scanResult = com.vvf.smartmanager.core.model.CleanerScanResult(),
                    scanProgressPct = 0f,
                    scanProgressText = "Storage access required",
                    userMessage = "Grant All Files Access in Explorer before running a Cleaner scan."
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isScanning = true,
                    scanProgressText = if (level == DuplicateLevel.LEVEL_3_SIMILARITY)
                        "Analyzing semantic embeddings for near-duplicates (${(it.similarityThreshold * 100).toInt()}% threshold)..."
                    else "Scanning storage for duplicates & junk...",
                    scanProgressPct = 0.1f,
                    duplicateLevel = level
                )
            }

            // 1. Scan Duplicates based on Level
            if (level == DuplicateLevel.LEVEL_3_SIMILARITY && aiIntelligenceUseCase != null) {
                aiIntelligenceUseCase.scanNearDuplicates(_uiState.value.similarityThreshold).collectLatest { groups ->
                    _uiState.update { state ->
                        state.copy(
                            duplicateGroups = groups,
                            scanProgressPct = 0.6f
                        )
                    }
                    applyDuplicateAutoSelect(DuplicateAutoSelectStrategy.KEEP_OLDEST, groups)
                }
            } else {
                duplicateCleanerUseCase.scanDuplicates(level).collectLatest { groups ->
                    _uiState.update { state ->
                        state.copy(
                            duplicateGroups = groups,
                            scanProgressPct = 0.6f
                        )
                    }
                    applyDuplicateAutoSelect(DuplicateAutoSelectStrategy.KEEP_OLDEST, groups)
                }
            }

            // 2. Scan Junk Items
            junkCleanerUseCase.scanJunkFiles().collectLatest { junkResult ->
                _uiState.update { state ->
                    state.copy(
                        scanResult = junkResult,
                        // Pre-select empty folders and temporary files
                        selectedEmptyFolderPaths = junkResult.emptyFolders.map { it.path }.toSet(),
                        selectedTempFilePaths = junkResult.tempFiles.map { it.path }.toSet(),
                        isScanning = false,
                        scanProgressPct = 1.0f
                    )
                }
            }
        }
    }

    fun toggleDuplicateSelection(path: String) {
        _uiState.update { state ->
            val updated = state.selectedDuplicatePaths.toMutableSet()
            if (updated.contains(path)) {
                updated.remove(path)
            } else {
                updated.add(path)
            }
            state.copy(selectedDuplicatePaths = updated)
        }
    }

    fun applyDuplicateAutoSelect(strategy: DuplicateAutoSelectStrategy, groups: List<DuplicateFileGroup> = _uiState.value.duplicateGroups) {
        val selected = mutableSetOf<String>()
        groups.forEach { group ->
            val files = group.files
            if (files.size > 1) {
                when (strategy) {
                    DuplicateAutoSelectStrategy.KEEP_OLDEST -> {
                        val sorted = files.sortedBy { it.lastModified }
                        // Keep first (oldest), select all others for deletion
                        sorted.drop(1).forEach { selected.add(it.path) }
                    }
                    DuplicateAutoSelectStrategy.KEEP_NEWEST -> {
                        val sorted = files.sortedByDescending { it.lastModified }
                        // Keep first (newest), select all others for deletion
                        sorted.drop(1).forEach { selected.add(it.path) }
                    }
                    DuplicateAutoSelectStrategy.SELECT_ALL -> {
                        files.forEach { selected.add(it.path) }
                    }
                    DuplicateAutoSelectStrategy.DESELECT_ALL -> {
                        // Keep all unselected
                    }
                }
            }
        }

        _uiState.update { it.copy(selectedDuplicatePaths = selected) }
    }

    fun toggleEmptyFolderSelection(path: String) {
        _uiState.update { state ->
            val updated = state.selectedEmptyFolderPaths.toMutableSet()
            if (updated.contains(path)) updated.remove(path) else updated.add(path)
            state.copy(selectedEmptyFolderPaths = updated)
        }
    }

    fun toggleTempFileSelection(path: String) {
        _uiState.update { state ->
            val updated = state.selectedTempFilePaths.toMutableSet()
            if (updated.contains(path)) updated.remove(path) else updated.add(path)
            state.copy(selectedTempFilePaths = updated)
        }
    }

    fun toggleLargeFileSelection(path: String) {
        _uiState.update { state ->
            val updated = state.selectedLargeFilePaths.toMutableSet()
            if (updated.contains(path)) updated.remove(path) else updated.add(path)
            state.copy(selectedLargeFilePaths = updated)
        }
    }

    fun toggleApkSelection(path: String) {
        _uiState.update { state ->
            val updated = state.selectedApkPaths.toMutableSet()
            if (updated.contains(path)) updated.remove(path) else updated.add(path)
            state.copy(selectedApkPaths = updated)
        }
    }

    fun showCleanConfirmation() {
        _uiState.update { it.copy(showCleanConfirmDialog = true) }
    }

    fun dismissCleanConfirmation() {
        _uiState.update { it.copy(showCleanConfirmDialog = false) }
    }

    fun executeClean() {
        val state = _uiState.value
        val allSelectedPaths = (
                state.selectedDuplicatePaths +
                state.selectedEmptyFolderPaths +
                state.selectedTempFilePaths +
                state.selectedLargeFilePaths +
                state.selectedApkPaths
        ).toList()

        if (allSelectedPaths.isEmpty()) {
            dismissCleanConfirmation()
            return
        }

        viewModelScope.launch(Dispatchers.IO) {
            _uiState.update { it.copy(isCleaning = true, showCleanConfirmDialog = false) }

            val result = junkCleanerUseCase.cleanFiles(allSelectedPaths)

            result.onSuccess { bytesReclaimed ->
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isCleaning = false,
                            lastCleanReclaimedBytes = bytesReclaimed,
                            userMessage = "Cleaned ${allSelectedPaths.size} items! Reclaimed space.",
                            selectedDuplicatePaths = emptySet(),
                            selectedEmptyFolderPaths = emptySet(),
                            selectedTempFilePaths = emptySet(),
                            selectedLargeFilePaths = emptySet(),
                            selectedApkPaths = emptySet()
                        )
                    }
                    // Refresh scans
                    startScan(state.duplicateLevel)
                }
            }.onFailure { error ->
                withContext(Dispatchers.Main) {
                    _uiState.update {
                        it.copy(
                            isCleaning = false,
                            userMessage = "Cleaning failed: ${error.localizedMessage}"
                        )
                    }
                }
            }
        }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    companion object {
        fun provideFactory(
            duplicateCleanerUseCase: DuplicateCleanerUseCase,
            junkCleanerUseCase: JunkCleanerUseCase,
            aiIntelligenceUseCase: AiIntelligenceUseCase? = null,
            canScanPrimaryStorage: () -> Boolean = { true }
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return CleanerViewModel(
                    duplicateCleanerUseCase,
                    junkCleanerUseCase,
                    aiIntelligenceUseCase,
                    canScanPrimaryStorage
                ) as T
            }
        }
    }
}
