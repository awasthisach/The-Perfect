package com.vvf.smartmanager.core.domain.restore.impl

import com.vvf.smartmanager.core.domain.restore.VerifiedArtifact
import com.vvf.smartmanager.core.model.CloudBackupInfo
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import com.vvf.smartmanager.core.security.PortableBackupCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.mock
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class CryptoBackupDecryptorTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun portableDatabaseArchiveExtractsKeyMaterialAndConsumesPassphrase() {
        val root = temp.newFolder("portable-decrypt")
        val payload = File(root, "payload.zip")
        val expectedDatabase = "SQLCIPHER-CIPHERTEXT".toByteArray()
        val expectedPassphrase = ByteArray(32) { (it + 1).toByte() }
        ZipOutputStream(payload.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("database/vvf_smart_manager_enc.db"))
            zip.write(expectedDatabase)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("key-material/database-passphrase.bin"))
            zip.write(expectedPassphrase)
            zip.closeEntry()
        }

        val encrypted = File(root, "portable.vvfbak")
        PortableBackupCrypto().encryptFile(payload, encrypted, "a sufficiently long backup passphrase".toCharArray())
        val info = CloudBackupInfo(
            backupId = "remote-portable",
            timestamp = 1L,
            backupName = "portable.vvfbak",
            backupSizeBytes = encrypted.length(),
            includesVault = false,
            includesDatabase = true,
            includesPreferences = false,
            checksumSha256 = "verified-by-caller"
        )
        val passphrase = "a sufficiently long backup passphrase".toCharArray()
        val staging = File(root, "restore-stage").apply { mkdirs() }

        val result = kotlinx.coroutines.runBlocking {
            CryptoBackupDecryptor(mock<CryptoSecurityManager>()).decrypt(
                VerifiedArtifact(encrypted, info, "verified-by-caller"),
                staging,
                passphrase
            )
        }

        assertTrue("Portable archive should decrypt", result.isSuccess)
        val decrypted = result.getOrThrow()
        assertArrayEquals(expectedDatabase, decrypted.databaseFile.readBytes())
        assertArrayEquals(expectedPassphrase, decrypted.databasePassphraseFile!!.readBytes())
        assertEquals("Portable key material must be exactly 32 bytes", 32L, decrypted.databasePassphraseFile.length())
        assertTrue("Decryptor must consume the passphrase", passphrase.all { it == 0.toChar() })
    }

    @Test
    fun portableArchiveMissingDatabaseKeyMaterialFailsClosed() {
        val root = temp.newFolder("portable-missing-key")
        val payload = File(root, "payload.zip")
        ZipOutputStream(payload.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("database/db.enc"))
            zip.write(byteArrayOf(1, 2, 3))
            zip.closeEntry()
        }
        val encrypted = File(root, "portable.vvfbak")
        PortableBackupCrypto().encryptFile(payload, encrypted, "a sufficiently long backup passphrase".toCharArray())
        val info = CloudBackupInfo(
            backupId = "remote-portable",
            timestamp = 1L,
            backupName = "portable.vvfbak",
            backupSizeBytes = encrypted.length(),
            includesVault = false,
            includesDatabase = true,
            includesPreferences = false,
            checksumSha256 = "verified-by-caller"
        )
        val staging = File(root, "restore-stage").apply { mkdirs() }
        val result = kotlinx.coroutines.runBlocking {
            CryptoBackupDecryptor(mock<CryptoSecurityManager>()).decrypt(
                VerifiedArtifact(encrypted, info, "verified-by-caller"),
                staging,
                "a sufficiently long backup passphrase".toCharArray()
            )
        }

        assertTrue("Missing SQLCipher key material must reject portable restore", result.isFailure)
    }
}
