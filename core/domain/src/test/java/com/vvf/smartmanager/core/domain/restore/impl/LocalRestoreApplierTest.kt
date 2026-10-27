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
    fun databaseOnlyRestorePreservesLiveVault() = runBlocking {
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

        assertTrue(result.isSuccess)
        assertEquals("NEW-DB", liveDb.readText())
        assertEquals("KEEP-VAULT", File(liveVault, "keep.bin").readText())
    }

    @Test
    fun vaultOnlyRestorePreservesLiveDatabase() = runBlocking {
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

        assertTrue(result.isSuccess)
        assertEquals("KEEP-DB", liveDb.readText())
        assertEquals("NEW-VAULT", File(liveVault, "new.bin").readText())
        assertFalse(File(liveVault, "old.bin").exists())
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

}
