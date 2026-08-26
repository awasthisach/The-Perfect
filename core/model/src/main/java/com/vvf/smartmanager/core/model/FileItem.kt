package com.vvf.smartmanager.core.model

import java.io.File

/**
 * Core model representing a file or directory in VVF Smart Manager.
 */
data class FileItem(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val lastModified: Long,
    val isDirectory: Boolean,
    val mimeType: String? = null,
    val isEncrypted: Boolean = false,
    val isFavorite: Boolean = false,
    val isTrash: Boolean = false,
    val originalPath: String? = null,
    val deletedTimestamp: Long? = null,
    val md5Hash: String? = null,
    val itemCount: Int = 0,
    val tags: List<String> = emptyList()
) {
    val extension: String
        get() = name.substringAfterLast('.', "").lowercase()

    val isHidden: Boolean
        get() = name.startsWith('.')

    val file: File
        get() = File(path)
}

enum class FileCategory {
    ALL,
    IMAGES,
    VIDEOS,
    AUDIO,
    DOCUMENTS,
    ARCHIVES,
    APKS,
    VAULT,
    FAVORITES,
    TRASH
}

enum class FileSortOption(val displayName: String) {
    NAME_ASC("Name (A-Z)"),
    NAME_DESC("Name (Z-A)"),
    DATE_DESC("Date (Newest first)"),
    DATE_ASC("Date (Oldest first)"),
    SIZE_DESC("Size (Largest first)"),
    SIZE_ASC("Size (Smallest first)"),
    TYPE_ASC("File Type")
}

enum class FileViewMode {
    LIST,
    GRID
}

enum class DuplicateLevel(val displayName: String) {
    LEVEL_1_SIZE("Level 1: Fast Size Match"),
    LEVEL_2_HASH("Level 2: Deep SHA-256"),
    LEVEL_3_SIMILARITY("Level 3: AI Similarity (70%-95%)")
}

enum class DuplicateAutoSelectStrategy {
    KEEP_OLDEST,
    KEEP_NEWEST,
    SELECT_ALL,
    DESELECT_ALL
}

/**
 * Represents a set of duplicate files sharing the same size or cryptographic hash.
 */
data class DuplicateFileGroup(
    val id: String,
    val matchKey: String, // size in bytes or MD5/SHA-256 hash string
    val level: DuplicateLevel,
    val sizePerFile: Long,
    val files: List<FileItem>,
    val selectedPaths: Set<String> = emptySet()
) {
    val fileCount: Int
        get() = files.size

    val fileSizeBytes: Long
        get() = sizePerFile

    val duplicateCount: Int
        get() = (files.size - 1).coerceAtLeast(0)

    val wastedBytes: Long
        get() = duplicateCount.toLong() * sizePerFile

    val recoverableBytes: Long
        get() = selectedPaths.size * sizePerFile
}

/**
 * Represents categorized junk item found during device scan.
 */
data class JunkItem(
    val path: String,
    val name: String,
    val sizeBytes: Long,
    val category: JunkCategory,
    val details: String = "",
    val isSelected: Boolean = true
) {
    val isDirectory: Boolean
        get() = category == JunkCategory.EMPTY_FOLDERS
}

enum class JunkCategory(val displayName: String) {
    DUPLICATE_FILES("Duplicate Files"),
    EMPTY_FOLDERS("Empty Folders"),
    LARGE_FILES("Large Files (>100MB)"),
    TEMP_CACHE_FILES("Temporary & Cache"),
    APK_FILES("APK Packages")
}

data class CleanerScanResult(
    val duplicateGroups: List<DuplicateFileGroup> = emptyList(),
    val emptyFolders: List<JunkItem> = emptyList(),
    val largeFiles: List<JunkItem> = emptyList(),
    val tempFiles: List<JunkItem> = emptyList(),
    val apkFiles: List<JunkItem> = emptyList(),
    val isScanning: Boolean = false,
    val scanProgress: Float = 0f,
    val currentScannedPath: String = "",
    val totalScannedCount: Int = 0
) {
    val wastedBytes: Long
        get() {
            val emptyBytes = emptyFolders.sumOf { it.sizeBytes }
            val largeBytes = largeFiles.sumOf { it.sizeBytes }
            val tempBytes = tempFiles.sumOf { it.sizeBytes }
            val apkBytes = apkFiles.sumOf { it.sizeBytes }
            return emptyBytes + largeBytes + tempBytes + apkBytes
        }

    val totalRecoverableBytes: Long
        get() {
            val dupBytes = duplicateGroups.sumOf { it.recoverableBytes }
            val emptyBytes = emptyFolders.filter { it.isSelected }.sumOf { it.sizeBytes }
            val largeBytes = largeFiles.filter { it.isSelected }.sumOf { it.sizeBytes }
            val tempBytes = tempFiles.filter { it.isSelected }.sumOf { it.sizeBytes }
            val apkBytes = apkFiles.filter { it.isSelected }.sumOf { it.sizeBytes }
            return dupBytes + emptyBytes + largeBytes + tempBytes + apkBytes
        }

    val totalSelectedItemsCount: Int
        get() {
            val dupCount = duplicateGroups.sumOf { it.selectedPaths.size }
            val emptyCount = emptyFolders.count { it.isSelected }
            val largeCount = largeFiles.count { it.isSelected }
            val tempCount = tempFiles.count { it.isSelected }
            val apkCount = apkFiles.count { it.isSelected }
            return dupCount + emptyCount + largeCount + tempCount + apkCount
        }
}

enum class FileOperationType {
    COPY,
    MOVE,
    RENAME,
    CREATE_FOLDER,
    DELETE_PERMANENT,
    SOFT_DELETE,
    RESTORE
}

data class FileOperationProgress(
    val operation: FileOperationType,
    val currentFileName: String = "",
    val processedCount: Int = 0,
    val totalCount: Int = 0,
    val progressPercentage: Float = 0f,
    val isCompleted: Boolean = false,
    val errorMessage: String? = null
)
