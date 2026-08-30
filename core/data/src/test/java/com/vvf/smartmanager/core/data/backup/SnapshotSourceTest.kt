package com.vvf.smartmanager.core.data.backup

import java.io.File
import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SnapshotSourceTest {
    @Test
    fun databaseSnapshotCopiesDatabaseAndWalShmWithoutTouchingSource() {
        val root = Files.createTempDirectory("snapshot-db").toFile()
        try {
            val database = File(root, "vvf.db").apply { writeText("db") }
            File(root, "vvf.db-wal").writeText("wal")
            File(root, "vvf.db-shm").writeText("shm")
            val staging = File(root, "staging").apply { mkdirs() }

            val result = ReadOnlyDatabaseSnapshotSource(database).snapshot(staging)

            assertNotNull(result)
            assertEquals("db", File(staging, "vvf.db").readText())
            assertEquals("wal", File(staging, "vvf.db-wal").readText())
            assertEquals("shm", File(staging, "vvf.db-shm").readText())
            assertEquals("db", database.readText())
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun missingDatabaseReturnsNull() {
        val root = Files.createTempDirectory("snapshot-missing").toFile()
        try {
            val result = ReadOnlyDatabaseSnapshotSource(File(root, "missing.db"))
                .snapshot(File(root, "staging").apply { mkdirs() })
            assertTrue(result == null)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun vaultSnapshotCopiesInjectedDirectoryAndPreservesSource() {
        val root = Files.createTempDirectory("snapshot-vault").toFile()
        try {
            val vault = File(root, "vault").apply { mkdirs() }
            File(vault, "item.bin").writeText("secret")
            val staging = File(root, "staging").apply { mkdirs() }

            val result = InjectedVaultSnapshotSource(vault).snapshot(staging)

            assertNotNull(result)
            assertEquals("secret", File(staging, "vault/item.bin").readText())
            assertEquals(6L, InjectedVaultSnapshotSource(vault).dataSizeBytes())
            assertFalse(File(vault, "item.bin").readText().isEmpty())
        } finally {
            root.deleteRecursively()
        }
    }
}
