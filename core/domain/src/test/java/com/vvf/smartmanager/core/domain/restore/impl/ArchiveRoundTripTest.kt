package com.vvf.smartmanager.core.domain.restore.impl

import com.vvf.smartmanager.core.data.backup.ReadOnlyDatabaseSnapshotSource
import com.vvf.smartmanager.core.domain.backup.ArchiveService
import com.vvf.smartmanager.core.domain.restore.VerifiedArtifact
import com.vvf.smartmanager.core.model.CloudBackupInfo
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.*
import java.io.File
import java.nio.file.Files

/** Real snapshot/archive/decryptor/applier boundary. Crypto transport is stubbed here;
 * authenticated encryption and SQLCipher validation require the native suite separately. */
class ArchiveRoundTripTest {
    @Test
    fun vaultArchiveFailsClosedWithoutPortableKey() = runBlocking {
        val root = Files.createTempDirectory("archive-portability").toFile()
        try {
            val result = ArchiveService(
                File(root, "cache"),
                emptyList(),
                mock<CryptoSecurityManager>()
            ).createArchive(includeVault = true, includeDatabase = false)
            assertTrue(result.isFailure)
            assertTrue(result.exceptionOrNull()?.message.orEmpty().contains("Portable vault backup is unavailable"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test fun actualProducerRestoresDatabaseAndWalUsingDeclaredLayout() = runBlocking {
        val root = Files.createTempDirectory("archive-roundtrip").toFile()
        try {
            val source = File(root, "source.db").apply { writeText("snapshot-db") }
            File(source.path + "-wal").writeText("snapshot-wal")
            val crypto = mock<CryptoSecurityManager>()
            whenever(crypto.encryptFile(any(), any())).thenAnswer {
                val from = it.getArgument<File>(0); from.copyTo(it.getArgument(1), overwrite = true)
                CryptoSecurityManager.StreamEncryptionResult("fixture", from.length())
            }
            whenever(crypto.decryptFile(any(), any())).thenAnswer {
                val from = it.getArgument<File>(0); from.copyTo(it.getArgument(1), overwrite = true)
                from.length()
            }
            val artifact = ArchiveService(File(root, "cache"), listOf(ReadOnlyDatabaseSnapshotSource(source)), crypto)
                .createArchive(includeVault = false).getOrThrow()
            val info = CloudBackupInfo(backupId = "synthetic", timestamp = 1, backupName = "test", backupSizeBytes = artifact.file.length())
            val decoded = CryptoBackupDecryptor(crypto).decrypt(VerifiedArtifact(artifact.file, info, artifact.checksumSha256), File(root,"stage")).getOrThrow()
            assertEquals("snapshot-db", decoded.databaseFile.readText())
            val target = File(root, "target.db").apply { writeText("old") }
            val applier = LocalRestoreApplier(target, File(root,"vault"), File(root,"undo"))
            assertTrue(applier.apply(decoded).isSuccess)
            assertEquals("snapshot-db", target.readText())
            assertEquals("snapshot-wal", File(target.path + "-wal").readText())
        } finally { root.deleteRecursively() }
    }
}
