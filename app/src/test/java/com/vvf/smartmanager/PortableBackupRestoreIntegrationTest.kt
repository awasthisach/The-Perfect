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
    fun portableArchiveRestoresDatabaseKeyAndRewrapsVaultFiles() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val crypto = CryptoSecurityManager(context, "Robolectric")
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
        val archive = service.createArchive(
            includeVault = true,
            includeDatabase = true,
            passphrase = "portable test passphrase 2026".toCharArray()
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
        restoredVaultPlain.delete()
        archive.file.delete()
        restoreStage.deleteRecursively()
    }
}
