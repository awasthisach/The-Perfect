package com.vvf.smartmanager.core.domain.restore.impl

import com.vvf.smartmanager.core.domain.backup.ArchiveMetadata
import com.vvf.smartmanager.core.domain.restore.DecryptedBackup
import com.vvf.smartmanager.core.model.CloudBackupInfo
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import java.io.File
import java.io.FileOutputStream
import java.util.Properties
import java.util.UUID

class LocalRestoreApplierTest {

    @get:Rule
    val temp = TemporaryFolder()

    private val json = Json { encodeDefaults = true }

    private val sampleInfo = CloudBackupInfo(
        backupId = "b1",
        timestamp = 1L,
        backupName = "t",
        backupSizeBytes = 1L,
        includesVault = true,
        includesDatabase = true,
        includesPreferences = false,
        checksumSha256 = "abc"
    )

    @Test
    fun incompleteManifestFailsWithoutMutatingLive() = runBlocking {
        val liveDb = temp.newFile("live.db").apply { writeText("LIVE-DB") }
        val liveVault = temp.newFolder("live-vault").also { File(it, "keep.bin").writeText("KEEP") }
        val staging = temp.newFolder("staging")
        val vault = File(staging, "vault").also { it.mkdirs(); File(it, "one.bin").writeText("1") }
        val db = File(staging, "database").also { it.mkdirs(); File(it, "db").writeText("NEW-DB") }
        val meta = ArchiveMetadata(
            version = 1,
            timestamp = 1L,
            checksumSha256 = "",
            databaseSizeBytes = 1L,
            vaultSizeBytes = 1L,
            fileCount = 10,
            vaultItemCount = 5,
            appVersion = "test",
            schemaVersion = 1
        )
        File(staging, "metadata.json").writeText(json.encodeToString(meta))

        val applier = LocalRestoreApplier(
            liveDatabaseFile = liveDb,
            liveVaultDir = liveVault,
            snapshotRoot = temp.newFolder("snaps")
        )
        applier.prepare().getOrThrow()
        val result = applier.apply(
            DecryptedBackup(
                stagingDir = staging,
                databaseFile = File(db, "db"),
                vaultDir = vault,
                backupInfo = sampleInfo,
                timestamp = 1L
            )
        )
        assertTrue(result.isFailure)
        assertEquals("LIVE-DB", liveDb.readText())
        assertTrue(File(liveVault, "keep.bin").isFile)
        assertEquals("KEEP", File(liveVault, "keep.bin").readText())
    }

    @Test
    fun successfulApplySwapsDbAndVault() = runBlocking {
        val liveDb = temp.newFile("live2.db").apply { writeText("OLD") }
        val liveVault = temp.newFolder("live-vault-2").also { File(it, "old.bin").writeText("OLDV") }
        val staging = temp.newFolder("staging2")
        val vault = File(staging, "vault").also { it.mkdirs(); File(it, "new.bin").writeText("NEWV") }
        val dbFile = File(staging, "dbfile").apply { writeText("NEWDB") }
        val meta = ArchiveMetadata(
            version = 1,
            timestamp = 1L,
            checksumSha256 = "",
            databaseSizeBytes = 5L,
            vaultSizeBytes = 4L,
            fileCount = 2,
            vaultItemCount = 1,
            appVersion = "test",
            schemaVersion = 1
        )
        File(staging, "metadata.json").writeText(json.encodeToString(meta))

        var beforeCalled = false
        val applier = LocalRestoreApplier(
            liveDatabaseFile = liveDb,
            liveVaultDir = liveVault,
            snapshotRoot = temp.newFolder("snaps2"),
            beforeApply = { beforeCalled = true }
        )
        applier.prepare().getOrThrow()
        val result = applier.apply(
            DecryptedBackup(
                stagingDir = staging,
                databaseFile = dbFile,
                vaultDir = vault,
                backupInfo = sampleInfo,
                timestamp = 1L
            )
        )
        assertTrue(result.isSuccess)
        assertTrue(beforeCalled)
        assertEquals("NEWDB", liveDb.readText())
        assertTrue(File(liveVault, "new.bin").isFile)
        assertEquals("NEWV", File(liveVault, "new.bin").readText())
        assertFalse(File(liveVault, "old.bin").exists())
    }

