package com.vvf.smartmanager.core.domain.backup

import com.vvf.smartmanager.core.data.backup.SnapshotSource
import com.vvf.smartmanager.core.domain.restore.VerifiedArtifact
import com.vvf.smartmanager.core.domain.restore.impl.CryptoBackupDecryptor
import com.vvf.smartmanager.core.domain.restore.impl.LocalRestoreApplier
import com.vvf.smartmanager.core.model.CloudBackupInfo
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.argThat
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.io.File

class PortableBackupRestoreIntegrationTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun portableArchiveRestoresDatabaseAndReencryptsVaultWithDestinationKey() = runBlocking {
        val root = temp.newFolder("portable-restore-e2e")
        val cache = File(root, "cache").apply { mkdirs() }
        val sourceDatabaseBytes = "SQLCIPHER-DATABASE-CIPHERTEXT".toByteArray()
        val sourceDatabase = File(root, "source.db").apply { writeBytes(sourceDatabaseBytes) }
        val sourceVault = File(root, "source-vault").apply {
            mkdirs()
            File(this, "enc_item.vvf").writeText("SOURCE-DEVICE-CIPHERTEXT")
        }
        val destinationPassphrase = ByteArray(32) { (it + 11).toByte() }
        val crypto = mock<CryptoSecurityManager>()
        whenever(crypto.getOrCreateDatabasePassphrase()).thenReturn(destinationPassphrase.clone())
        whenever(crypto.isVaultConfigured()).thenReturn(true)
        whenever(crypto.verifyVaultPin("1234")).thenReturn(true)
        whenever(crypto.decryptFile(any(), any())).thenAnswer { invocation ->
            invocation.getArgument<File>(1).writeText("PORTABLE-VAULT-PLAINTEXT")
            "PORTABLE-VAULT-PLAINTEXT".length.toLong()
        }
        whenever(crypto.encryptFile(any(), any())).thenAnswer { invocation ->
            val source = invocation.getArgument<File>(0)
            invocation.getArgument<File>(1).writeText("DESTINATION-KEY-CIPHERTEXT")
            CryptoSecurityManager.StreamEncryptionResult("test-iv", source.length())
        }
        whenever(crypto.secureShredFile(any())).thenAnswer { invocation ->
            val file = invocation.getArgument<File>(0)
            if (!file.exists()) true else {
                if (file.isFile && file.length() > 0L) {
                    file.writeBytes(ByteArray(file.length().toInt()))
                }
                file.delete()
            }
        }

        val databaseSource = object : SnapshotSource {
            override val sourceName: String = "database"
            override fun snapshot(stagingDir: File): File =
                File(stagingDir, "database/vvf_smart_manager_enc.db").apply {
                    parentFile.mkdirs()
                    sourceDatabase.copyTo(this, overwrite = true)
                }
            override fun dataSizeBytes(): Long = sourceDatabase.length()
        }
        val vaultSource = object : SnapshotSource {
            override val sourceName: String = "vault"
            override fun snapshot(stagingDir: File): File =
                File(stagingDir, "vault").apply {
                    mkdirs()
                    sourceVault.listFiles().orEmpty().filter { it.isFile }.forEach { source ->
                        source.copyTo(File(this, source.name), overwrite = true)
                    }
                }
            override fun dataSizeBytes(): Long =
                sourceVault.walkTopDown().filter { it.isFile }.sumOf { it.length() }
        }

        val archive = ArchiveService(cache, listOf(databaseSource, vaultSource), crypto)
            .createArchive(
                includeDatabase = true,
                includeVault = true,
                passphrase = "a sufficiently long backup passphrase".toCharArray(),
                vaultPin = "1234"
            ).getOrThrow()

        val restoreStaging = File(root, "restore-staging").apply { mkdirs() }
        val decrypted = CryptoBackupDecryptor(crypto).decrypt(
            VerifiedArtifact(archive.file, archive.backupInfo, archive.checksumSha256),
            restoreStaging,
            "a sufficiently long backup passphrase".toCharArray()
        ).getOrThrow()

        val liveDatabase = File(root, "live/vvf_smart_manager_enc.db").apply {
            parentFile.mkdirs()
            writeText("OLD-DATABASE")
        }
        val liveVault = File(root, "live/vvf_vault_encrypted").apply {
            mkdirs()
            File(this, "old.vvf").writeText("OLD-VAULT-CIPHERTEXT")
        }
        val applier = LocalRestoreApplier(
            liveDatabaseFile = liveDatabase,
            liveVaultDir = liveVault,
            snapshotRoot = File(root, "snapshots"),
            cryptoSecurityManager = crypto
        )
        applier.prepare().getOrThrow()
        val applied = applier.apply(decrypted).getOrThrow()

        assertTrue("Restore transaction should apply", applied.success)
        assertArrayEquals(sourceDatabaseBytes, liveDatabase.readBytes())
        assertEquals(
            "DESTINATION-KEY-CIPHERTEXT",
            File(liveVault, "enc_item.vvf").readText()
        )
        assertTrue("Portable plaintext must not be present in the live vault", liveVault.walkTopDown().none {
            it.isFile && it.readText().contains("PORTABLE-VAULT-PLAINTEXT")
        })
        verify(crypto).replaceDatabasePassphraseForRestore(argThat { contentEquals(destinationPassphrase) })
    }
}
