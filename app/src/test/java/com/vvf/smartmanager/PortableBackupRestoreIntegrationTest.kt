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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
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
        val sourceCrypto = CryptoSecurityManager(context, "SourceDeviceKeyStore")
        val destinationCrypto = CryptoSecurityManager(context, "DestinationDeviceKeyStore")
        assertTrue("Test must configure a real vault PIN", sourceCrypto.saveVaultPin("1234"))
        val sourceDatabase = temp.newFile("source.db").apply { writeText("SQLCIPHER-DATABASE-CIPHERTEXT") }
        val sourceVault = temp.newFolder("source-vault")
        val sourceVaultPlain = temp.newFile("vault-plain.txt").apply { writeText("vault secret payload") }
        val sourceVaultCipher = File(sourceVault, "enc_fixture.vvf")
        sourceCrypto.encryptFile(sourceVaultPlain, sourceVaultCipher)
        sourceVaultPlain.delete()

        val service = ArchiveService(
            cacheDir = temp.newFolder("archive-cache"),
            snapshotSources = listOf(
                ReadOnlyDatabaseSnapshotSource(sourceDatabase),
                InjectedVaultSnapshotSource(sourceVault)
            ),
            cryptoSecurityManager = sourceCrypto
        )
        val originalPassphrase = sourceCrypto.getOrCreateDatabasePassphrase()
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

        // Emulate a second device: it has a different Keystore key and no source-wrapped DB key file.
        listOf("vvf_db_passphrase_v2.bin", "vvf_db_enc_passphrase.bin", "vvf_db_passphrase_iv.bin")
            .map { File(context.filesDir, it) }
            .forEach { file -> check(!file.exists() || file.delete()) { "Unable to reset simulated destination key state" } }
        val destinationPassphrase = destinationCrypto.getOrCreateDatabasePassphrase()
        assertTrue("Different devices must start with different wrapped database keys",
            !originalPassphrase.contentEquals(destinationPassphrase))

        // A new device must establish its own local PIN/key before restoring vault payloads.
        // This also isolates the simulated destination's PIN metadata from the source device.
        check(context.getSharedPreferences("vvf_secure_vault_prefs", Context.MODE_PRIVATE)
            .edit().clear().commit()) { "Unable to reset simulated destination vault state" }
        assertTrue("Destination must configure a local PIN before vault restore",
            destinationCrypto.saveVaultPin("5678"))

        val lockedRestoreStage = temp.newFolder("restore-locked-stage")
        destinationCrypto.lockVaultDataKey()
        val lockedRestore = CryptoBackupDecryptor(destinationCrypto).decrypt(
            VerifiedArtifact(archive.file, archive.backupInfo, archive.checksumSha256),
            lockedRestoreStage,
            "portable test passphrase 2026".toCharArray()
        )
        assertTrue("Vault restore must fail closed until a local PIN unlocks the destination key", lockedRestore.isFailure)
        assertFalse(destinationCrypto.isVaultDataKeyUnlocked())
        assertTrue("Correct local PIN must unlock the destination vault key",
            destinationCrypto.verifyVaultPin("5678"))

        val restoreStage = temp.newFolder("restore-stage")
        val decrypted = CryptoBackupDecryptor(destinationCrypto).decrypt(
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
        destinationCrypto.encryptFile(oldVaultPlain, File(liveVault, "enc_old.vvf"))
        oldVaultPlain.delete()
        val snapshotRoot = temp.newFolder("rollback-snapshots")
        val applier = LocalRestoreApplier(
            liveDatabaseFile = liveDatabase,
            liveVaultDir = liveVault,
            snapshotRoot = snapshotRoot,
            cryptoSecurityManager = destinationCrypto
        )

        // Force key import validation to fail after the DB/vault swaps; rollback must restore all live state.
        val failedSnapshot = applier.prepare().getOrThrow()
        val invalidKeyMaterial = temp.newFile("invalid-db-key.bin").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val failedApply = applier.apply(decrypted.copy(databasePassphraseFile = invalidKeyMaterial))
        assertTrue("Restore must fail closed on malformed imported DB key material", failedApply.isFailure)
        assertTrue("Rollback must recover the pre-restore DB/vault/key state", applier.rollback(failedSnapshot).isSuccess)
        assertEquals("OLD-DATABASE", liveDatabase.readText())
        assertTrue("Rollback must restore the old vault payload", File(liveVault, "enc_old.vvf").isFile)
        val keyAfterRollback = destinationCrypto.getOrCreateDatabasePassphrase()
        assertArrayEquals(destinationPassphrase, keyAfterRollback)
        keyAfterRollback.fill(0)
        invalidKeyMaterial.delete()

        applier.prepare().getOrThrow()
        val applied = applier.apply(decrypted).getOrThrow()

        assertTrue(applied.success)
        assertEquals("SQLCIPHER-DATABASE-CIPHERTEXT", liveDatabase.readText())
        assertTrue(File(liveVault, "enc_fixture.vvf").isFile)
        destinationCrypto.lockVaultDataKey()
        assertFalse("Restore completion must leave the in-memory vault key locked",
            destinationCrypto.isVaultDataKeyUnlocked())
        val restoredVaultPlain = File(temp.root, "restored-vault-plain.txt")
        assertThrows(SecurityException::class.java) {
            destinationCrypto.decryptFile(File(liveVault, "enc_fixture.vvf"), restoredVaultPlain)
        }
        assertTrue("The configured local PIN must unlock the restored vault",
            destinationCrypto.verifyVaultPin("5678"))
        destinationCrypto.decryptFile(File(liveVault, "enc_fixture.vvf"), restoredVaultPlain)
        assertEquals("vault secret payload", restoredVaultPlain.readText())
        val sourceDeviceDecrypt = runCatching {
            sourceCrypto.decryptFile(
                File(liveVault, "enc_fixture.vvf"),
                File(temp.root, "wrong-device-plaintext.txt")
            )
        }
        assertTrue("Restored vault ciphertext must not be decryptable by the source device key", sourceDeviceDecrypt.isFailure)
        File(temp.root, "wrong-device-plaintext.txt").delete()

        val currentPassphrase = destinationCrypto.getOrCreateDatabasePassphrase()
        assertArrayEquals(originalPassphrase, currentPassphrase)
        currentPassphrase.fill(0)
        originalPassphrase.fill(0)
        destinationPassphrase.fill(0)
        restoredVaultPlain.delete()
        archive.file.delete()
        restoreStage.deleteRecursively()
        Unit
    }
}
