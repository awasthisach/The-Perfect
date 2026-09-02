package com.vvf.smartmanager.core.data.storage

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for STORAGE-INV-001 / PROD-001: storage boundary must fail closed.
 * Empty approved roots must never broaden filesystem access.
 */
class StorageBoundaryTest {

    @Test
    fun emptyApprovedRoots_deniesAllPaths() {
        assertFalse(
            StorageManager.isPathWithinApprovedRoots(
                "/data/user/0/com.vvf.smartmanager/files",
                emptyList()
            )
        )
        assertFalse(
            StorageManager.isPathWithinApprovedRoots(
                "/storage/emulated/0",
                emptyList()
            )
        )
    }

    @Test
    fun pathInsideRoot_isAllowed() {
        val roots = listOf("/data/user/0/com.vvf.smartmanager/files")
        assertTrue(
            StorageManager.isPathWithinApprovedRoots(
                "/data/user/0/com.vvf.smartmanager/files",
                roots
            )
        )
        assertTrue(
            StorageManager.isPathWithinApprovedRoots(
                "/data/user/0/com.vvf.smartmanager/files/docs/note.txt",
                roots
            )
        )
    }

    @Test
    fun pathOutsideRoot_isDenied() {
        val roots = listOf("/data/user/0/com.vvf.smartmanager/files")
        assertFalse(
            StorageManager.isPathWithinApprovedRoots(
                "/storage/emulated/0/DCIM",
                roots
            )
        )
        assertFalse(
            StorageManager.isPathWithinApprovedRoots(
                "/data/user/0/com.vvf.smartmanager/files_extra",
                roots
            )
        )
    }

    @Test
    fun traversalAttempt_isDenied() {
        val roots = listOf("/data/user/0/com.vvf.smartmanager/files")
        // After canonicalization the real manager uses canonicalFile; pure helper
        // still rejects paths that do not stay under the root prefix.
        assertFalse(
            StorageManager.isPathWithinApprovedRoots(
                "/data/user/0/com.vvf.smartmanager/files/../cache",
                roots
            )
        )
    }

    @Test
    fun multipleRoots_acceptsAnyMatching() {
        val roots = listOf(
            "/data/user/0/com.vvf.smartmanager/files",
            "/storage/emulated/0"
        )
        assertTrue(
            StorageManager.isPathWithinApprovedRoots(
                "/storage/emulated/0/Download/a.pdf",
                roots
            )
        )
    }
}
