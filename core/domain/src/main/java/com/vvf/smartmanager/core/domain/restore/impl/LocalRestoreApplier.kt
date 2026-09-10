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
            beforeApply?.invoke()

            // --- Manifest completeness (when present) ---
            val manifestFile = File(decryptedBackup.stagingDir, "metadata.json")
            if (manifestFile.isFile) {
                val manifest = json.decodeFromString(ArchiveMetadata.serializer(), manifestFile.readText())
                // ArchiveService counts files before writing metadata.json, so exclude it here.
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
                if (actualVault < manifest.vaultItemCount) {
                    return Result.failure(
                        RestoreException(
                            "Incomplete restore: expected ${manifest.vaultItemCount} vault items, extracted $actualVault"
                        )
                    )
                }
            }

            // --- Stage database to temp (do not touch live yet) ---
            if (tempDb.exists() && !tempDb.delete()) {
                return Result.failure(RestoreException("Unable to clear previous temp database"))
            }
            if (decryptedBackup.databaseFile.isFile) {
                decryptedBackup.databaseFile.copyTo(tempDb, overwrite = true)
                if (tempDb.length() != decryptedBackup.databaseFile.length()) {
                    tempDb.delete()
                    return Result.failure(RestoreException("Database temp copy size mismatch"))
                }
            }

            // --- Stage vault to temp ---
            if (tempVault.exists()) tempVault.deleteRecursively()
            if (!tempVault.mkdirs()) {
                return Result.failure(RestoreException("Unable to create temp vault directory"))
            }
            if (decryptedBackup.vaultDir.exists()) {
                decryptedBackup.vaultDir.copyRecursively(tempVault, overwrite = true)
            }

            // --- Auth metadata before committing live vault/DB ---
            val authFile = File(decryptedBackup.stagingDir, "vault_auth.json")
            if (authFile.isFile && vaultAuthImporter != null) {
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

            // --- Commit DB: keep old as .restore-bak then swap temp into place ---
            if (backupLiveDb.exists()) backupLiveDb.delete()
            if (liveDatabaseFile.exists()) {
                if (!liveDatabaseFile.renameTo(backupLiveDb)) {
                    liveDatabaseFile.copyTo(backupLiveDb, overwrite = true)
                    if (!liveDatabaseFile.delete()) {
                        cleanupTemps(tempDb, tempVault)
                        return Result.failure(RestoreException("Unable to move live database aside for swap"))
                    }
                }
            }
            if (tempDb.isFile) {
                if (!tempDb.renameTo(liveDatabaseFile)) {
                    tempDb.copyTo(liveDatabaseFile, overwrite = true)
                    tempDb.delete()
                }
                if (!liveDatabaseFile.isFile) {
                    // attempt emergency restore from bak
                    if (backupLiveDb.isFile) backupLiveDb.copyTo(liveDatabaseFile, overwrite = true)
                    cleanupTemps(tempDb, tempVault)
                    return Result.failure(RestoreException("Database swap failed"))
                }
            }

            // --- Commit vault: move live aside, move temp into place ---
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
                if (backupLiveVault.exists()) {
                    backupLiveVault.copyRecursively(liveVaultDir, overwrite = true)
                }
                if (backupLiveDb.isFile) backupLiveDb.copyTo(liveDatabaseFile, overwrite = true)
                cleanupTemps(tempDb, tempVault)
                return Result.failure(RestoreException("Vault swap failed"))
            }

            // Success — drop rollback backups and temps
            backupLiveDb.delete()
            if (backupLiveVault.exists()) backupLiveVault.deleteRecursively()
            cleanupTemps(tempDb, tempVault)

            val fileCount = decryptedBackup.stagingDir.walkTopDown().count { it.isFile }
            val vaultCount = liveVaultDir.walkTopDown().count { it.isFile }
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
