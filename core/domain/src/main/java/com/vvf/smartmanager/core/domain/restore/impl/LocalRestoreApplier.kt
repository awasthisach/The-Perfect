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
import java.security.MessageDigest
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
            ?: run {
                decryptedBackup.databasePassphrase?.fill(0)
                return Result.failure(RestoreException("Live database parent directory is missing"))
            }
        if (!parent.exists() && !parent.mkdirs()) {
            decryptedBackup.databasePassphrase?.fill(0)
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

            // Vault item metadata is stored in the database. Reject partial restores that would
            // pair a database from one point in time with unrelated live vault files.
            if (decryptedBackup.backupInfo.includesVault && !decryptedBackup.backupInfo.includesDatabase) {
                throw RestoreException("Vault-only restore is unsafe because vault metadata lives in the database")
            }
            if (decryptedBackup.backupInfo.includesDatabase && !decryptedBackup.backupInfo.includesVault &&
                liveVaultDir.walkTopDown().any { it.isFile }
            ) {
                throw RestoreException(
                    "Database-only restore cannot be applied while the live vault contains files; use a consistent full backup"
                )
            }

            val manifestFile = File(decryptedBackup.stagingDir, "metadata.json")
            if (manifestFile.isFile) {
                val manifest = json.decodeFromString(ArchiveMetadata.serializer(), manifestFile.readText())
                val actualFiles = decryptedBackup.stagingDir.walkTopDown()
                    .count { it.isFile && it.name != "metadata.json" }
                val removedLegacyKeyMaterial = if (
                    decryptedBackup.databasePassphrase != null && manifest.version < 4
                ) 1 else 0
                val expectedFiles = manifest.fileCount - removedLegacyKeyMaterial
                if (actualFiles < expectedFiles) {
                    return Result.failure(
                        RestoreException(
                            "Incomplete restore: expected $expectedFiles files after secure key-material extraction, found $actualFiles"
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

            if (decryptedBackup.backupInfo.includesDatabase && decryptedBackup.databasePassphrase != null) {
                val crypto = cryptoSecurityManager
                    ?: throw RestoreException("Portable database restore requires local Keystore migration support")
                crypto.replaceDatabasePassphraseForRestore(decryptedBackup.databasePassphrase)
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
            decryptedBackup.databasePassphrase?.fill(0)
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
         * Older releases could leave swap artifacts without a journal. Keep an existing live
         * database/vault authoritative; if a live target is absent, recover only from one
         * unambiguous complete backup. Never fail every app launch merely because stale artifacts
         * exist, and never guess between multiple possible vault backups.
         */
        private fun cleanupSnapshotArtifacts(snapshotRoot: File, token: String) {
            require(runCatching { UUID.fromString(token).toString() }.getOrNull() == token) {
                "Refusing to clean restore artifacts for an invalid token"
            }
            val root = snapshotRoot.canonicalFile
            val names = setOf(
                "$token-db",
                "$token-db-wal",
                "$token-vault",
                "$token-key-state",
                JOURNAL_FILE,
                "$JOURNAL_FILE.tmp"
            )
            root.listFiles().orEmpty()
                .filter { it.name in names && it.canonicalFile.parentFile == root }
                .forEach { file ->
                    val removed = if (file.isDirectory) file.deleteRecursively() else file.delete()
                    if (!removed && file.exists()) {
                        throw IllegalStateException("Unable to clean restore transaction artifact: ${file.name}")
                    }
                }
        }

        /**
         * A crash before the journal is published can leave transaction-named snapshots behind.
         * Remove only known UUID-scoped artifacts directly inside the snapshot root; preserve
         * unrelated files and reject paths whose canonical parent escapes the root.
         */
        private fun cleanupOrphanedSnapshotArtifacts(snapshotRoot: File) {
            val root = snapshotRoot.canonicalFile
            val transactionArtifact = Regex(
                "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[1-8][0-9a-fA-F]{3}-[89abAB][0-9a-fA-F]{3}-[0-9a-fA-F]{12}-(db-wal|db|vault|key-state)$"
            )
            root.listFiles().orEmpty()
                .filter { file ->
                    (transactionArtifact.matches(file.name) || file.name == "$JOURNAL_FILE.tmp") &&
                        file.canonicalFile.parentFile == root
                }
                .forEach { file ->
                    val removed = if (file.isDirectory) file.deleteRecursively() else file.delete()
                    if (!removed && file.exists()) {
                        throw IllegalStateException("Unable to clean orphaned restore artifact: ${file.name}")
                    }
                }
        }

        private fun filesHaveSameContents(first: File, second: File): Boolean {
            if (!first.isFile || !second.isFile || first.length() != second.length()) return false

            fun digest(file: File): ByteArray {
                val sha256 = MessageDigest.getInstance("SHA-256")
                file.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val count = input.read(buffer)
                        if (count < 0) break
                        sha256.update(buffer, 0, count)
                    }
                    buffer.fill(0)
                }
                return sha256.digest()
            }

            return digest(first).contentEquals(digest(second))
        }

        private fun recoverLegacySwapArtifacts(liveDatabaseFile: File, liveVaultDir: File) {
            val parent = liveDatabaseFile.parentFile
                ?: throw IllegalStateException("Legacy restore recovery has no parent directory")
            val db = liveDatabaseFile.name
            val vault = liveVaultDir.name
            // A process death during legacy recovery can leave partial staging copies. They are
            // never authoritative; the original .restore-bak sources remain until both targets
            // have been published, so retire only UUID-scoped recovery staging first.
            val recoveryStageName = Regex("^\\.legacy-recovery-(db|vault)-[0-9a-fA-F-]{36}$")
            parent.listFiles().orEmpty()
                .filter { recoveryStageName.matches(it.name) }
                .forEach { staged ->
                    val removed = if (staged.isDirectory) staged.deleteRecursively() else staged.delete()
                    if (!removed && staged.exists()) {
                        throw IllegalStateException("Unable to remove interrupted legacy recovery staging: ${staged.name}")
                    }
                }
            val entries = parent.listFiles().orEmpty()
            val databaseBackup = File(parent, "$db.restore-bak")
            val vaultBackupArtifacts = entries.filter { it.name.startsWith("$vault.restore-bak-") }
            val vaultBackups = vaultBackupArtifacts.filter { it.isDirectory }

            // Without a journal, an existing live DB plus any old swap backup is ambiguous.
            // The prior implementation treated the live DB as authoritative when no journal
            // existed. Keep all artifacts for later/manual recovery, but do not crash-loop the app
            // on every launch or combine the live DB with a potentially mismatched vault backup.
            if (liveDatabaseFile.exists() &&
                (databaseBackup.exists() || vaultBackupArtifacts.isNotEmpty())
            ) {
                // Resume the narrow legacy crash window where recovery already copied the DB
                // from its rollback backup but died before publishing the matching vault. Only
                // pair the vault backup when the live DB is byte-identical to that backup.
                val canResumeVaultPublication =
                    !liveVaultDir.exists() &&
                        databaseBackup.isFile &&
                        databaseBackup.length() > 0L &&
                        vaultBackupArtifacts.size == 1 &&
                        vaultBackups.size == 1 &&
                        filesHaveSameContents(liveDatabaseFile, databaseBackup)
                if (!canResumeVaultPublication) return
            }

            if (!liveDatabaseFile.exists() && (!databaseBackup.isFile || databaseBackup.length() == 0L)) {
                throw IllegalStateException(
                    "Legacy restore artifacts exist but no non-empty live database or complete database backup is available"
                )
            }
            if (!liveVaultDir.exists() && vaultBackupArtifacts.any { !it.isDirectory }) {
                throw IllegalStateException(
                    "Legacy restore left a malformed vault backup and no live vault; preserving artifacts for manual recovery"
                )
            }
            if (!liveVaultDir.exists() && vaultBackups.size > 1) {
                throw IllegalStateException(
                    "Legacy restore left multiple vault backups and no live vault; refusing ambiguous recovery"
                )
            }

            // Recover the vault first, then the database. If the process dies between these
            // atomic publications, the missing database causes the next launch to retry recovery
            // instead of treating a newly published database with a missing vault as authoritative.
            if (!liveVaultDir.exists() && vaultBackups.size == 1) {
                val backup = vaultBackups.single()
                val stagedVault = File(parent, ".legacy-recovery-vault-${UUID.randomUUID()}")
                try {
                    backup.copyRecursively(stagedVault, overwrite = false)
                    if (!stagedVault.isDirectory) {
                        throw IllegalStateException("Legacy vault backup recovery failed")
                    }
                    if (!stagedVault.renameTo(liveVaultDir)) {
                        throw IllegalStateException("Unable to atomically publish recovered legacy vault")
                    }
                } finally {
                    if (stagedVault.exists() && !stagedVault.deleteRecursively()) {
                        throw IllegalStateException("Unable to remove legacy vault recovery staging")
                    }
                }
            }
            if (!liveDatabaseFile.exists()) {
                val stagedDatabase = File(parent, ".legacy-recovery-db-${UUID.randomUUID()}")
                try {
                    databaseBackup.copyTo(stagedDatabase, overwrite = false)
                    if (stagedDatabase.length() != databaseBackup.length()) {
                        throw IllegalStateException("Legacy database backup recovery size mismatch")
                    }
                    if (!stagedDatabase.renameTo(liveDatabaseFile)) {
                        throw IllegalStateException("Unable to atomically publish recovered legacy database")
                    }
                } finally {
                    if (stagedDatabase.exists() && !stagedDatabase.delete()) {
                        throw IllegalStateException("Unable to remove legacy database recovery staging")
                    }
                }
            }

            parent.listFiles().orEmpty()
                .filter { file ->
                    file.name == "$db.restore-bak" ||
                        file.name.startsWith("$db.restore-tmp") ||
                        file.name.startsWith("$db.rollback-") ||
                        file.name.startsWith("$db-wal.rollback-") ||
                        file.name.startsWith("$vault.restore-bak-") ||
                        file.name.startsWith("$vault.restore-tmp-")
                }
                .forEach { file ->
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
            if (!snapshotRoot.exists()) {
                if (hasInterruptedSwapArtifacts(liveDatabaseFile, liveVaultDir)) {
                    recoverLegacySwapArtifacts(liveDatabaseFile, liveVaultDir)
                }
                return
            }
            val journal = File(snapshotRoot, JOURNAL_FILE)
            if (!journal.isFile) {
                if (hasInterruptedSwapArtifacts(liveDatabaseFile, liveVaultDir)) {
                    // Legacy swap artifacts may be the only surviving rollback material. Do not
                    // also delete orphaned snapshot files when recovery had no journal to map them.
                    recoverLegacySwapArtifacts(liveDatabaseFile, liveVaultDir)
                    return
                }
                cleanupOrphanedSnapshotArtifacts(snapshotRoot)
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
            cleanupSnapshotArtifacts(snapshotRoot, token)
            cleanupOrphanedSnapshotArtifacts(snapshotRoot)
        }
    }
}