    @Test
    fun legacyVaultAuthMetadataIsIgnoredDuringRestore() = runBlocking {
        val liveDb = temp.newFile("live3.db").apply { writeText("LIVE") }
        val liveVault = temp.newFolder("live-vault-3").also { File(it, "x.bin").writeText("X") }
        val staging = temp.newFolder("staging3")
        val vault = File(staging, "vault").also { it.mkdirs(); File(it, "v.bin").writeText("V") }
        val dbFile = File(staging, "db").apply { writeText("DB") }
        File(staging, "vault_auth.json").writeText("{\"vault_pin_hash\":\"attacker-controlled\"}")
        val meta = ArchiveMetadata(
            version = 1,
            timestamp = 1L,
            checksumSha256 = "",
            databaseSizeBytes = 2L,
            vaultSizeBytes = 1L,
            fileCount = 3,
            vaultItemCount = 1,
            appVersion = "test",
            schemaVersion = 1
        )
        File(staging, "metadata.json").writeText(json.encodeToString(meta))

        val applier = LocalRestoreApplier(
            liveDatabaseFile = liveDb,
            liveVaultDir = liveVault,
            snapshotRoot = temp.newFolder("snaps3")
        )
        applier.prepare().getOrThrow()
        val result = applier.apply(
            DecryptedBackup(
                stagingDir = staging,
                databaseFile = dbFile,
                vaultDir = vault,
                backupInfo = sampleInfo,
                timestamp = 1L
            )
        )
        assertTrue("Restore should ignore legacy authentication metadata", result.isSuccess)
        assertEquals("DB", liveDb.readText())
        assertTrue(File(liveVault, "v.bin").isFile)
        assertFalse(File(liveVault, "x.bin").exists())
    }

    @Test
    fun databaseOnlyRestoreIsRejectedWhenLiveVaultHasFiles() = runBlocking {
        val liveDb = temp.newFile("database-only-live.db").apply { writeText("OLD-DB") }
        val liveVault = temp.newFolder("database-only-vault").also {
            File(it, "keep.bin").writeText("KEEP-VAULT")
        }
        val staging = temp.newFolder("database-only-staging")
        val dbFile = File(staging, "database/vvf.db").apply {
            parentFile.mkdirs()
            writeText("NEW-DB")
        }
        val info = sampleInfo.copy(includesVault = false, includesDatabase = true)
        val applier = LocalRestoreApplier(liveDb, liveVault, temp.newFolder("database-only-snaps"))

        applier.prepare().getOrThrow()
        val result = applier.apply(
            DecryptedBackup(
                stagingDir = staging,
                databaseFile = dbFile,
                vaultDir = File(staging, "vault").apply { mkdirs() },
                backupInfo = info,
                timestamp = 2L
            )
        )

        assertTrue("Database-only restore must fail closed when live vault files would become inconsistent", result.isFailure)
        assertEquals("OLD-DB", liveDb.readText())
        assertEquals("KEEP-VAULT", File(liveVault, "keep.bin").readText())
    }

    @Test
    fun vaultOnlyRestoreIsRejectedBecauseMetadataLivesInDatabase() = runBlocking {
        val liveDb = temp.newFile("vault-only-live.db").apply { writeText("KEEP-DB") }
        val liveVault = temp.newFolder("vault-only-live-vault").also {
            File(it, "old.bin").writeText("OLD-VAULT")
        }
        val staging = temp.newFolder("vault-only-staging")
        val vault = File(staging, "vault").also {
            it.mkdirs()
            File(it, "new.bin").writeText("NEW-VAULT")
        }
        val info = sampleInfo.copy(includesVault = true, includesDatabase = false)
        val applier = LocalRestoreApplier(liveDb, liveVault, temp.newFolder("vault-only-snaps"))

        applier.prepare().getOrThrow()
        val result = applier.apply(
            DecryptedBackup(
                stagingDir = staging,
                databaseFile = File(staging, "database"),
                vaultDir = vault,
                backupInfo = info,
                timestamp = 3L
            )
        )

        assertTrue("Vault-only restore must fail closed because metadata lives in the database", result.isFailure)
        assertEquals("KEEP-DB", liveDb.readText())
        assertEquals("OLD-VAULT", File(liveVault, "old.bin").readText())
        assertFalse(File(liveVault, "new.bin").exists())
    }

