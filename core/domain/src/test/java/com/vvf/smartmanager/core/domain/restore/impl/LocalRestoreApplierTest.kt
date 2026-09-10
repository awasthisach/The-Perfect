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
}
