package com.vvf.smartmanager.core.domain.backup

import com.vvf.smartmanager.core.data.backup.SnapshotSource
import com.vvf.smartmanager.core.model.CloudBackupInfo
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import com.vvf.smartmanager.core.security.PortableBackupCrypto
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
    private val portableCrypto = PortableBackupCrypto()

    suspend fun createArchive(
        includeVault: Boolean = true,
        includeDatabase: Boolean = true,
        passphrase: CharArray? = null,
        vaultPin: String? = null
    ): Result<ArchiveArtifact> = withContext(Dispatchers.IO) {
        if (passphrase == null || passphrase.size < 12) {
            passphrase?.fill(0.toChar())
            return@withContext Result.failure(ArchiveException("A backup passphrase of at least 12 characters is required"))
        }
        if (!includeDatabase && includeVault) {
            passphrase.fill(0.toChar())
            return@withContext Result.failure(
                ArchiveException("Vault-only backups are unsupported because vault metadata is stored in the encrypted database")
            )
        }
        if (!includeDatabase && !includeVault) {
            passphrase.fill(0.toChar())
            return@withContext Result.failure(ArchiveException("At least one backup resource must be selected"))
        }
        if (includeDatabase && !includeVault && snapshotSources.none { it.sourceName == "vault" }) {
            passphrase.fill(0.toChar())
            return@withContext Result.failure(
                ArchiveException("Cannot safely omit the vault without a vault snapshot source to validate database metadata consistency")
            )
        }
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
                (source.sourceName == "vault" && (includeVault || includeDatabase)) ||
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

            // Vault rows are stored in the SQLCipher database. A database-only archive is safe
            // only when the source vault is empty; otherwise its metadata would reference omitted files.
            if (includeDatabase && !includeVault) {
                val stagedVault = File(stagingDir, "vault")
                if (stagedVault.walkTopDown().any { it.isFile }) {
                    throw ArchiveException(
                        "The vault contains files referenced by database metadata; enable vault inclusion to create a consistent backup"
                    )
                }
                if (stagedVault.exists() && !stagedVault.deleteRecursively()) {
                    throw ArchiveException("Unable to remove empty vault validation staging")
                }
            }

            if (includeDatabase) {
                val databaseDir = File(stagingDir, "database")
                val databaseFile = databaseDir.walkTopDown().firstOrNull {
                    it.isFile && !it.name.endsWith("-wal") && !it.name.endsWith("-shm")
                } ?: throw ArchiveException("Database snapshot is missing")
                if (databaseFile.length() == 0L) {
                    throw ArchiveException("Refusing to create a backup with an empty database snapshot")
                }
                val keyBytes = cryptoSecurityManager.getOrCreateDatabasePassphrase()
                try {
                    val keyDir = File(stagingDir, "key-material")
                    if (!keyDir.mkdirs() && !keyDir.isDirectory) throw ArchiveException("Unable to stage portable key material")
                    File(keyDir, "database-passphrase.bin").writeBytes(keyBytes)
                } finally {
                    keyBytes.fill(0)
                }
            }

            if (includeVault) {
                val vaultDir = File(stagingDir, "vault")
                if (!vaultDir.isDirectory) throw ArchiveException("Vault snapshot is missing")
                val vaultFiles = vaultDir.walkTopDown().filter { it.isFile }.toList()
                if (vaultFiles.isNotEmpty()) {
                    if (vaultPin.isNullOrBlank() ||
                        !cryptoSecurityManager.isVaultConfigured() ||
                        !cryptoSecurityManager.verifyVaultPin(vaultPin)
                    ) {
                        throw ArchiveException("The current real vault PIN is required to include vault contents")
                    }
                }
                vaultFiles.forEach { encryptedVaultFile ->
                    val plainTemp = File(encryptedVaultFile.parentFile, encryptedVaultFile.name + ".portable-plain")
                    try {
                        cryptoSecurityManager.decryptFile(encryptedVaultFile, plainTemp)
                        if (!encryptedVaultFile.delete()) {
                            throw ArchiveException("Unable to replace staged vault ciphertext with portable payload")
                        }
                        if (!plainTemp.renameTo(encryptedVaultFile)) {
                            plainTemp.copyTo(encryptedVaultFile, overwrite = true)
                            if (!plainTemp.delete()) {
                                throw ArchiveException("Unable to clean staged vault plaintext")
                            }
                        }
                    } catch (error: Throwable) {
                        // A failed decrypt/replace may leave plaintext or partial output in staging.
                        val plaintextRemoved = cryptoSecurityManager.secureShredFile(plainTemp)
                        val stagedRemoved = cryptoSecurityManager.secureShredFile(encryptedVaultFile)
                        if ((!plaintextRemoved && plainTemp.exists()) ||
                            (!stagedRemoved && encryptedVaultFile.exists())
                        ) {
                            throw ArchiveException("Unable to securely clean failed vault export staging", error)
                        }
                        throw error
                    }
                }
            }

            val metadata = ArchiveMetadata(
                version = 4,
                timestamp = timestamp,
                checksumSha256 = "",
                databaseSizeBytes = requestedSources.firstOrNull { it.sourceName == "database" }?.dataSizeBytes() ?: 0L,
                vaultSizeBytes = requestedSources.firstOrNull { it.sourceName == "vault" }?.dataSizeBytes() ?: 0L,
                fileCount = stagingDir.walkTopDown().count {
                    it.isFile && it.relativeTo(stagingDir).invariantSeparatorsPath != "key-material/database-passphrase.bin"
                },
                vaultItemCount = File(stagingDir, "vault").walkTopDown().count { it.isFile },
                appVersion = appVersion,
                schemaVersion = schemaVersion,
                includesDatabase = includeDatabase,
                includesVault = includeVault,
            )
            File(stagingDir, "metadata.json").writeText(json.encodeToString(metadata))
            zipDirectory(stagingDir, zipFile)
            portableCrypto.encryptFile(zipFile, encryptedFile, passphrase)
            // The staging tree contains the raw SQLCipher passphrase and temporarily decrypted
            // vault payloads. Overwrite staged files before unlinking; never publish if cleanup fails.
            cleanupPlaintextArtifactsOrThrow(stagingDir, zipFile)

            if (!encryptedFile.isFile || encryptedFile.length() == 0L) {
                return@withContext Result.failure(ArchiveException("Encryption produced no artifact"))
            }
            val checksum = calculateSha256(encryptedFile)
            val backupInfo = CloudBackupInfo(
                backupId = "backup-$timestamp",
                timestamp = timestamp,
                backupName = "VVF Backup ${formatTimestamp(timestamp)}",
                backupSizeBytes = encryptedFile.length(),
                includesVault = includeVault,
                includesDatabase = includeDatabase,
                includesPreferences = false,
                checksumSha256 = checksum
            )
            Result.success(
                ArchiveArtifact(
                    file = encryptedFile,
                    backupInfo = backupInfo,
                    checksumSha256 = checksum
                )
            )
        } catch (error: Throwable) {
            if (error is CancellationException) throw error
            runCatching { cleanupPlaintextArtifactsOrThrow(stagingDir, zipFile) }
            encryptedFile.delete()
            val reportedError = if (error is ArchiveException) {
                error
            } else {
                ArchiveException("Archive creation failed; no backup was published", error)
            }
            Result.failure(reportedError)
        } finally {
            passphrase.fill(0.toChar())
            // Best-effort second pass if an earlier cleanup attempt encountered a transient error.
            runCatching { cleanupPlaintextArtifactsOrThrow(stagingDir, zipFile) }
        }
    }

    private fun cleanupPlaintextArtifactsOrThrow(stagingDir: File, zipFile: File) {
        val failed = mutableListOf<String>()
        if (zipFile.exists() && !cryptoSecurityManager.secureShredFile(zipFile) && zipFile.exists()) {
            failed += zipFile.name
        }
        if (stagingDir.exists()) {
            stagingDir.walkBottomUp().filter { it.isFile }.forEach { file ->
                if (!cryptoSecurityManager.secureShredFile(file) && file.exists()) {
                    failed += file.name
                }
            }
            stagingDir.walkBottomUp().filter { it.isDirectory }.forEach { directory ->
                if (!directory.delete() && directory.exists()) failed += directory.name
            }
        }
        if (failed.isNotEmpty()) {
            throw ArchiveException("Unable to securely remove plaintext staging artifacts: ${failed.distinct().joinToString()}")
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
    val schemaVersion: Int,
    // Version 3+ records payload selection explicitly; defaults keep legacy metadata readable.
    val includesDatabase: Boolean = true,
    val includesVault: Boolean = false,
)

class ArchiveException(message: String, cause: Throwable? = null) : Exception(message, cause)