    @Test
    fun startupRecoveryRestoresDatabaseWalAndVaultFromInterruptedJournal() {
        val root = temp.newFolder("interrupted-restore")
        val snapshotRoot = File(root, "snapshots").apply { mkdirs() }
        val unrelated = File(snapshotRoot, "keep-unrelated.txt").apply { writeText("DO NOT DELETE") }
        val liveDb = File(root, "live.db").apply { writeText("PARTIAL-DB") }
        File(root, "live.db-wal").writeText("PARTIAL-WAL")
        val liveVault = File(root, "vault").also {
            it.mkdirs()
            File(it, "partial.bin").writeText("PARTIAL-VAULT")
        }
        val token = UUID.randomUUID().toString()
        File(snapshotRoot, "$token-db").writeText("ORIGINAL-DB")
        File(snapshotRoot, "$token-db-wal").writeText("ORIGINAL-WAL")
        File(snapshotRoot, "$token-vault").also {
            it.mkdirs()
            File(it, "original.bin").writeText("ORIGINAL-VAULT")
        }
        val journal = Properties().apply {
            setProperty("token", token)
            setProperty("timestamp", "1")
            setProperty("databaseExisted", "true")
            setProperty("vaultExisted", "true")
            setProperty("databaseWalExisted", "true")
            setProperty("keyStateExisted", "false")
        }
        FileOutputStream(File(snapshotRoot, "active-restore.properties")).use {
            journal.store(it, "simulated process-death restore journal")
        }

        LocalRestoreApplier.recoverInterruptedRestore(
            liveDatabaseFile = liveDb,
            liveVaultDir = liveVault,
            snapshotRoot = snapshotRoot,
            cryptoSecurityManager = mock()
        )

        assertEquals("ORIGINAL-DB", liveDb.readText())
        assertEquals("ORIGINAL-WAL", File(root, "live.db-wal").readText())
        assertEquals("ORIGINAL-VAULT", File(liveVault, "original.bin").readText())
        assertFalse("Partial vault must be removed", File(liveVault, "partial.bin").exists())
        assertFalse("Recovery journal must be cleared", File(snapshotRoot, "active-restore.properties").exists())
        assertTrue("Recovery must preserve unrelated files in the snapshot root", unrelated.isFile)
        assertEquals("DO NOT DELETE", unrelated.readText())
    }

    @Test
    fun rollbackRestoresDatabaseWalWithDatabase() = runBlocking {
        val root = temp.newFolder("restore-wal-state")
        val liveDb = File(root, "live.db").apply { writeText("ORIGINAL-DB") }
        val liveWal = File(root, "live.db-wal").apply { writeText("ORIGINAL-WAL") }
        val liveVault = File(root, "vault").also { it.mkdirs(); File(it, "item.bin").writeText("VAULT") }
        val applier = LocalRestoreApplier(liveDb, liveVault, File(root, "snapshots"))

        val snapshot = applier.prepare().getOrThrow()
        liveDb.writeText("PARTIAL-DB")
        liveWal.writeText("PARTIAL-WAL")

        val rollback = applier.rollback(snapshot)

        assertTrue("Rollback must restore database and WAL together", rollback.isSuccess)
        assertEquals("ORIGINAL-DB", liveDb.readText())
        assertEquals("ORIGINAL-WAL", liveWal.readText())
    }

    @Test
    fun missingWalRollbackSnapshotFailsBeforeMutatingDatabase() = runBlocking {
        val root = temp.newFolder("missing-wal-snapshot")
        val liveDb = File(root, "live.db").apply { writeText("ORIGINAL-DB") }
        File(root, "live.db-wal").writeText("ORIGINAL-WAL")
        val liveVault = File(root, "vault").also { it.mkdirs(); File(it, "item.bin").writeText("ORIGINAL-VAULT") }
        val applier = LocalRestoreApplier(liveDb, liveVault, File(root, "snapshots"))

        val snapshot = applier.prepare().getOrThrow()
        liveDb.writeText("PARTIAL-DB")
        snapshot.databaseWalSnapshotPath!!.delete()

        val rollback = applier.rollback(snapshot)

        assertTrue("Missing WAL recovery material must fail closed", rollback.isFailure)
        assertEquals("Preflight must leave the database untouched", "PARTIAL-DB", liveDb.readText())
    }

    @Test
    fun rollbackMissingVaultSnapshotFailsBeforeMutatingDatabase() = runBlocking {
        val root = temp.newFolder("missing-vault-snapshot")
        val liveDb = File(root, "live.db").apply { writeText("ORIGINAL-DB") }
        val liveVault = File(root, "vault").also {
            it.mkdirs()
            File(it, "original.bin").writeText("ORIGINAL-VAULT")
        }
        val applier = LocalRestoreApplier(
            liveDatabaseFile = liveDb,
            liveVaultDir = liveVault,
            snapshotRoot = File(root, "snapshots")
        )

        val snapshot = applier.prepare().getOrThrow()
        liveDb.writeText("PARTIAL-DB")
        File(liveVault, "original.bin").writeText("PARTIAL-VAULT")
        snapshot.vaultSnapshotPath.deleteRecursively()

        val rollback = applier.rollback(snapshot)

        assertTrue("Rollback must report missing recovery material", rollback.isFailure)
        assertEquals("Preflight must leave database untouched", "PARTIAL-DB", liveDb.readText())
        assertEquals("Preflight must leave vault untouched", "PARTIAL-VAULT", File(liveVault, "original.bin").readText())
    }

