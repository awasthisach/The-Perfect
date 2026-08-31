package com.vvf.smartmanager.core.domain.backup

import com.vvf.smartmanager.core.data.backup.SnapshotSource
import com.vvf.smartmanager.core.model.CloudBackupInfo
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Creates encrypted, upload-ready backup artifacts without mutating live data sources. */
class ArchiveService(
    private val cacheDir: File,
    private val snapshotSources: List<SnapshotSource>,
    private val cryptoSecurityManager: CryptoSecurityManager,
    private val appVersion: String = "unknown",
    private val schemaVersion: Int = 1
) {
    private val json = Json { prettyPrint = true; encodeDefaults = true }

    suspend fun createArchive(
        includeVault: Boolean = true,
        includeDatabase: Boolean = true
    ): Result<ArchiveArtifact> = withContext(Dispatchers.IO) {
        val timestamp = System.currentTimeMillis()
        val stagingDir = File(cacheDir, "archive-staging-$timestamp")
        val zipFile = File(cacheDir, "backup-$timestamp.zip")
        val encryptedFile = File(cacheDir, "backup-$timestamp.vvfbak")
        try {
            if (!cacheDir.exists() && !cacheDir.mkdirs()) {
                return@withContext Result.failure(ArchiveException("Unable to create archive cache directory"))
            }
            if (!stagingDir.mkdirs()) {
                return@withContext Result.failure(ArchiveException("Unable to create archive staging directory"))
            }

            val requestedSources = snapshotSources.filter { source ->
                (source.sourceName == "vault" && includeVault) ||
                    (source.sourceName == "database" && includeDatabase) ||
                    (source.sourceName != "vault" && source.sourceName != "database")
            }
            val snapshots = requestedSources.associate { source ->
                source.sourceName to source.snapshot(stagingDir)
            }
            val failed = snapshots.filterValues { it == null }.keys
            if (failed.isNotEmpty()) {
                return@withContext Result.failure(
                    ArchiveException("Snapshot failed for: ${failed.joinToString()}")
                )
            }

            val metadata = ArchiveMetadata(
                version = 1,
                timestamp = timestamp,
                checksumSha256 = "",
                databaseSizeBytes = requestedSources.firstOrNull { it.sourceName == "database" }?.dataSizeBytes() ?: 0L,
                vaultSizeBytes = requestedSources.firstOrNull { it.sourceName == "vault" }?.dataSizeBytes() ?: 0L,
                fileCount = stagingDir.walkTopDown().count { it.isFile },
                vaultItemCount = File(stagingDir, "vault").walkTopDown().count { it.isFile },
                appVersion = appVersion,
                schemaVersion = schemaVersion
            )
            File(stagingDir, "metadata.json").writeText(json.encodeToString(metadata))
            zipDirectory(stagingDir, zipFile)
            cryptoSecurityManager.encryptFile(zipFile, encryptedFile)
            zipFile.delete()

            if (!encryptedFile.isFile || encryptedFile.length() == 0L) {
                return@withContext Result.failure(ArchiveException("Encryption produced no artifact"))
            }
            val checksum = calculateSha256(encryptedFile)
            Result.success(
                ArchiveArtifact(
                    file = encryptedFile,
                    backupInfo = CloudBackupInfo(
                        backupId = "backup-$timestamp",
                        timestamp = timestamp,
                        backupName = "VVF Backup ${formatTimestamp(timestamp)}",
                        backupSizeBytes = encryptedFile.length(),
                        includesVault = snapshots.containsKey("vault"),
                        includesDatabase = snapshots.containsKey("database"),
                        includesPreferences = false
                    ),
                    checksumSha256 = checksum
                )
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            encryptedFile.delete()
            Result.failure(ArchiveException("Archive creation failed", error))
        } finally {
            stagingDir.deleteRecursively()
            zipFile.delete()
        }
    }

    private fun zipDirectory(sourceDir: File, zipFile: File) {
        ZipOutputStream(zipFile.outputStream()).use { output ->
            sourceDir.walkTopDown().filter { it.isFile }.forEach { file ->
                val entryName = file.relativeTo(sourceDir).invariantSeparatorsPath
                require(entryName.isNotEmpty() && !entryName.startsWith("/") &&
                    entryName.split('/').none { it == ".." }) { "Unsafe archive entry" }
                output.putNextEntry(ZipEntry(entryName))
                file.inputStream().use { it.copyTo(output) }
                output.closeEntry()
            }
        }
    }

    private fun calculateSha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(8192)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun formatTimestamp(timestamp: Long): String =
        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))
}

data class ArchiveArtifact(
    val file: File,
    val backupInfo: CloudBackupInfo,
    val checksumSha256: String
)

@Serializable
data class ArchiveMetadata(
    val version: Int,
    val timestamp: Long,
    val checksumSha256: String,
    val databaseSizeBytes: Long,
    val vaultSizeBytes: Long,
    val fileCount: Int,
    val vaultItemCount: Int,
    val appVersion: String,
    val schemaVersion: Int
)

class ArchiveException(message: String, cause: Throwable? = null) : Exception(message, cause)
