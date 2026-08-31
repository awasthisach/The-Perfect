package com.vvf.smartmanager.core.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class SecureVaultRepositoryContractTest {

    @Test
    fun canonicalDirectoryContainmentDoesNotAcceptSiblingPrefix() {
        val root = Files.createTempDirectory("vault-root").toFile()
        val vault = File(root, "vault").apply { mkdirs() }
        val sibling = File(root, "vault-evil").apply { mkdirs() }

        assertTrue(canonicalInside(vault, File(vault, "enc_123.vvf")))
        assertFalse(canonicalInside(vault, File(sibling, "enc_123.vvf")))
    }

    @Test
    fun nestedVaultPathIsContained() {
        val root = Files.createTempDirectory("vault-root").toFile()
        val vault = File(root, "vault").apply { mkdirs() }
        val nested = File(vault, "nested/file.vvf")

        assertTrue(canonicalInside(vault, nested))
    }

    private fun canonicalInside(directory: File, candidate: File): Boolean {
        val directoryPath = directory.canonicalPath
        val candidatePath = candidate.canonicalPath
        return candidatePath == directoryPath || candidatePath.startsWith(directoryPath + File.separator)
    }
}
