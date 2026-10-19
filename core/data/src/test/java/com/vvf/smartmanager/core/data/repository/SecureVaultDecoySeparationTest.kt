package com.vvf.smartmanager.core.data.repository

import com.vvf.smartmanager.core.database.VVFDatabase
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File
import java.nio.file.Files

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class SecureVaultDecoySeparationTest {
    @Test
    fun repositoryStoresRealAndDecoyCiphertextInSeparateDirectories() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        val database = VVFDatabase.buildInMemoryDatabase(context)
        val root = Files.createTempDirectory("vvf-vault-separation").toFile()
        val realDirectory = File(root, "vvf_vault_encrypted")
        val decoyDirectory = File(root, "vvf_decoy_vault_encrypted")
        val crypto = CryptoSecurityManager(context, "Robolectric")
        val repository = SecureVaultRepository(
            database.vaultDao(),
            crypto,
            realDirectory,
            database.vaultJournalDao(),
            decoyDirectory
        )

        try {
            val realSource = File(root, "real-secret.txt").apply { writeText("real vault fixture") }
            val decoySource = File(root, "decoy-note.txt").apply { writeText("decoy vault fixture") }

            val realItem = repository.lockFileInVault(realSource, isDecoy = false).getOrThrow()
            val decoyItem = repository.lockFileInVault(decoySource, isDecoy = true).getOrThrow()

            assertFalse(realDirectory.canonicalPath == decoyDirectory.canonicalPath)
            assertTrue(File(realDirectory, realItem.encryptedFileName).isFile)
            assertTrue(File(decoyDirectory, decoyItem.encryptedFileName).isFile)
            assertFalse(File(realDirectory, decoyItem.encryptedFileName).exists())
            assertFalse(File(decoyDirectory, realItem.encryptedFileName).exists())
            assertEquals(1, database.vaultDao().getAllVaultItems(false).first().size)
            assertEquals(1, database.vaultDao().getAllVaultItems(true).first().size)
            assertFalse(realItem.isDecoy)
            assertTrue(decoyItem.isDecoy)
        } finally {
            database.close()
            root.deleteRecursively()
        }
    }
}
