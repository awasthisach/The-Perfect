package com.vvf.smartmanager

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.vvf.smartmanager.core.data.backup.InjectedVaultSnapshotSource
import com.vvf.smartmanager.core.data.backup.ReadOnlyDatabaseSnapshotSource
import com.vvf.smartmanager.core.domain.backup.ArchiveService
import com.vvf.smartmanager.core.domain.restore.impl.CryptoBackupDecryptor
import com.vvf.smartmanager.core.domain.restore.impl.LocalRestoreApplier
import com.vvf.smartmanager.core.domain.restore.VerifiedArtifact
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.rules.TemporaryFolder
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PortableBackupRestoreIntegrationTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun interruptedRestoreRecoversDatabaseVaultAndWrappedKeyBeforeStartup() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = CryptoSecurityManager(context, "Robolectric")
        val liveDatabase = temp.newFile("interrupted-live.db").apply { writeText("ORIGINAL-DB") }
        val liveVault = temp.newFolder("interrupted-live-vault").also {
            File(it, "original.bin").writeText("ORIGINAL-VAULT")
        }
        val snapshotRoot = temp.newFolder("interrupted-snapshots")
        val applier = LocalRestoreApplier(
            liveDatabaseFile = liveDatabase,
            liveVaultDir = liveVault,
            snapshotRoot = snapshotRoot,
            cryptoSecurityManager = crypto
        )
        val originalPassphrase = crypto.getOrCreateDatabasePassphrase()
        val snapshot = applier.prepare().getOrThrow()
        liveDatabase.writeText("PARTIAL-DB")
        File(liveVault, "original.bin").writeText("PARTIAL-VAULT")
        val replacement = ByteArray(32) { (it + 11).toByte() }
        crypto.replaceDatabasePassphraseForRestore(replacement)

        LocalRestoreApplier.recoverInterruptedRestore(
            liveDatabaseFile = liveDatabase,
            liveVaultDir = liveVault,
            snapshotRoot = snapshotRoot,
            cryptoSecurityManager = crypto
        )

        assertEquals("ORIGINAL-DB", liveDatabase.readText())
        assertEquals("ORIGINAL-VAULT", File(liveVault, "original.bin").readText())
        val restoredPassphrase = crypto.getOrCreateDatabasePassphrase()
        assertArrayEquals(originalPassphrase, restoredPassphrase)
        assertTrue("Recovery must clear the active journal", !File(snapshotRoot, "active-restore.properties").exists())
        assertTrue("Recovery must remove consumed rollback snapshots", snapshotRoot.listFiles().orEmpty().isEmpty())
        originalPassphrase.fill(0)
        restoredPassphrase.fill(0)
        replacement.fill(0)
    }

    @Test
    fun portableArchiveRestoresDatabaseKeyAndRewrapsVaultFiles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = CryptoSecurityManager(context, "Robolectric")
        assertTrue("Test must configure a real vault PIN", crypto.saveVaultPin("1234"))
        val sourceDatabase = temp.newFile("source.db").apply { writeText("SQLCIPHER-DATABASE-CIPHERTEXT") }
        val sourceVault = temp.newFolder("source-vault")
        val sourceVaultPlain = temp.newFile("vault-plain.txt").apply { writeText("vault secret payload") }
        val sourceVaultCipher = File(sourceVault, "enc_fixture.vvf")
        crypto.encryptFile(sourceVaultPlain, sourceVaultCipher)
        sourceVaultPlain.delete()

        val service = ArchiveService(
            cacheDir = temp.newFolder("archive-cache"),
            snapshotSources = listOf(
                ReadOnlyDatabaseSnapshotSource(sourceDatabase),
                InjectedVaultSnapshotSource(sourceVault)
            ),
            cryptoSecurityManager = crypto
        )
        val originalPassphrase = crypto.getOrCreateDatabasePassphrase()
        assertTrue("Portable backup must fail closed without a passphrase", service.createArchive(
            includeVault = true,
            includeDatabase = true
        ).isFailure)
        assertTrue("Vault export must fail closed for an incorrect PIN", service.createArchive(
            includeVault = true,
            includeDatabase = true,
            passphrase = "portable test passphrase 2026".toCharArray(),
            vaultPin = "9999"
        ).isFailure)
        val archive = service.createArchive(
            includeVault = true,
            includeDatabase = true,
            passphrase = "portable test passphrase 2026".toCharArray(),
            vaultPin = "1234"
        ).getOrThrow()

        val restoreStage = temp.newFolder("restore-stage")
        val decrypted = CryptoBackupDecryptor(crypto).decrypt(
            VerifiedArtifact(archive.file, archive.backupInfo, archive.checksumSha256),
            restoreStage,
            "portable test passphrase 2026".toCharArray()
        ).getOrThrow()

        val bundledPassphrase = decrypted.databasePassphraseFile!!.readBytes()
        assertArrayEquals(originalPassphrase, bundledPassphrase)
        bundledPassphrase.fill(0)

        // Simulate a destination device whose currently wrapped DB passphrase differs from the archive.
        val destinationPassphrase = ByteArray(32) { (it + 101).toByte() }
        crypto.replaceDatabasePassphraseForRestore(destinationPassphrase)
        val liveDatabase = temp.newFile("live.db").apply { writeText("OLD-DATABASE") }
        val liveVault = temp.newFolder("live-vault")
        val oldVaultPlain = temp.newFile("old-vault.txt").apply { writeText("old vault item") }
        crypto.encryptFile(oldVaultPlain, File(liveVault, "enc_old.vvf"))
        oldVaultPlain.delete()
        val applier = LocalRestoreApplier(
            liveDatabaseFile = liveDatabase,
            liveVaultDir = liveVault,
            snapshotRoot = temp.newFolder("rollback-snapshots"),
            cryptoSecurityManager = crypto
        )
        applier.prepare().getOrThrow()
        val applied = applier.apply(decrypted).getOrThrow()

        assertTrue(applied.success)
        assertEquals("SQLCIPHER-DATABASE-CIPHERTEXT", liveDatabase.readText())
        assertTrue(File(liveVault, "enc_fixture.vvf").isFile)
        val restoredVaultPlain = File(temp.root, "restored-vault-plain.txt")
        crypto.decryptFile(File(liveVault, "enc_fixture.vvf"), restoredVaultPlain)
        assertEquals("vault secret payload", restoredVaultPlain.readText())

        val currentPassphrase = crypto.getOrCreateDatabasePassphrase()
        assertArrayEquals(originalPassphrase, currentPassphrase)
        currentPassphrase.fill(0)
        originalPassphrase.fill(0)
        destinationPassphrase.fill(0)
        restoredVaultPlain.delete()
        archive.file.delete()
        restoreStage.deleteRecursively()
    }
}