    @Test
    fun rollbackRestoresOriginalDatabaseAndVaultContents() = runBlocking {
        val root = temp.newFolder("restore-original-state")
        val liveDb = File(root, "live.db").apply { writeText("ORIGINAL-DB") }
        val liveVault = File(root, "vault").also {
            it.mkdirs()
            File(it, "original.bin").writeText("ORIGINAL-VAULT")
        }
        val applier = LocalRestoreApplier(
            liveDatabaseFile = liveDb,
            liveVaultDir = liveVault,
            snapshotRoot = File(root, "snapshots")
        )

        val snapshot = applier.prepare().getOrThrow()
        liveDb.writeText("PARTIAL-DB")
        liveVault.deleteRecursively()
        liveVault.mkdirs()
        File(liveVault, "partial.bin").writeText("PARTIAL-VAULT")

        val rollback = applier.rollback(snapshot)

        assertTrue("Rollback must succeed", rollback.isSuccess)
        assertEquals("ORIGINAL-DB", liveDb.readText())
        assertEquals("ORIGINAL-VAULT", File(liveVault, "original.bin").readText())
        assertFalse(File(liveVault, "partial.bin").exists())
    }

    @Test
    fun rollbackRemovesDatabaseAndVaultCreatedByFailedRestore() = runBlocking {
        val root = temp.newFolder("absent-state")
        val liveDb = File(root, "not-yet-created.db")
        val liveVault = File(root, "not-yet-created-vault")
        val applier = LocalRestoreApplier(
            liveDatabaseFile = liveDb,
            liveVaultDir = liveVault,
            snapshotRoot = File(root, "snapshots")
        )

        val snapshot = applier.prepare().getOrThrow()
        liveDb.writeText("PARTIAL-DB")
        liveVault.mkdirs()
        File(liveVault, "partial.bin").writeText("PARTIAL-VAULT")

        val rollback = applier.rollback(snapshot)

        assertTrue("Rollback must succeed", rollback.isSuccess)
        assertFalse("Database absent before restore must be removed", liveDb.exists())
        assertFalse("Vault absent before restore must be removed", liveVault.exists())
    }

    @Test
    fun rollbackRestoresPreviouslyAbsentVaultWithoutCreatingEmptyDirectory() = runBlocking {
        val root = temp.newFolder("absent-vault-state")
        val liveDb = File(root, "existing.db").apply { writeText("ORIGINAL-DB") }
        val liveVault = File(root, "absent-vault")
        val applier = LocalRestoreApplier(
            liveDatabaseFile = liveDb,
            liveVaultDir = liveVault,
            snapshotRoot = File(root, "snapshots")
        )

        val snapshot = applier.prepare().getOrThrow()
        liveDb.writeText("PARTIAL-DB")
        liveVault.mkdirs()
        File(liveVault, "partial.bin").writeText("PARTIAL")

        val rollback = applier.rollback(snapshot)

        assertTrue("Rollback must succeed", rollback.isSuccess)
        assertEquals("ORIGINAL-DB", liveDb.readText())
        assertFalse("Previously absent vault must remain absent", liveVault.exists())
    }


