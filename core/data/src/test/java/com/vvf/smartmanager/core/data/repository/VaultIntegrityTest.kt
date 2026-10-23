package com.vvf.smartmanager.core.data.repository

import com.vvf.smartmanager.core.database.dao.VaultDao
import com.vvf.smartmanager.core.database.dao.VaultJournalDao
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import java.nio.file.Files
import java.io.File

class VaultIntegrityTest {
    @Test fun failedSourceUnlinkRetainsCiphertextAndMetadata() = runBlocking {
        exercise(false)
    }
    @Test fun journalFailureAfterShreddingRetainsCiphertextAndMetadata() = runBlocking {
        exercise(true)
    }
    private suspend fun exercise(journalFailure: Boolean) {
        val root = Files.createTempDirectory("vault-integrity").toFile()
        try {
            val source = File(root, "source.txt").apply { writeText("original") }
            val vault = File(root, "vault").apply { mkdirs() }
            val dao = mock<VaultDao>()
            val journal = mock<VaultJournalDao>()
            val crypto = mock<CryptoSecurityManager>()
            whenever(journal.insertJournal(any())).thenReturn(1L)
            whenever(crypto.encryptFile(any(), any())).thenAnswer {
                it.getArgument<File>(1).writeText("authenticated-ciphertext-fixture")
                CryptoSecurityManager.StreamEncryptionResult("fixture-iv", 32)
            }
            whenever(crypto.secureShredFile(source)).thenAnswer {
                source.writeText("overwritten")
                if (journalFailure) source.delete()
                journalFailure
            }
            if (journalFailure) whenever(journal.updateJournal(any())).thenThrow(IllegalStateException("journal unavailable"))
            val repo = SecureVaultRepository(dao, crypto, vault, journal)
            assertTrue(repo.lockFileInVault(source, deleteOriginal = true).isFailure)
            assertEquals(1, vault.listFiles()!!.size)
            assertEquals("authenticated-ciphertext-fixture", vault.listFiles()!!.single().readText())
            verify(dao, never()).deleteById(any())
        } finally { root.deleteRecursively() }
    }
}
