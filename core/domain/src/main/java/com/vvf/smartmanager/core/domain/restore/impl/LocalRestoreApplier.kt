package com.vvf.smartmanager.core.domain.restore.impl

import com.vvf.smartmanager.core.domain.backup.ArchiveMetadata
import com.vvf.smartmanager.core.domain.restore.AppliedRestore
import com.vvf.smartmanager.core.domain.restore.DecryptedBackup
import com.vvf.smartmanager.core.domain.restore.RestoreApplier
import com.vvf.smartmanager.core.domain.restore.RestoreException
import com.vvf.smartmanager.core.domain.restore.RestoreSnapshot
import kotlinx.serialization.json.Json
import java.io.File
import java.util.UUID

/**
 * Snapshot + apply + rollback for live database and vault directories.
 *
 * Apply stages into sibling temp paths and verifies sizes before swapping live paths.
 * Legacy vault authentication metadata is deliberately ignored and never imported.
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
            val dbWalSnap = File(snapshotRoot, "$token-db-wal")
            val vaultSnap = File(snapshotRoot, "$token-vault")
            val databaseExisted = liveDatabaseFile.exists()
            val databaseWal = File(liveDatabaseFile.path + "-wal")
            val databaseWalExisted = databaseWal.isFile
            val vaultExisted = liveVaultDir.exists()
            // Best-effort consistent copy of live DB + WAL (caller should avoid concurrent writers).
            if (databaseExisted) liveDatabaseFile.copyTo(dbSnap, overwrite = true)
            if (databaseWalExisted) databaseWal.copyTo(dbWalSnap, overwrite = true)
            if (vaultExisted) liveVaultDir.copyRecursively(vaultSnap, overwrite = true)
            else vaultSnap.mkdirs()
            Result.success(
                RestoreSnapshot(
                    token = token,
                    timestamp = System.currentTimeMillis(),
                    databaseSnapshotPath = dbSnap,
                    vaultSnapshotPath = vaultSnap,
                    databaseExisted = databaseExisted,
                    vaultExisted = vaultExisted,
                    databaseWalSnapshotPath = dbWalSnap,
                    databaseWalExisted = databaseWalExisted
                )
            )
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

            val manifestFile = File(decryptedBackup.stagingDir, "metadata.json")
            if (manifestFile.isFile) {
                val manifest = json.decodeFromString(ArchiveMetadata.serializer(), manifestFile.readText())
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

            if (decryptedBackup.backupInfo.includesDatabase) {
                if (!decryptedBackup.databaseFile.isFile) {
                    return Result.failure(RestoreException("Backup declares a database but payload is missing"))
                }
                if (tempDb.exists() && !tempDb.delete()) {
                    return Result.failure(RestoreException("Unable to clear previous temp database"))
                }
                decryptedBackup.databaseFile.copyTo(tempDb, overwrite = true)
                if (tempDb.length() != decryptedBackup.databaseFile.length()) {
                    return Result.failure(RestoreException("Database temp copy size mismatch"))
                }
                val sourceWal = File(decryptedBackup.databaseFile.path + "-wal")
                val tempWal = File(tempDb.path + "-wal")
                if (sourceWal.isFile) {
                    sourceWal.copyTo(tempWal, overwrite = true)
                } else {
                    tempWal.delete()
                }
            }

            if (decryptedBackup.backupInfo.includesVault) {
                if (tempVault.exists() && !tempVault.deleteRecursively()) {
                    return Result.failure(RestoreException("Unable to clear previous temp vault"))
                }
                if (!tempVault.mkdirs()) {
                    return Result.failure(RestoreException("Unable to create temp vault directory"))
                }
                if (decryptedBackup.vaultDir.exists()) {
                    decryptedBackup.vaultDir.copyRecursively(tempVault, overwrite = true)
                }
            }

            // Each resource is replaced only when the backup actually contains it.
            if (decryptedBackup.backupInfo.includesDatabase) {
                val liveWal = File(liveDatabaseFile.path + "-wal")
                val liveShm = File(liveDatabaseFile.path + "-shm")
                if (liveWal.exists() && !liveWal.delete()) {
                    return Result.failure(RestoreException("Unable to remove live database WAL for swap"))
                }
                if (liveShm.exists() && !liveShm.delete()) {
                    return Result.failure(RestoreException("Unable to remove live database SHM for swap"))
                }

                if (backupLiveDb.exists() && !backupLiveDb.delete()) {
                    return Result.failure(RestoreException("Unable to clear previous database rollback file"))
                }
                if (liveDatabaseFile.exists() && !liveDatabaseFile.renameTo(backupLiveDb)) {
                    liveDatabaseFile.copyTo(backupLiveDb, overwrite = true)
                    if (!liveDatabaseFile.delete()) {
                        return Result.failure(RestoreException("Unable to move live database aside for swap"))
                    }
                }
                if (!tempDb.renameTo(liveDatabaseFile)) {
                    tempDb.copyTo(liveDatabaseFile, overwrite = true)
                    if (!tempDb.delete()) {
                        return Result.failure(RestoreException("Unable to finish database swap"))
                    }
                }
                if (!liveDatabaseFile.isFile) {
                    return Result.failure(RestoreException("Database swap failed"))
                }

                val tempWal = File(tempDb.path + "-wal")
                if (tempWal.isFile) {
                    val targetWal = File(liveDatabaseFile.path + "-wal")
                    if (!tempWal.renameTo(targetWal)) {
                        tempWal.copyTo(targetWal, overwrite = true)
                        tempWal.delete()
                    }
                    if (!targetWal.isFile) {
                        return Result.failure(RestoreException("Database WAL swap failed"))
                    }
                }
            }

            if (decryptedBackup.backupInfo.includesVault) {
                if (backupLiveVault.exists() && !backupLiveVault.deleteRecursively()) {
                    return Result.failure(RestoreException("Unable to clear previous vault rollback directory"))
                }
                if (liveVaultDir.exists() && !liveVaultDir.renameTo(backupLiveVault)) {
                    liveVaultDir.copyRecursively(backupLiveVault, overwrite = true)
                    if (!liveVaultDir.deleteRecursively()) {
                        return Result.failure(RestoreException("Unable to move live vault aside for swap"))
                    }
                }
                if (!tempVault.renameTo(liveVaultDir)) {
                    if (!liveVaultDir.mkdirs()) {
                        return Result.failure(RestoreException("Unable to create live vault directory"))
                    }
                    tempVault.copyRecursively(liveVaultDir, overwrite = true)
                    if (!tempVault.deleteRecursively()) {
                        return Result.failure(RestoreException("Unable to finish vault swap"))
                    }
                }
                if (!liveVaultDir.isDirectory) {
                    return Result.failure(RestoreException("Vault swap failed"))
                }
            }

            backupLiveDb.delete()
            backupLiveVault.deleteRecursively()
            val fileCount = decryptedBackup.stagingDir.walkTopDown().count { it.isFile }
            val vaultCount = if (decryptedBackup.backupInfo.includesVault) {
                liveVaultDir.walkTopDown().count { it.isFile }
            } else {
                0
            }
            Result.success(
                AppliedRestore(
                    success = true,
                    restoredFileCount = fileCount,
                    restoredVaultItemCount = vaultCount,
                    message = "Restore applied (files=$fileCount, vault=$vaultCount)"
                )
            )
        } catch (error: Throwable) {
            Result.failure(RestoreException("Restore apply failed", error))
        } finally {
            cleanupTemps(tempDb, tempVault)
        }
    }

    override suspend fun rollback(snapshot: RestoreSnapshot): Result<Unit> {
        return try {
            // Validate every required rollback artifact before mutating either live resource.
            if (snapshot.databaseExisted && !snapshot.databaseSnapshotPath.isFile) {
                throw RestoreException("Database rollback snapshot is missing")
            }
            if (snapshot.databaseWalExisted &&
                (snapshot.databaseWalSnapshotPath?.isFile != true)) {
                throw RestoreException("Database WAL rollback snapshot is missing")
            }
            if (snapshot.vaultExisted && !snapshot.vaultSnapshotPath.isDirectory) {
                throw RestoreException("Vault rollback snapshot is missing")
            }

            if (snapshot.databaseExisted) {
                val parent = liveDatabaseFile.parentFile
                    ?: throw RestoreException("Live database parent directory is missing")
                if (!parent.exists() && !parent.mkdirs()) {
                    throw RestoreException("Unable to create database parent during rollback")
                }
                val rollbackTemp = File(parent, "${liveDatabaseFile.name}.rollback-${UUID.randomUUID()}")
                try {
                    snapshot.databaseSnapshotPath.copyTo(rollbackTemp, overwrite = true)
                    if (rollbackTemp.length() != snapshot.databaseSnapshotPath.length()) {
                        throw RestoreException("Database rollback snapshot size mismatch")
                    }
                    if (liveDatabaseFile.exists() && !liveDatabaseFile.delete()) {
                        throw RestoreException("Unable to remove failed database during rollback")
                    }
                    if (!rollbackTemp.renameTo(liveDatabaseFile)) {
                        throw RestoreException("Unable to publish database rollback snapshot")
                    }
                } finally {
                    rollbackTemp.delete()
                }
            } else if (liveDatabaseFile.exists() && !liveDatabaseFile.delete()) {
                throw RestoreException("Unable to remove database created by failed restore")
            }

            val liveWal = File(liveDatabaseFile.path + "-wal")
            if (snapshot.databaseWalExisted) {
                val walSnapshot = snapshot.databaseWalSnapshotPath
                    ?: throw RestoreException("Database WAL rollback snapshot is missing")
                val walTemp = File(liveDatabaseFile.parentFile, "${liveDatabaseFile.name}-wal.rollback-${UUID.randomUUID()}")
                try {
                    walSnapshot.copyTo(walTemp, overwrite = true)
                    if (liveWal.exists() && !liveWal.delete()) {
                        throw RestoreException("Unable to remove failed database WAL during rollback")
                    }
                    if (!walTemp.renameTo(liveWal)) {
                        throw RestoreException("Unable to publish database WAL rollback snapshot")
                    }
                } finally {
                    walTemp.delete()
                }
            } else if (liveWal.exists() && !liveWal.delete()) {
                throw RestoreException("Unable to remove database WAL created by failed restore")
            }
            val liveShm = File(liveDatabaseFile.path + "-shm")
            if (liveShm.exists() && !liveShm.delete()) {
                throw RestoreException("Unable to remove transient database SHM during rollback")
            }

            if (liveVaultDir.exists() && !liveVaultDir.deleteRecursively()) {
                throw RestoreException("Unable to remove failed vault during rollback")
            }
            if (snapshot.vaultExisted) {
                if (!snapshot.vaultSnapshotPath.isDirectory) {
                    throw RestoreException("Vault rollback snapshot is missing")
                }
                snapshot.vaultSnapshotPath.copyRecursively(liveVaultDir, overwrite = true)
                if (!liveVaultDir.isDirectory) {
                    throw RestoreException("Unable to restore vault snapshot")
                }
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
