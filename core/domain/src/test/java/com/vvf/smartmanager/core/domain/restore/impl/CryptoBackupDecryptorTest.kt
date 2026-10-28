package com.vvf.smartmanager.core.domain.restore.impl

import com.vvf.smartmanager.core.domain.restore.VerifiedArtifact
import com.vvf.smartmanager.core.model.CloudBackupInfo
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import com.vvf.smartmanager.core.security.PortableBackupCrypto
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
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
    fun legacyRootLevelDatabaseLayoutIsRecognized() {
        val root = temp.newFolder("legacy-root-db")
        val payload = File(root, "payload.zip")
        val expectedDatabase = "LEGACY-ROOT-DB-CIPHERTEXT".toByteArray()
        val expectedPassphrase = ByteArray(32) { (it + 19).toByte() }
        val manifest = """{"version":3,"timestamp":1,"checksumSha256":"","databaseSizeBytes":0,"vaultSizeBytes":0,"fileCount":2,"vaultItemCount":0,"appVersion":"test","schemaVersion":1,"includesDatabase":true,"includesVault":false}"""
        ZipOutputStream(payload.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("vvf_smart_manager_enc.db"))
            zip.write(expectedDatabase)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("key-material/database-passphrase.bin"))
            zip.write(expectedPassphrase)
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("metadata.json"))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
        }
        val encrypted = File(root, "portable.vvfbak")
        PortableBackupCrypto().encryptFile(payload, encrypted, "a sufficiently long backup passphrase".toCharArray())
        val info = CloudBackupInfo(
            backupId = "remote-legacy-layout",
            timestamp = 1L,
            backupName = "legacy-layout.vvfbak",
            backupSizeBytes = encrypted.length(),
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

        assertTrue("Legacy root-level database layout should decrypt", result.isSuccess)
        assertEquals("vvf_smart_manager_enc.db", result.getOrThrow().databaseFile.name)
        assertArrayEquals(expectedDatabase, result.getOrThrow().databaseFile.readBytes())
        assertArrayEquals(expectedPassphrase, result.getOrThrow().databasePassphraseFile!!.readBytes())
    }

    @Test
    fun authenticatedManifestOverridesIncompleteProviderPayloadFlags() {
        val root = temp.newFolder("manifest-flags")
        val payload = File(root, "payload.zip")
        val manifest = """{"version":3,"timestamp":1,"checksumSha256":"","databaseSizeBytes":0,"vaultSizeBytes":0,"fileCount":0,"vaultItemCount":0,"appVersion":"test","schemaVersion":1,"includesDatabase":false,"includesVault":true}"""
        ZipOutputStream(payload.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("metadata.json"))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
        }
        val encrypted = File(root, "portable.vvfbak")
        PortableBackupCrypto().encryptFile(payload, encrypted, "a sufficiently long backup passphrase".toCharArray())
        // Provider-created metadata defaults to database=true/vault=false; authenticated archive metadata is authoritative.
        val providerInfo = CloudBackupInfo(
            backupId = "remote-portable",
            timestamp = 1L,
            backupName = "portable.vvfbak",
            backupSizeBytes = encrypted.length(),
            checksumSha256 = "verified-by-caller"
        )
        val staging = File(root, "restore-stage").apply { mkdirs() }
        val result = kotlinx.coroutines.runBlocking {
            CryptoBackupDecryptor(mock<CryptoSecurityManager>()).decrypt(
                VerifiedArtifact(encrypted, providerInfo, "verified-by-caller"),
                staging,
                "a sufficiently long backup passphrase".toCharArray()
            )
        }

        assertTrue("Manifest-only portable archive should decrypt", result.isSuccess)
        assertEquals(false, result.getOrThrow().backupInfo.includesDatabase)
        assertEquals(true, result.getOrThrow().backupInfo.includesVault)
    }


    @Test
    fun failedVaultReencryptionShredsPlaintextAndPartialCiphertext() {
        val root = temp.newFolder("failed-vault-reencryption")
        val payload = File(root, "payload.zip")
        val manifest = """{"version":3,"timestamp":1,"checksumSha256":"","databaseSizeBytes":0,"vaultSizeBytes":12,"fileCount":1,"vaultItemCount":1,"appVersion":"test","schemaVersion":1,"includesDatabase":false,"includesVault":true}"""
        ZipOutputStream(payload.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("vault/item.vvf"))
            zip.write("vault plaintext".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("metadata.json"))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
        }
        val encrypted = File(root, "portable.vvfbak")
        PortableBackupCrypto().encryptFile(payload, encrypted, "a sufficiently long backup passphrase".toCharArray())
        val info = CloudBackupInfo(
            backupId = "remote-vault",
            timestamp = 1L,
            backupName = "portable-vault.vvfbak",
            backupSizeBytes = encrypted.length(),
            includesVault = true,
            includesDatabase = false,
            checksumSha256 = "verified-by-caller"
        )
        val crypto = mock<CryptoSecurityManager>()
        whenever(crypto.encryptFile(any(), any())).thenAnswer { invocation ->
            val destination = invocation.getArgument<File>(1)
            destination.writeText("partial ciphertext")
            throw java.io.IOException("simulated encryption failure")
        }
        whenever(crypto.secureShredFile(any())).thenAnswer { invocation ->
            val file = invocation.getArgument<File>(0)
            if (!file.exists()) {
                true
            } else {
                file.writeBytes(ByteArray(file.length().toInt()))
                file.delete()
            }
        }
        val staging = File(root, "restore-stage").apply { mkdirs() }
        val passphrase = "a sufficiently long backup passphrase".toCharArray()

        val result = kotlinx.coroutines.runBlocking {
            CryptoBackupDecryptor(crypto).decrypt(
                VerifiedArtifact(encrypted, info, "verified-by-caller"),
                staging,
                passphrase
            )
        }

        assertTrue("Failed vault conversion must fail restore", result.isFailure)
        assertTrue("Decryptor must consume passphrase on failure", passphrase.all { it == 0.toChar() })
        verify(crypto, times(2)).secureShredFile(any())
        assertTrue("Failed restore staging must be removed", staging.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun rootLevelNonDatabaseFileIsNotAcceptedAsDatabasePayload() {
        val root = temp.newFolder("legacy-non-db-root")
        val payload = File(root, "payload.zip")
        val manifest = """{"version":3,"timestamp":1,"checksumSha256":"","databaseSizeBytes":0,"vaultSizeBytes":0,"fileCount":2,"vaultItemCount":0,"appVersion":"test","schemaVersion":1,"includesDatabase":true,"includesVault":false}"""
        ZipOutputStream(payload.outputStream()).use { zip ->
            zip.putNextEntry(ZipEntry("notes.txt"))
            zip.write("not a database".toByteArray())
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("key-material/database-passphrase.bin"))
            zip.write(ByteArray(32) { (it + 1).toByte() })
            zip.closeEntry()
            zip.putNextEntry(ZipEntry("metadata.json"))
            zip.write(manifest.toByteArray())
            zip.closeEntry()
        }
        val encrypted = File(root, "portable.vvfbak")
        PortableBackupCrypto().encryptFile(payload, encrypted, "a sufficiently long backup passphrase".toCharArray())
        val info = CloudBackupInfo(
            backupId = "remote-non-db-root",
            timestamp = 1L,
            backupName = "non-db.vvfbak",
            backupSizeBytes = encrypted.length(),
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

        assertTrue("Non-database root files must not be accepted as the database", result.isFailure)
        assertFalse("Rejected archive must not leave plaintext staging", staging.exists())
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
        assertFalse("Failed restore must remove plaintext staging", staging.exists())
    }
}
