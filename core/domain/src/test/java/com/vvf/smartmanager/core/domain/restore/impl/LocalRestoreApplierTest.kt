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
import java.io.File

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
    fun processRestartRecoversFilesSidecarsAndAuthAfterInterruptedPublication() = runBlocking {
        val db = temp.newFile("recover.db").apply { writeText("old-db") }
        File(db.path + "-wal").writeText("old-wal")
        val vault = temp.newFolder("recover-vault").also { File(it, "old").writeText("old-vault") }
        val snapshots = temp.newFolder("recover-snapshots")
        var auth = mapOf("vault_pin_hash" to "old-wrapped-pin")
        val original = LocalRestoreApplier(db, vault, snapshots,
            vaultAuthExporter = { auth }, vaultAuthRestorer = { auth = it; true })
        original.prepare().getOrThrow()
        db.writeText("partially-published-db")
        File(db.path + "-wal").writeText("new-wal")
        File(vault, "old").delete()
        File(vault, "new").writeText("new-vault")
        auth = mapOf("vault_pin_hash" to "new-wrapped-pin")
        val restarted = LocalRestoreApplier(db, vault, snapshots, vaultAuthRestorer = { auth = it; true })
        restarted.recoverInterrupted().getOrThrow()
        assertEquals("old-db", db.readText())
        assertEquals("old-wal", File(db.path + "-wal").readText())
        assertEquals("old-vault", File(vault, "old").readText())
        assertFalse(File(vault, "new").exists())
        assertEquals("old-wrapped-pin", auth["vault_pin_hash"])
        assertFalse(File(snapshots, "pending.properties").exists())
        assertTrue(restarted.recoverInterrupted().isSuccess)
    }

    @Test
    fun missingUndoFileBlocksRecoveryAndRetainsJournal() = runBlocking {
        val db = temp.newFile("missing-undo.db").apply { writeText("keep") }
        val vault = temp.newFolder("missing-undo-vault")
        val snapshots = temp.newFolder("missing-undo-snapshots")
        val applier = LocalRestoreApplier(db, vault, snapshots)
        val snapshot = applier.prepare().getOrThrow()
        snapshot.databaseSnapshotPath.delete()
        assertTrue(applier.recoverInterrupted().isFailure)
        assertEquals("keep", db.readText())
        assertTrue(File(snapshots, "pending.properties").isFile)
    }

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
            schemaVersion = 1,
            includesVaultAuth = false
        )
        File(staging, "metadata.json").writeText(json.encodeToString(meta))

        val applier = LocalRestoreApplier(
            liveDatabaseFile = liveDb,
            liveVaultDir = liveVault,
            snapshotRoot = temp.newFolder("snaps")
        )
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
    fun authImportFailureLeavesLiveUnchanged() = runBlocking {
        val liveDb = temp.newFile("live3.db").apply { writeText("LIVE") }
        val liveVault = temp.newFolder("live-vault-3").also { File(it, "x.bin").writeText("X") }
        val staging = temp.newFolder("staging3")
        val vault = File(staging, "vault").also { it.mkdirs(); File(it, "v.bin").writeText("V") }
        val dbFile = File(staging, "db").apply { writeText("DB") }
        File(staging, "vault_auth.json").writeText("{\"vault_pin_hash\":\"abc\"}")
        val meta = ArchiveMetadata(
            version = 1,
            timestamp = 1L,
            checksumSha256 = "",
            databaseSizeBytes = 2L,
            vaultSizeBytes = 1L,
            fileCount = 3,
            vaultItemCount = 1,
            appVersion = "test",
            schemaVersion = 1,
            includesVaultAuth = true
        )
        File(staging, "metadata.json").writeText(json.encodeToString(meta))

        val applier = LocalRestoreApplier(
            liveDatabaseFile = liveDb,
            liveVaultDir = liveVault,
            snapshotRoot = temp.newFolder("snaps3"),
            vaultAuthImporter = { false }
        )
        val result = applier.apply(
            DecryptedBackup(
                stagingDir = staging,
                databaseFile = dbFile,
                vaultDir = vault,
                backupInfo = sampleInfo,
                timestamp = 1L
            )
        )
        assertTrue(result.isFailure)
        assertEquals("LIVE", liveDb.readText())
        assertEquals("X", File(liveVault, "x.bin").readText())
    }
    @Test
    fun vaultOnlyRestorePreservesLiveDatabase() = runBlocking {
        val liveDb = temp.newFile("live-vault-only.db").apply { writeText("KEEP-DB") }
        val liveVault = temp.newFolder("live-vault-only").also { File(it, "old.bin").writeText("OLD") }
        val staging = temp.newFolder("staging-vault-only")
        val vault = File(staging, "vault").also { it.mkdirs(); File(it, "new.bin").writeText("NEW") }
        val missingDb = File(staging, "database/missing.db")
        val meta = ArchiveMetadata(
            version = 1, timestamp = 1L, checksumSha256 = "",
            databaseSizeBytes = 0L, vaultSizeBytes = 3L,
            fileCount = 1, vaultItemCount = 1, appVersion = "test", schemaVersion = 1,
            includesDatabase = false, includesVault = true
        )
        File(staging, "metadata.json").writeText(json.encodeToString(meta))

        val applier = LocalRestoreApplier(liveDb, liveVault, temp.newFolder("snap-vault-only"))
        val result = applier.apply(
            DecryptedBackup(staging, missingDb, vault, sampleInfo, 1L)
        )

        assertTrue(result.isSuccess)
        assertEquals("KEEP-DB", liveDb.readText())
        assertTrue(File(liveVault, "new.bin").isFile)
        assertFalse(File(liveVault, "old.bin").exists())
    }

    @Test
    fun databaseOnlyRestorePreservesLiveVault() = runBlocking {
        val liveDb = temp.newFile("live-db-only.db").apply { writeText("OLD-DB") }
        val liveVault = temp.newFolder("live-db-only-vault").also { File(it, "keep.bin").writeText("KEEP") }
        val staging = temp.newFolder("staging-db-only")
        val db = File(staging, "database.db").apply { writeText("NEW-DB") }
        val vault = File(staging, "vault").also { it.mkdirs() }
        val meta = ArchiveMetadata(
            version = 1, timestamp = 1L, checksumSha256 = "",
            databaseSizeBytes = 6L, vaultSizeBytes = 0L,
            fileCount = 1, vaultItemCount = 0, appVersion = "test", schemaVersion = 1,
            includesDatabase = true, includesVault = false
        )
        File(staging, "metadata.json").writeText(json.encodeToString(meta))

        val applier = LocalRestoreApplier(liveDb, liveVault, temp.newFolder("snap-db-only"))
        val result = applier.apply(
            DecryptedBackup(staging, db, vault, sampleInfo, 1L)
        )

        assertTrue(result.isSuccess)
        assertEquals("NEW-DB", liveDb.readText())
        assertTrue(File(liveVault, "keep.bin").isFile)
        assertEquals("KEEP", File(liveVault, "keep.bin").readText())
    }

    @Test
    fun manifestDeclaringMissingDatabaseFailsWithoutMutatingLive() = runBlocking {
        val liveDb = temp.newFile("live-missing-db.db").apply { writeText("KEEP-DB") }
        val liveVault = temp.newFolder("live-missing-db-vault").also { File(it, "keep.bin").writeText("KEEP") }
        val staging = temp.newFolder("staging-missing-db")
        val vault = File(staging, "vault").also { it.mkdirs(); File(it, "new.bin").writeText("NEW") }
        val meta = ArchiveMetadata(
            version = 1, timestamp = 1L, checksumSha256 = "",
            databaseSizeBytes = 5L, vaultSizeBytes = 3L,
            fileCount = 1, vaultItemCount = 1, appVersion = "test", schemaVersion = 1,
            includesDatabase = true, includesVault = true
        )
        File(staging, "metadata.json").writeText(json.encodeToString(meta))

        val applier = LocalRestoreApplier(liveDb, liveVault, temp.newFolder("snap-missing-db"))
        val result = applier.apply(
            DecryptedBackup(staging, File(staging, "database/missing.db"), vault, sampleInfo, 1L)
        )

        assertTrue(result.isFailure)
        assertEquals("KEEP-DB", liveDb.readText())
        assertTrue(File(liveVault, "keep.bin").isFile)
        assertFalse(File(liveVault, "new.bin").exists())
    }

    @Test
    fun rollbackRemovesComponentsThatDidNotExistBeforeRestore() = runBlocking {
        val root = temp.newFolder("rollback-absent")
        val liveDb = File(root, "previously-absent.db")
        val liveVault = File(root, "previously-absent-vault")
        val applier = LocalRestoreApplier(
            liveDatabaseFile = liveDb,
            liveVaultDir = liveVault,
            snapshotRoot = File(root, "snapshots")
        )

        val snapshot = applier.prepare().getOrThrow()
        liveDb.writeText("FAILED-RESTORE-DB")
        liveVault.mkdirs()
        File(liveVault, "failed.bin").writeText("FAILED-RESTORE-VAULT")

        assertTrue(applier.rollback(snapshot).isSuccess)
        assertFalse(liveDb.exists())
        assertFalse(liveVault.exists())
    }

}
