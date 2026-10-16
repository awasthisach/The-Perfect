package com.vvf.smartmanager.core.domain.restore.impl

import com.vvf.smartmanager.core.domain.backup.ArchiveMetadata
import com.vvf.smartmanager.core.domain.restore.AppliedRestore
import com.vvf.smartmanager.core.domain.restore.DecryptedBackup
import com.vvf.smartmanager.core.domain.restore.RestoreApplier
import com.vvf.smartmanager.core.domain.restore.RestoreException
import com.vvf.smartmanager.core.domain.restore.RestoreSnapshot
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Snapshot + apply + rollback for live database and vault directories.
 *
 * Apply stages into sibling temp paths, verifies sizes, imports vault auth, then swaps
 * onto live paths so a mid-apply crash is less likely to leave a half-wiped vault.
 *
 * Note: the SQLCipher database file is bound to the device Keystore passphrase.
 * Same-device restore works when the live passphrase matches the snapshotted DB.
 * Cross-device restore requires a future portable-key export (documented residual).
 *
 * Callers should close Room/SQLCipher connections via [beforeApply] before mutation.
 */
class LocalRestoreApplier(
    private val liveDatabaseFile: File,
    private val liveVaultDir: File,
    private val snapshotRoot: File,
    private val vaultAuthImporter: ((Map<String, String>) -> Boolean)? = null,
    private val beforeApply: (() -> Unit)? = null
) : RestoreApplier {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun prepare(): Result<RestoreSnapshot> {
        return try {
            if (!snapshotRoot.exists() && !snapshotRoot.mkdirs()) {
                return Result.failure(RestoreException("Unable to create snapshot root"))
            }
            val token = UUID.randomUUID().toString()
            val dbSnap = File(snapshotRoot, "$token-db")
            val vaultSnap = File(snapshotRoot, "$token-vault")
            // Best-effort consistent copy of live DB file (caller should avoid concurrent writers).
            if (liveDatabaseFile.exists()) liveDatabaseFile.copyTo(dbSnap, overwrite = true)
            if (liveVaultDir.exists()) liveVaultDir.copyRecursively(vaultSnap, overwrite = true)
            else vaultSnap.mkdirs()
            Result.success(RestoreSnapshot(token, System.currentTimeMillis(), dbSnap, vaultSnap))
        } catch (error: Throwable) {
            Result.failure(RestoreException("Restore snapshot prepare failed", error))
        }
    }

    override suspend fun apply(decryptedBackup: DecryptedBackup): Result<AppliedRestore> {
        val parent = liveDatabaseFile.parentFile
            ?: return Result.failure(RestoreException("Live database parent directory is missing"))
        if (!parent.exists() && !parent.mkdirs()) {
            return Result.failure(RestoreException("Unable to create live database parent directory"))
        }

        val tempDb = File(parent, "${liveDatabaseFile.name}.restore-tmp")
        val tempVault = File(parent, "${liveVaultDir.name}.restore-tmp-${UUID.randomUUID()}")
        val backupLiveDb = File(parent, "${liveDatabaseFile.name}.restore-bak")
        val backupLiveVault = File(parent, "${liveVaultDir.name}.restore-bak-${UUID.randomUUID()}")

        return try {
            // A manifest is required to distinguish full backups from deliberately partial backups.
            // Never infer a missing component as an empty component and overwrite live user data.
            val manifestFile = File(decryptedBackup.stagingDir, "metadata.json")
            if (!manifestFile.isFile) {
                return Result.failure(RestoreException("Backup manifest is missing; live data left unchanged"))
            }
            val manifest = json.decodeFromString(ArchiveMetadata.serializer(), manifestFile.readText())
            val includesDatabase = manifest.includesDatabase
            val includesVault = manifest.includesVault
            if (includesDatabase && (!decryptedBackup.databaseFile.isFile || decryptedBackup.databaseFile.length() <= 0L)) {
                return Result.failure(RestoreException("Backup declares a database but its payload is missing or empty"))
            }
            if (includesVault && !decryptedBackup.vaultDir.isDirectory) {
                return Result.failure(RestoreException("Backup declares a vault but its payload directory is missing"))
            }
            if (!includesDatabase && !includesVault) {
                return Result.failure(RestoreException("Backup contains no restorable database or vault component"))
            }

            // Validate the staged archive completely before closing connections or touching live data.
            val actualFiles = decryptedBackup.stagingDir.walkTopDown()
                .count { it.isFile && it.name != "metadata.json" }
            if (actualFiles < manifest.fileCount) {
                return Result.failure(
                    RestoreException(
                        "Incomplete restore: expected ${manifest.fileCount} files, extracted $actualFiles"
                    )
                )
            }
            val actualVault = decryptedBackup.vaultDir.walkTopDown().count { it.isFile }
            if (includesVault && actualVault < manifest.vaultItemCount) {
                return Result.failure(
                    RestoreException(
                        "Incomplete restore: expected ${manifest.vaultItemCount} vault items, extracted $actualVault"
                    )
                )
            }

            beforeApply?.invoke()

            // --- Stage only the components explicitly included in the manifest ---
            if (includesDatabase) {
                if (tempDb.exists() && !tempDb.delete()) {
                    return Result.failure(RestoreException("Unable to clear previous temp database"))
                }
                decryptedBackup.databaseFile.copyTo(tempDb, overwrite = true)
                if (!tempDb.isFile || tempDb.length() != decryptedBackup.databaseFile.length() || tempDb.length() <= 0L) {
                    tempDb.delete()
                    return Result.failure(RestoreException("Database temp copy verification failed"))
                }
            }

            if (includesVault) {
                if (tempVault.exists()) tempVault.deleteRecursively()
                if (!tempVault.mkdirs()) {
                    return Result.failure(RestoreException("Unable to create temp vault directory"))
                }
                decryptedBackup.vaultDir.copyRecursively(tempVault, overwrite = true)
            }

            // --- Auth metadata is relevant only when restoring the vault component ---
            val authFile = File(decryptedBackup.stagingDir, "vault_auth.json")
            if (includesVault && manifest.includesVaultAuth && authFile.isFile && vaultAuthImporter != null) {
                val metadata = json.decodeFromString(
                    MapSerializer(String.serializer(), String.serializer()),
                    authFile.readText()
                )
                val imported = vaultAuthImporter.invoke(metadata)
                if (!imported) {
                    cleanupTemps(tempDb, tempVault)
                    return Result.failure(
                        RestoreException("Vault auth metadata import failed; live data left unchanged")
                    )
                }
            }

            // --- Commit database only if this backup explicitly contains it ---
            if (includesDatabase) {
                if (backupLiveDb.exists() && !backupLiveDb.delete()) {
                    cleanupTemps(tempDb, tempVault)
                    return Result.failure(RestoreException("Unable to clear previous database rollback file"))
                }
                if (liveDatabaseFile.exists()) {
                    if (!liveDatabaseFile.renameTo(backupLiveDb)) {
                        liveDatabaseFile.copyTo(backupLiveDb, overwrite = true)
                        if (!liveDatabaseFile.delete()) {
                            cleanupTemps(tempDb, tempVault)
                            return Result.failure(RestoreException("Unable to move live database aside for swap"))
                        }
                    }
                }
                if (!tempDb.renameTo(liveDatabaseFile)) {
                    tempDb.copyTo(liveDatabaseFile, overwrite = true)
                    tempDb.delete()
                }
                if (!liveDatabaseFile.isFile || liveDatabaseFile.length() <= 0L) {
                    if (backupLiveDb.isFile) backupLiveDb.copyTo(liveDatabaseFile, overwrite = true)
                    cleanupTemps(tempDb, tempVault)
                    return Result.failure(RestoreException("Database swap failed"))
                }
            }

            // --- Commit vault only if this backup explicitly contains it ---
            if (includesVault) {
                if (backupLiveVault.exists()) backupLiveVault.deleteRecursively()
                if (liveVaultDir.exists()) {
                    if (!liveVaultDir.renameTo(backupLiveVault)) {
                        liveVaultDir.copyRecursively(backupLiveVault, overwrite = true)
                        liveVaultDir.deleteRecursively()
                    }
                }
                if (!tempVault.renameTo(liveVaultDir)) {
                    liveVaultDir.mkdirs()
                    tempVault.copyRecursively(liveVaultDir, overwrite = true)
                    tempVault.deleteRecursively()
                }
                if (!liveVaultDir.isDirectory) {
                    if (backupLiveVault.exists()) backupLiveVault.copyRecursively(liveVaultDir, overwrite = true)
                    if (includesDatabase && backupLiveDb.isFile) backupLiveDb.copyTo(liveDatabaseFile, overwrite = true)
                    cleanupTemps(tempDb, tempVault)
                    return Result.failure(RestoreException("Vault swap failed"))
                }
            }

            // Success — drop only rollback files created for components being restored.
            if (includesDatabase) backupLiveDb.delete()
            if (includesVault && backupLiveVault.exists()) backupLiveVault.deleteRecursively()
            cleanupTemps(tempDb, tempVault)

            val fileCount = if (includesDatabase) decryptedBackup.stagingDir.walkTopDown().count { it.isFile } else 0
            val vaultCount = if (includesVault) liveVaultDir.walkTopDown().count { it.isFile } else 0
            Result.success(
                AppliedRestore(
                    success = true,
                    restoredFileCount = fileCount,
                    restoredVaultItemCount = vaultCount,
                    message = "Restore applied (files=$fileCount, vault=$vaultCount)"
                )
            )
        } catch (error: Throwable) {
            cleanupTemps(tempDb, tempVault)
            Result.failure(RestoreException("Restore apply failed", error))
        }
    }

    override suspend fun rollback(snapshot: RestoreSnapshot): Result<Unit> {
        return try {
            if (snapshot.databaseSnapshotPath.isFile) {
                snapshot.databaseSnapshotPath.copyTo(liveDatabaseFile, overwrite = true)
            }
            if (liveVaultDir.exists()) liveVaultDir.deleteRecursively()
            liveVaultDir.mkdirs()
            if (snapshot.vaultSnapshotPath.exists()) {
                snapshot.vaultSnapshotPath.copyRecursively(liveVaultDir, overwrite = true)
            }
            Result.success(Unit)
        } catch (error: Throwable) {
            Result.failure(RestoreException("Restore rollback failed", error))
        }
    }

    private fun cleanupTemps(tempDb: File, tempVault: File) {
        runCatching { if (tempDb.exists()) tempDb.delete() }
        runCatching { if (tempVault.exists()) tempVault.deleteRecursively() }
    }
}
