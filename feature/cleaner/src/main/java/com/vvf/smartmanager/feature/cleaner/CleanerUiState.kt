package com.vvf.smartmanager.feature.cleaner

import com.vvf.smartmanager.core.model.CleanerScanResult
import com.vvf.smartmanager.core.model.DuplicateAutoSelectStrategy
import com.vvf.smartmanager.core.model.DuplicateFileGroup
import com.vvf.smartmanager.core.model.DuplicateLevel
import com.vvf.smartmanager.core.model.FileItem

enum class CleanerTab {
    DUPLICATES,
    JUNK_FILES
}

data class CleanerUiState(
    val activeTab: CleanerTab = CleanerTab.DUPLICATES,
    val duplicateLevel: DuplicateLevel = DuplicateLevel.LEVEL_1_SIZE,
    val similarityThreshold: Float = 0.80f, // Configurable threshold 0.70f to 0.95f (70% - 95%)
    val isScanning: Boolean = false,
    val scanProgressText: String = "",
    val scanProgressPct: Float = 0f,
    val scanResult: CleanerScanResult? = null,
    val duplicateGroups: List<DuplicateFileGroup> = emptyList(),
    // Selected paths across duplicates and junk items
    val selectedDuplicatePaths: Set<String> = emptySet(),
    val selectedEmptyFolderPaths: Set<String> = emptySet(),
    val selectedTempFilePaths: Set<String> = emptySet(),
    val selectedLargeFilePaths: Set<String> = emptySet(),
    val selectedApkPaths: Set<String> = emptySet(),
    val isCleaning: Boolean = false,
    val lastCleanReclaimedBytes: Long? = null,
    val userMessage: String? = null,
    val showCleanConfirmDialog: Boolean = false
) {
    val totalSelectedBytes: Long
        get() {
            var sum = 0L
            // From duplicates
            duplicateGroups.forEach { group ->
                group.files.forEach { file ->
                    if (selectedDuplicatePaths.contains(file.path)) {
                        sum += file.sizeBytes
                    }
                }
            }
            // From junk
            scanResult?.let { res ->
                res.tempFiles.filter { selectedTempFilePaths.contains(it.path) }.forEach { sum += it.sizeBytes }
                res.largeFiles.filter { selectedLargeFilePaths.contains(it.path) }.forEach { sum += it.sizeBytes }
                res.apkFiles.filter { selectedApkPaths.contains(it.path) }.forEach { sum += it.sizeBytes }
            }
            return sum
        }

    val totalSelectedItemsCount: Int
        get() = selectedDuplicatePaths.size +
                selectedEmptyFolderPaths.size +
                selectedTempFilePaths.size +
                selectedLargeFilePaths.size +
                selectedApkPaths.size
}
