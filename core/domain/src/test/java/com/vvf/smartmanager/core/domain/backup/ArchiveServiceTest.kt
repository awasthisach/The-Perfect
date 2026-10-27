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
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.io.File
import java.util.zip.ZipInputStream

class ArchiveServiceTest {
    @get:Rule
    val temp = TemporaryFolder()

    @Test
    fun databaseOnlyArchiveCarriesAuthenticatedPayloadSelectionFlags() = runBlocking {
        val cache = temp.newFolder("archive-cache")
        val crypto = mock<CryptoSecurityManager>()
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
        val service = ArchiveService(cache, listOf(source), crypto)
        val passphrase = "a sufficiently long backup passphrase".toCharArray()

        val result = service.createArchive(
            includeVault = false,
            includeDatabase = true,
            passphrase = passphrase
        )

        assertTrue("Database-only archive should be created", result.isSuccess)
        assertTrue("Archive creation must consume passphrase", passphrase.all { it == 0.toChar() })
        val artifact = result.getOrThrow()
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
        assertEquals(3, manifest.version)
        assertEquals(true, manifest.includesDatabase)
        assertEquals(false, manifest.includesVault)
    }
}
