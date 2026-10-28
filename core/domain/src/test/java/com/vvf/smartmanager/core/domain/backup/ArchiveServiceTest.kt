package com.vvf.smartmanager.core.domain.backup

import com.vvf.smartmanager.core.data.backup.SnapshotSource
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import com.vvf.smartmanager.core.security.PortableBackupCrypto
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
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
import java.util.zip.ZipInputStream

class ArchiveServiceTest {
    @get:Rule
    val temp = TemporaryFolder()


    @Test
    fun failedVaultExportShredsPlaintextAndStagedCiphertext() = runBlocking {
        val cache = temp.newFolder("failed-vault-export-cache")
        val sourceVault = temp.newFolder("failed-vault-source")
        val sourceCiphertext = File(sourceVault, "enc_item.vvf").apply { writeText("source ciphertext") }
        val crypto = mock<CryptoSecurityManager>()
        whenever(crypto.getOrCreateDatabasePassphrase()).thenReturn(ByteArray(32) { (it + 5).toByte() })
        whenever(crypto.isVaultConfigured()).thenReturn(true)
        whenever(crypto.verifyVaultPin("1234")).thenReturn(true)
        whenever(crypto.decryptFile(any(), any())).thenAnswer { invocation ->
            invocation.getArgument<File>(1).writeText("partial vault plaintext")
            throw java.io.IOException("simulated decryption failure")
        }
        whenever(crypto.secureShredFile(any())).thenAnswer { invocation ->
            val file = invocation.getArgument<File>(0)
            if (!file.exists()) true else {
                file.writeBytes(ByteArray(file.length().toInt()))
                file.delete()
            }
        }
        val source = object : SnapshotSource {
            override val sourceName: String = "vault"
            override fun snapshot(stagingDir: File): File {
                return File(stagingDir, "vault/enc_item.vvf").apply {
                    parentFile.mkdirs()
                    sourceCiphertext.copyTo(this, overwrite = true)
                }
            }
            override fun dataSizeBytes(): Long = sourceCiphertext.length()
        }
        val databaseSource = object : SnapshotSource {
            override val sourceName: String = "database"
            override fun snapshot(stagingDir: File): File =
                File(stagingDir, "database/vvf_smart_manager_enc.db").apply {
                    parentFile.mkdirs()
                    writeText("SQLCIPHER-DATABASE")
                }
            override fun dataSizeBytes(): Long = 18L
        }
        val service = ArchiveService(cache, listOf(source, databaseSource), crypto)
        val passphrase = "portable backup passphrase".toCharArray()

        val result = service.createArchive(
            includeVault = true,
            includeDatabase = true,
            passphrase = passphrase,
            vaultPin = "1234"
        )

        assertTrue("Archive must fail when vault decryption fails", result.isFailure)
        assertTrue("Backup passphrase must be consumed on failure", passphrase.all { it == 0.toChar() })
        verify(crypto, org.mockito.kotlin.atLeast(2)).secureShredFile(any())
        assertTrue("Failed archive must remove plaintext staging", cache.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun plaintextCleanupFailurePreventsPublishingPortableArchive() = runBlocking {
        val cache = temp.newFolder("cleanup-failure-cache")
        val crypto = mock<CryptoSecurityManager>()
        whenever(crypto.getOrCreateDatabasePassphrase()).thenReturn(ByteArray(32) { (it + 3).toByte() })
        whenever(crypto.secureShredFile(any())).thenReturn(false)
        val source = object : SnapshotSource {
            override val sourceName: String = "database"
            override fun snapshot(stagingDir: File): File =
                File(stagingDir, "database/vvf_smart_manager_enc.db").apply {
                    parentFile.mkdirs()
                    writeText("SQLCIPHER-DATABASE")
                }
            override fun dataSizeBytes(): Long = 18L
        }
        val emptyVaultSource = object : SnapshotSource {
            override val sourceName: String = "vault"
            override fun snapshot(stagingDir: File): File = File(stagingDir, "vault").apply { mkdirs() }
            override fun dataSizeBytes(): Long = 0L
        }
        val passphrase = "a sufficiently long backup passphrase".toCharArray()

        val result = ArchiveService(cache, listOf(source, emptyVaultSource), crypto).createArchive(
            includeVault = false,
            includeDatabase = true,
            passphrase = passphrase
        )

        assertTrue("Backup must fail if plaintext staging cannot be securely removed", result.isFailure)
        assertTrue("Backup passphrase must be consumed", passphrase.all { it == 0.toChar() })
        assertTrue(
            "A failed cleanup must never leave a publishable portable archive",
            cache.listFiles().orEmpty().none { it.name.endsWith(".vvfbak") }
        )
    }

    @Test
    fun vaultOnlyArchiveIsRejectedBecauseVaultMetadataRequiresDatabase() = runBlocking {
        val cache = temp.newFolder("vault-only-rejected")
        val crypto = mock<CryptoSecurityManager>()
        val passphrase = "a sufficiently long backup passphrase".toCharArray()

        val result = ArchiveService(cache, emptyList(), crypto).createArchive(
            includeVault = true,
            includeDatabase = false,
            passphrase = passphrase
        )

        assertTrue("Vault-only archive cannot preserve Room vault metadata", result.isFailure)
        assertTrue("Rejected backup must consume passphrase", passphrase.all { it == 0.toChar() })
        assertTrue("Rejected backup must not create artifacts", cache.listFiles().orEmpty().isEmpty())
    }

    @Test
    fun databaseOnlyArchiveRejectsNonEmptyVaultToAvoidDanglingMetadata() = runBlocking {
        val cache = temp.newFolder("database-with-vault-rejected")
        val crypto = mock<CryptoSecurityManager>()
        whenever(crypto.getOrCreateDatabasePassphrase()).thenReturn(ByteArray(32) { 1 })
        whenever(crypto.secureShredFile(any())).thenAnswer { invocation ->
            val file = invocation.getArgument<File>(0)
            if (!file.exists()) true else {
                file.writeBytes(ByteArray(file.length().toInt()))
                file.delete()
            }
        }
        val databaseSource = object : SnapshotSource {
            override val sourceName: String = "database"
            override fun snapshot(stagingDir: File): File =
                File(stagingDir, "database/vvf_smart_manager_enc.db").apply {
                    parentFile.mkdirs()
                    writeText("SQLCIPHER-DATABASE")
                }
            override fun dataSizeBytes(): Long = 18L
        }
        val vaultSource = object : SnapshotSource {
            override val sourceName: String = "vault"
            override fun snapshot(stagingDir: File): File =
                File(stagingDir, "vault/enc_item.vvf").apply {
                    parentFile.mkdirs()
                    writeText("ciphertext")
                }
            override fun dataSizeBytes(): Long = 10L
        }
        val passphrase = "a sufficiently long backup passphrase".toCharArray()

        val result = ArchiveService(cache, listOf(databaseSource, vaultSource), crypto).createArchive(
            includeVault = false,
            includeDatabase = true,
            passphrase = passphrase
        )

        assertTrue("Database-only backup must not omit files referenced by vault metadata", result.isFailure)
        assertTrue(
            "User must receive an actionable reason for the rejected partial backup",
            result.exceptionOrNull()?.message?.contains("vault contains files", ignoreCase = true) == true
        )
        assertTrue("Rejected backup must consume passphrase", passphrase.all { it == 0.toChar() })
        assertTrue("Rejected backup must not publish an encrypted archive", cache.listFiles().orEmpty().none { it.name.endsWith(".vvfbak") })
    }

    @Test
    fun databaseOnlyArchiveCarriesAuthenticatedPayloadSelectionFlags() = runBlocking {
        val cache = temp.newFolder("archive-cache")
        val crypto = mock<CryptoSecurityManager>()
        whenever(crypto.secureShredFile(any())).thenAnswer { invocation ->
            val file = invocation.getArgument<File>(0)
            if (!file.exists()) true else {
                file.writeBytes(ByteArray(file.length().toInt()))
                file.delete()
            }
        }
        whenever(crypto.getOrCreateDatabasePassphrase()).thenReturn(ByteArray(32) { (it + 7).toByte() })
        val source = object : SnapshotSource {
            override val sourceName: String = "database"
            override fun snapshot(stagingDir: File): File {
                return File(stagingDir, "database/vvf_smart_manager_enc.db").apply {
                    parentFile.mkdirs()
                    writeBytes("SQLCIPHER-DATABASE".toByteArray())
                }
            }
            override fun dataSizeBytes(): Long = "SQLCIPHER-DATABASE".length.toLong()
        }
        val emptyVaultSource = object : SnapshotSource {
            override val sourceName: String = "vault"
            override fun snapshot(stagingDir: File): File = File(stagingDir, "vault").apply { mkdirs() }
            override fun dataSizeBytes(): Long = 0L
        }
        val service = ArchiveService(cache, listOf(source, emptyVaultSource), crypto)
        val passphrase = "a sufficiently long backup passphrase".toCharArray()

        val result = service.createArchive(
            includeVault = false,
            includeDatabase = true,
            passphrase = passphrase
        )

        assertTrue("Database-only archive should be created", result.isSuccess)
        assertTrue("Archive creation must consume passphrase", passphrase.all { it == 0.toChar() })
        val artifact = result.getOrThrow()
        assertTrue(
            "Successful backup must not leave plaintext ZIP or staging data",
            cache.listFiles().orEmpty().none {
                it.name.startsWith("archive-staging-") ||
                    (it.name.startsWith("backup-") && it.name.endsWith(".zip"))
            }
        )
        val zip = File(cache, "inspect.zip")
        PortableBackupCrypto().decryptFile(
            artifact.file,
            zip,
            "a sufficiently long backup passphrase".toCharArray()
        )
        val manifestText = ZipInputStream(zip.inputStream()).use { input ->
            var found: String? = null
            while (true) {
                val entry = input.nextEntry ?: break
                if (entry.name == "metadata.json") {
                    found = input.readBytes().toString(Charsets.UTF_8)
                    break
                }
            }
            found
        }
        assertTrue("Encrypted archive must contain metadata", manifestText != null)
        val manifest = Json { ignoreUnknownKeys = true }
            .decodeFromString(ArchiveMetadata.serializer(), manifestText!!)
        assertEquals(4, manifest.version)
        assertEquals(true, manifest.includesDatabase)
        assertEquals(false, manifest.includesVault)
    }
}
