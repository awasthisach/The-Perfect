package com.vvf.smartmanager.core.data.storage

import android.content.Context
import com.vvf.smartmanager.core.database.dao.FileDao
import com.vvf.smartmanager.core.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.File

/**
 * Duplicate and junk scanners on top of file operations.
 */
open class StorageManagerOps(
    context: Context,
    fileDao: FileDao
) : StorageManagerFileOps(context, fileDao) {

    fun scanDuplicatesFlow(level: DuplicateLevel): Flow<List<DuplicateFileGroup>> = flow {
        val rootDir = File(getPrimaryStoragePath())
        val allFiles = mutableListOf<File>()
        collectFiles(rootDir, allFiles, maxFiles = 3000)
        val candidateFiles = allFiles.filter { it.isFile && it.length() > 0 && !it.absolutePath.contains(".vvf_trash") }
        val sizeGroups = candidateFiles.groupBy { it.length() }.filter { it.value.size > 1 }

        if (level == DuplicateLevel.LEVEL_1_SIZE) {
            val groups = sizeGroups.map { (size, files) ->
                val fileItems = files.map { file ->
                    FileItem(
                        path = file.absolutePath,
                        name = file.name,
                        sizeBytes = file.length(),
                        lastModified = file.lastModified(),
                        isDirectory = false,
                        mimeType = getMimeType(file.name)
                    )
                }
                val sorted = fileItems.sortedBy { it.lastModified }
                val defaultSelected = sorted.drop(1).map { it.path }.toSet()
                DuplicateFileGroup(
                    id = "size_$size",
                    matchKey = "$size bytes",
                    level = DuplicateLevel.LEVEL_1_SIZE,
                    sizePerFile = size,
                    files = sorted,
                    selectedPaths = defaultSelected
                )
            }.sortedByDescending { it.sizePerFile * it.duplicateCount }
            emit(groups)
            return@flow
        }

        val hashGroups = mutableListOf<DuplicateFileGroup>()
        for ((size, files) in sizeGroups) {
            val partialHashBuckets = files.groupBy { file -> calculatePartialHash(file) }.filter { it.value.size > 1 }
            for ((_, partialCandidates) in partialHashBuckets) {
                val fullHashBuckets = partialCandidates.groupBy { file -> calculateFullSha256(file) }.filter { it.value.size > 1 }
                for ((fullHash, identicalFiles) in fullHashBuckets) {
                    val fileItems = identicalFiles.map { file ->
                        FileItem(
                            path = file.absolutePath,
                            name = file.name,
                            sizeBytes = file.length(),
                            lastModified = file.lastModified(),
                            isDirectory = false,
                            mimeType = getMimeType(file.name),
                            md5Hash = fullHash,
                            sha256Hash = fullHash
                        )
                    }
                    val sorted = fileItems.sortedBy { it.lastModified }
                    val defaultSelected = sorted.drop(1).map { it.path }.toSet()
                    hashGroups.add(
                        DuplicateFileGroup(
                            id = "hash_${fullHash.take(12)}",
                            matchKey = "SHA-256: ${fullHash.take(12)}...",
                            level = DuplicateLevel.LEVEL_2_HASH,
                            sizePerFile = size,
                            files = sorted,
                            selectedPaths = defaultSelected
                        )
                    )
                }
            }
        }
        emit(hashGroups.sortedByDescending { it.sizePerFile * it.duplicateCount })
    }.flowOn(Dispatchers.IO)

    fun scanJunkFlow(): Flow<CleanerScanResult> = flow {
        val rootDir = File(getPrimaryStoragePath())
        val allFiles = mutableListOf<File>()
        val emptyFolders = mutableListOf<JunkItem>()
        val largeFiles = mutableListOf<JunkItem>()
        val tempFiles = mutableListOf<JunkItem>()
        val apkFiles = mutableListOf<JunkItem>()

        emit(CleanerScanResult(isScanning = true, scanProgress = 0.1f))
        collectFilesAndDirectories(rootDir, allFiles, emptyFolders, maxItems = 4000)
        emit(CleanerScanResult(isScanning = true, scanProgress = 0.5f, emptyFolders = emptyFolders))

        val tempExtensions = setOf("tmp", "temp", "log", "cache", "bak", "old")
        val largeThresholdBytes = 100L * 1024 * 1024

        for (file in allFiles) {
            if (!file.isFile || file.absolutePath.contains(".vvf_trash")) continue
            val ext = file.extension.lowercase()
            val size = file.length()
            if (tempExtensions.contains(ext) || file.name.startsWith("~") ||
                file.name.equals(".DS_Store", ignoreCase = true) ||
                file.name.equals("Thumbs.db", ignoreCase = true)
            ) {
                tempFiles.add(
                    JunkItem(
                        path = file.absolutePath,
                        name = file.name,
                        sizeBytes = size,
                        category = JunkCategory.TEMP_CACHE_FILES,
                        details = "Temporary cache file"
                    )
                )
            } else if (ext == "apk") {
                apkFiles.add(
                    JunkItem(
                        path = file.absolutePath,
                        name = file.name,
                        sizeBytes = size,
                        category = JunkCategory.APK_FILES,
                        details = "Standalone Android package installer"
                    )
                )
            } else if (size >= largeThresholdBytes) {
                largeFiles.add(
                    JunkItem(
                        path = file.absolutePath,
                        name = file.name,
                        sizeBytes = size,
                        category = JunkCategory.LARGE_FILES,
                        details = "File size exceeds 100 MB"
                    )
                )
            }
        }

        emit(
            CleanerScanResult(
                emptyFolders = emptyFolders,
                largeFiles = largeFiles,
                tempFiles = tempFiles,
                apkFiles = apkFiles,
                isScanning = false,
                scanProgress = 1.0f,
                totalScannedCount = allFiles.size + emptyFolders.size
            )
        )
    }.flowOn(Dispatchers.IO)
}