    @Test
    fun startupRecoveryPreservesAmbiguousLegacySwapArtifactsWithoutCrashLoop() {
        val root = temp.newFolder("legacy-orphaned-restore")
        val liveDb = File(root, "live.db").apply { writeText("LIVE-DB") }
        val databaseBackup = File(root, "live.db.restore-bak").apply { writeText("ROLLBACK-DB") }
        val tempDb = File(root, "live.db.restore-tmp-old").apply { writeText("STAGED-DB") }
        val liveVault = File(root, "vault").also {
            it.mkdirs()
            File(it, "live.bin").writeText("LIVE-VAULT")
        }
        val vaultBackup = File(root, "vault.restore-bak-old").also {
            it.mkdirs()
            File(it, "stale.bin").writeText("ROLLBACK-VAULT")
        }
        val snapshotRoot = File(root, "snapshots").apply { mkdirs() }
        val orphanedSnapshot = File(snapshotRoot, "${UUID.randomUUID()}-db").apply { writeText("POSSIBLE-ROLLBACK") }

        LocalRestoreApplier.recoverInterruptedRestore(
            liveDatabaseFile = liveDb,
            liveVaultDir = liveVault,
            snapshotRoot = snapshotRoot,
            cryptoSecurityManager = mock()
        )

        assertEquals("Live database must remain untouched", "LIVE-DB", liveDb.readText())
        assertEquals("Live vault must remain untouched", "LIVE-VAULT", File(liveVault, "live.bin").readText())
        assertTrue("Rollback DB must be preserved for recovery", databaseBackup.isFile)
        assertTrue("Staged DB must be preserved for diagnosis", tempDb.isFile)
        assertTrue("Rollback vault must be preserved for recovery", File(vaultBackup, "stale.bin").isFile)
        assertTrue("Unmapped snapshot must not be deleted without a recovery journal", orphanedSnapshot.isFile)
    }

    @Test
    fun recoveryWithoutJournalPreservesUnrelatedSnapshotRootFiles() {
        val root = temp.newFolder("unrelated-snapshot-data")
        val snapshotRoot = File(root, "snapshots").apply { mkdirs() }
        val unrelated = File(snapshotRoot, "user-data.bin").apply { writeText("KEEP") }
        val liveDb = File(root, "live.db").apply { writeText("LIVE-DB") }
        val liveVault = File(root, "vault").also { it.mkdirs() }

        LocalRestoreApplier.recoverInterruptedRestore(
            liveDatabaseFile = liveDb,
            liveVaultDir = liveVault,
            snapshotRoot = snapshotRoot,
            cryptoSecurityManager = mock()
        )

        assertTrue("Unrelated files must not be recursively deleted", unrelated.isFile)
        assertEquals("KEEP", unrelated.readText())
    }

    @Test
    fun startupRecoveryRejectsEmptyLegacyDatabaseBackupAndPreservesIt() {
        val root = temp.newFolder("legacy-empty-live-db")
        val backup = File(root, "live.db.restore-bak").apply { createNewFile() }
        val liveDb = File(root, "live.db")
        val liveVault = File(root, "vault").apply { mkdirs() }

        val result = runCatching {
            LocalRestoreApplier.recoverInterruptedRestore(
                liveDatabaseFile = liveDb,
                liveVaultDir = liveVault,
                snapshotRoot = File(root, "missing-snapshots"),
                cryptoSecurityManager = mock()
            )
        }

        assertTrue("Empty legacy database backup must not be published", result.isFailure)
        assertFalse("An empty database must not become live", liveDb.exists())
        assertTrue("Invalid recovery material must be preserved for diagnosis", backup.isFile)
    }

    @Test
    fun startupRecoveryDoesNotCrashLoopOnMalformedVaultBackupWhenLiveDatabaseExists() {
        val root = temp.newFolder("legacy-malformed-vault")
        val liveDb = File(root, "live.db").apply { writeText("LIVE-DB") }
        val liveVault = File(root, "vault")
        val malformedBackup = File(root, "vault.restore-bak-corrupt").apply { writeText("not a directory") }

        LocalRestoreApplier.recoverInterruptedRestore(
            liveDatabaseFile = liveDb,
            liveVaultDir = liveVault,
            snapshotRoot = File(root, "missing-snapshots"),
            cryptoSecurityManager = mock()
        )

        assertFalse("Malformed vault backup must not be published", liveVault.exists())
        assertTrue("Malformed recovery material must be preserved for manual recovery", malformedBackup.isFile)
        assertEquals("Live database must remain untouched", "LIVE-DB", liveDb.readText())
    }

    @Test
    fun startupRecoveryRestoresLegacyDatabaseBackupWhenLiveDatabaseIsMissing() {
        val root = temp.newFolder("legacy-missing-live-db")
        val backup = File(root, "live.db.restore-bak").apply { writeText("RECOVERABLE-DB") }
        val liveDb = File(root, "live.db")
        val liveVault = File(root, "vault")
        val snapshotRoot = File(root, "missing-snapshots")

        LocalRestoreApplier.recoverInterruptedRestore(
            liveDatabaseFile = liveDb,
            liveVaultDir = liveVault,
            snapshotRoot = snapshotRoot,
            cryptoSecurityManager = mock()
        )

        assertTrue("Database backup should be restored", liveDb.isFile)
        assertEquals("RECOVERABLE-DB", liveDb.readText())
        assertFalse("Consumed backup should be retired", backup.exists())
    }

}
