package com.vvf.smartmanager.core.domain.restore.impl

import com.vvf.smartmanager.core.domain.backup.ArchiveMetadata
import com.vvf.smartmanager.core.domain.restore.AppliedRestore
import com.vvf.smartmanager.core.domain.restore.DecryptedBackup
import com.vvf.smartmanager.core.domain.restore.RestoreApplier
import com.vvf.smartmanager.core.domain.restore.RestoreException
import com.vvf.smartmanager.core.domain.restore.RestoreSnapshot
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.util.Properties
import java.util.UUID
import kotlinx.coroutines.runBlocking

/**
 * Snapshot + apply + rollback for live database and vault directories.
 *
 * Apply stages into sibling temp paths and verifies sizes before swapping live paths.
 * Legacy vault authentication metadata is deliberately ignored and never imported.
 *
 * Portable archives carry the SQLCipher passphrase inside the authenticated envelope; this applier
 * re-wraps that passphrase under the destination device's Keystore and includes its prior wrapped
 * state in rollback. Legacy device-bound archives remain same-device-only.
 *
 * Callers should close Room/SQLCipher connections via [beforeApply] before mutation.
 */
class LocalRestoreApplier(
    private val liveDatabaseFile: File,
    private val liveVaultDir: File,
    private val snapshotRoot: File,
    private val beforeApply: (() -> Unit)? = null,
    private val cryptoSecurityManager: CryptoSecurityManager? = null
) : RestoreApplier {
    private val json = Json { ignoreUnknownKeys = true }

    override suspend fun prepare(): Result<RestoreSnapshot> {
        return try {
            if (!snapshotRoot.exists() && !snapshotRoot.mkdirs()) {
                return Result.failure(RestoreException("Unable to create snapshot root"))
            }
            if (File(snapshotRoot, JOURNAL_FILE).exists()) {
                return Result.failure(RestoreException("An interrupted restore transaction requires recovery before another restore"))
            }
            // Close Room before snapshotting so DB + WAL + key state form a stable rollback point.
            beforeApply?.invoke()
            val token = UUID.randomUUID().toString()
            val dbSnap = File(snapshotRoot, "$token-db")
            val dbWalSnap = File(snapshotRoot, "$token-db-wal")
            val vaultSnap = File(snapshotRoot, "$token-vault")
            val keyStateSnap = File(snapshotRoot, "$token-key-state")
            val databaseExisted = liveDatabaseFile.exists()
            val databaseWal = File(liveDatabaseFile.path + "-wal")
            val databaseWalExisted = databaseWal.isFile
            val vaultExisted = liveVaultDir.exists()
            // Best-effort consistent copy of live DB + WAL (caller should avoid concurrent writers).
            if (databaseExisted) liveDatabaseFile.copyTo(dbSnap, overwrite = true)
            if (databaseWalExisted) databaseWal.copyTo(dbWalSnap, overwrite = true)
            if (vaultExisted) liveVaultDir.copyRecursively(vaultSnap, overwrite = true)
            else vaultSnap.mkdirs()
            cryptoSecurityManager?.snapshotDatabasePassphraseState(keyStateSnap)
            val snapshot = RestoreSnapshot(
                token = token,
                timestamp = System.currentTimeMillis(),
                databaseSnapshotPath = dbSnap,
                vaultSnapshotPath = vaultSnap,
                databaseExisted = databaseExisted,
                vaultExisted = vaultExisted,
                databaseWalSnapshotPath = dbWalSnap,
                databaseWalExisted = databaseWalExisted,
                keyStateSnapshotPath = keyStateSnap.takeIf { cryptoSecurityManager != null },
                keyStateExisted = cryptoSecurityManager != null
            )
            writeJournal(snapshot)
            Result.success(snapshot)
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
            if (!File(snapshotRoot, JOURNAL_FILE).isFile) {
                throw RestoreException("Restore transaction journal is missing; refusing to mutate live data")
            }

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

            if (decryptedBackup.backupInfo.includesDatabase && decryptedBackup.databasePassphraseFile != null) {
                val crypto = cryptoSecurityManager
                    ?: throw RestoreException("Portable database restore requires local Keystore migration support")
                val importedPassphrase = decryptedBackup.databasePassphraseFile.readBytes()
                try {
                    crypto.replaceDatabasePassphraseForRestore(importedPassphrase)
                } finally {
                    importedPassphrase.fill(0)
                }
            }

            if (backupLiveDb.exists() && !backupLiveDb.delete()) {
                throw RestoreException("Unable to remove previous database after successful swap")
            }
            if (backupLiveVault.exists() && !backupLiveVault.deleteRecursively()) {
                throw RestoreException("Unable to remove previous vault after successful swap")
            }
            clearJournal(snapshotTokenFromJournal())
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
            if (snapshot.keyStateExisted) {
                val crypto = cryptoSecurityManager
                    ?: throw RestoreException("Database key-state rollback requires Keystore support")
                crypto.validateDatabasePassphraseStateSnapshot(
                    snapshot.keyStateSnapshotPath
                        ?: throw RestoreException("Database key-state rollback snapshot is missing")
                )
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

            if (snapshot.keyStateExisted) {
                val crypto = cryptoSecurityManager
                    ?: throw RestoreException("Database key-state rollback requires Keystore support")
                crypto.restoreDatabasePassphraseState(
                    snapshot.keyStateSnapshotPath
                        ?: throw RestoreException("Database key-state rollback snapshot is missing")
                )
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
            cleanupInterruptedSwapArtifacts()
            clearJournal(snapshot.token)
            Result.success(Unit)
        } catch (error: Throwable) {
            Result.failure(RestoreException("Restore rollback failed", error))
        }
    }

    private fun cleanupInterruptedSwapArtifacts() {
        val parent = liveDatabaseFile.parentFile ?: return
        val databasePrefix = liveDatabaseFile.name
        val vaultPrefix = liveVaultDir.name
        parent.listFiles().orEmpty()
            .filter { file ->
                file.name == "$databasePrefix.restore-bak" ||
                    file.name.startsWith("$databasePrefix.restore-tmp") ||
                    file.name.startsWith("$databasePrefix.rollback-") ||
                    file.name.startsWith("$databasePrefix-wal.rollback-") ||
                    file.name.startsWith("$vaultPrefix.restore-bak-") ||
                    file.name.startsWith("$vaultPrefix.restore-tmp-")
            }
            .forEach { file ->
                val removed = if (file.isDirectory) file.deleteRecursively() else file.delete()
                if (!removed && file.exists()) {
                    throw RestoreException("Unable to clean interrupted restore artifact: ${file.name}")
                }
            }
    }

    private fun writeJournal(snapshot: RestoreSnapshot) {
        val journal = File(snapshotRoot, JOURNAL_FILE)
        val temporary = File(snapshotRoot, "$JOURNAL_FILE.tmp")
        val properties = Properties().apply {
            setProperty("token", snapshot.token)
            setProperty("timestamp", snapshot.timestamp.toString())
            setProperty("databaseExisted", snapshot.databaseExisted.toString())
            setProperty("vaultExisted", snapshot.vaultExisted.toString())
            setProperty("databaseWalExisted", snapshot.databaseWalExisted.toString())
            setProperty("keyStateExisted", snapshot.keyStateExisted.toString())
        }
        try {
            FileOutputStream(temporary).use { output ->
                properties.store(output, "Active restore transaction; rollback before opening the database")
                output.flush()
                output.fd.sync()
            }
            check(temporary.renameTo(journal)) { "Unable to publish restore recovery journal" }
        } catch (error: Throwable) {
            temporary.delete()
            throw error
        }
    }

    private fun snapshotTokenFromJournal(): String {
        val journal = File(snapshotRoot, JOURNAL_FILE)
        require(journal.isFile) { "Restore recovery journal is missing" }
        val properties = Properties().apply { FileInputStream(journal).use { load(it) } }
        return properties.getProperty("token") ?: throw RestoreException("Restore journal token is missing")
    }

    private fun clearJournal(token: String) {
        val journal = File(snapshotRoot, JOURNAL_FILE)
        if (!journal.exists()) return
        val properties = Properties().apply { FileInputStream(journal).use { load(it) } }
        if (properties.getProperty("token") == token) {
            check(journal.delete()) { "Unable to clear completed restore journal" }
        }
    }

    private fun cleanupTemps(tempDb: File, tempVault: File) {
        runCatching { if (tempDb.exists()) tempDb.delete() }
        runCatching { File(tempDb.path + "-wal").delete() }
        runCatching { if (tempVault.exists()) tempVault.deleteRecursively() }
    }

    companion object {
        private const val JOURNAL_FILE = "active-restore.properties"

        /**
         * Recover a process-death interruption before Room/SQLCipher is opened.
         * Any malformed journal or missing rollback artifact fails startup closed.
         */
        private fun hasInterruptedSwapArtifacts(liveDatabaseFile: File, liveVaultDir: File): Boolean {
            val parent = liveDatabaseFile.parentFile ?: return false
            val db = liveDatabaseFile.name
            val vault = liveVaultDir.name
            return parent.listFiles().orEmpty().any { file ->
                file.name == "$db.restore-bak" ||
                    file.name.startsWith("$db.restore-tmp") ||
                    file.name.startsWith("$db.rollback-") ||
                    file.name.startsWith("$db-wal.rollback-") ||
                    file.name.startsWith("$vault.restore-bak-") ||
                    file.name.startsWith("$vault.restore-tmp-")
            }
        }

        /**
         * Older releases could leave swap files without a journal. Prefer a retained pre-restore
         * backup over any live target, because the live target may be the interrupted new payload.
         * If no backup exists, keep an existing live target and discard only transient staging.
         * Ambiguous multiple vault backups fail closed rather than guessing which data is valid.
         */
        private fun recoverLegacyUnjournaledSwapArtifacts(
            liveDatabaseFile: File,
            liveVaultDir: File
        ) {
            val parent = liveDatabaseFile.parentFile
                ?: throw IllegalStateException("Restore recovery has no database parent directory")
            val dbPrefix = liveDatabaseFile.name
            val vaultPrefix = liveVaultDir.name
            val entries = parent.listFiles().orEmpty()
            val dbBackup = File(parent, "$dbPrefix.restore-bak")
            val vaultBackups = entries.filter {
                it.name.startsWith("$vaultPrefix.restore-bak-") && it.isDirectory
            }
            if (vaultBackups.size > 1) {
                throw IllegalStateException("Multiple legacy vault rollback snapshots exist; manual recovery is required")
            }

            if (dbBackup.exists()) {
                if (!dbBackup.isFile || dbBackup.length() == 0L) {
                    throw IllegalStateException("Legacy database rollback artifact is invalid")
                }
                val recoveryTemp = File(parent, "$dbPrefix.legacy-recovery-tmp")
                if (recoveryTemp.exists() && !recoveryTemp.delete()) {
                    throw IllegalStateException("Unable to clear legacy database recovery temporary file")
                }
                dbBackup.copyTo(recoveryTemp, overwrite = true)
                if (recoveryTemp.length() != dbBackup.length()) {
                    recoveryTemp.delete()
                    throw IllegalStateException("Legacy database rollback copy is incomplete")
                }
                if (liveDatabaseFile.exists() && !liveDatabaseFile.delete()) {
                    recoveryTemp.delete()
                    throw IllegalStateException("Unable to replace interrupted database with its rollback copy")
                }
                if (!recoveryTemp.renameTo(liveDatabaseFile)) {
                    recoveryTemp.delete()
                    throw IllegalStateException("Unable to publish legacy database rollback copy")
                }
            } else if (!liveDatabaseFile.isFile) {
                throw IllegalStateException(
                    "Restore swap artifacts exist but neither a live database nor a rollback copy is available"
                )
            }

            val vaultBackup = vaultBackups.singleOrNull()
            if (vaultBackup != null) {
                val recoveryTemp = File(parent, "$vaultPrefix.legacy-recovery-tmp")
                if (recoveryTemp.exists() && !recoveryTemp.deleteRecursively()) {
                    throw IllegalStateException("Unable to clear legacy vault recovery temporary directory")
                }
                vaultBackup.copyRecursively(recoveryTemp, overwrite = true)
                if (!recoveryTemp.isDirectory) {
                    recoveryTemp.deleteRecursively()
                    throw IllegalStateException("Legacy vault rollback copy is incomplete")
                }
                if (liveVaultDir.exists() && !liveVaultDir.deleteRecursively()) {
                    recoveryTemp.deleteRecursively()
                    throw IllegalStateException("Unable to replace interrupted vault with its rollback copy")
                }
                if (!recoveryTemp.renameTo(liveVaultDir)) {
                    recoveryTemp.copyRecursively(liveVaultDir, overwrite = true)
                    if (!recoveryTemp.deleteRecursively()) {
                        throw IllegalStateException("Unable to publish legacy vault rollback copy")
                    }
                }
            }

            entries.filter { file ->
                file.name.startsWith("$dbPrefix.restore-tmp") ||
                    file.name.startsWith("$dbPrefix.rollback-") ||
                    file.name.startsWith("$dbPrefix-wal.rollback-") ||
                    file.name.startsWith("$vaultPrefix.restore-tmp-") ||
                    file.name == "$dbPrefix.restore-bak" ||
                    file.name.startsWith("$vaultPrefix.restore-bak-")
            }.forEach { file ->
                val removed = if (file.isDirectory) file.deleteRecursively() else file.delete()
                if (!removed && file.exists()) {
                    throw IllegalStateException("Unable to retire legacy restore artifact: ${file.name}")
                }
            }
        }

        fun recoverInterruptedRestore(
            liveDatabaseFile: File,
            liveVaultDir: File,
            snapshotRoot: File,
            cryptoSecurityManager: CryptoSecurityManager
        ) {
            val journal = File(snapshotRoot, JOURNAL_FILE)
            if (!journal.isFile) {
                if (hasInterruptedSwapArtifacts(liveDatabaseFile, liveVaultDir)) {
                    recoverLegacyUnjournaledSwapArtifacts(liveDatabaseFile, liveVaultDir)
                }
                // No transaction journal means there is no trustworthy token-to-snapshot mapping.
                // Only after safely reconciling legacy swap files may the unreferenced snapshot
                // directory be retired.
                if (snapshotRoot.exists()) {
                    snapshotRoot.listFiles().orEmpty().forEach { child ->
                        if (!child.deleteRecursively() && child.exists()) {
                            throw IllegalStateException("Unable to clean unreferenced restore snapshot artifact")
                        }
                    }
                }
                return
            }
            val properties = Properties().apply { FileInputStream(journal).use(::load) }
            val token = properties.getProperty("token")
                ?: throw IllegalStateException("Restore journal token is missing")
            require(runCatching { UUID.fromString(token).toString() }.getOrNull() == token) {
                "Restore journal token is invalid"
            }
            val snapshot = RestoreSnapshot(
                token = token,
                timestamp = properties.getProperty("timestamp")?.toLongOrNull()
                    ?: throw IllegalStateException("Restore journal timestamp is invalid"),
                databaseSnapshotPath = File(snapshotRoot, "$token-db"),
                vaultSnapshotPath = File(snapshotRoot, "$token-vault"),
                databaseExisted = properties.getProperty("databaseExisted")?.toBooleanStrictOrNull()
                    ?: throw IllegalStateException("Restore journal database flag is invalid"),
                vaultExisted = properties.getProperty("vaultExisted")?.toBooleanStrictOrNull()
                    ?: throw IllegalStateException("Restore journal vault flag is invalid"),
                databaseWalSnapshotPath = File(snapshotRoot, "$token-db-wal"),
                databaseWalExisted = properties.getProperty("databaseWalExisted")?.toBooleanStrictOrNull()
                    ?: throw IllegalStateException("Restore journal WAL flag is invalid"),
                keyStateSnapshotPath = File(snapshotRoot, "$token-key-state"),
                keyStateExisted = properties.getProperty("keyStateExisted")?.toBooleanStrictOrNull()
                    ?: throw IllegalStateException("Restore journal key-state flag is invalid")
            )
            val applier = LocalRestoreApplier(
                liveDatabaseFile = liveDatabaseFile,
                liveVaultDir = liveVaultDir,
                snapshotRoot = snapshotRoot,
                cryptoSecurityManager = cryptoSecurityManager
            )
            val rollback = runBlocking { applier.rollback(snapshot) }
            if (rollback.isFailure) {
                throw IllegalStateException(
                    "Interrupted restore recovery failed; refusing to open the database",
                    rollback.exceptionOrNull()
                )
            }
            snapshotRoot.listFiles().orEmpty().forEach { it.deleteRecursively() }
        }
    }
}
