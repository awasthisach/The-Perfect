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
            StoragePathPolicy.isPathWithinApprovedRoots(
                "/data/user/0/com.vvf.smartmanager/files",
                emptyList()
            )
        )
        assertFalse(
            StoragePathPolicy.isPathWithinApprovedRoots(
                "/storage/emulated/0",
                emptyList()
            )
        )
    }

    @Test
    fun pathInsideRoot_isAllowed() {
        val roots = listOf("/data/user/0/com.vvf.smartmanager/files")
        assertTrue(
            StoragePathPolicy.isPathWithinApprovedRoots(
                "/data/user/0/com.vvf.smartmanager/files",
                roots
            )
        )
        assertTrue(
            StoragePathPolicy.isPathWithinApprovedRoots(
                "/data/user/0/com.vvf.smartmanager/files/docs/note.txt",
                roots
            )
        )
    }

    @Test
    fun pathOutsideRoot_isDenied() {
        val roots = listOf("/data/user/0/com.vvf.smartmanager/files")
        assertFalse(
            StoragePathPolicy.isPathWithinApprovedRoots(
                "/storage/emulated/0/DCIM",
                roots
            )
        )
        assertFalse(
            StoragePathPolicy.isPathWithinApprovedRoots(
                "/data/user/0/com.vvf.smartmanager/files_extra",
                roots
            )
        )
    }

    @Test
    fun traversalAttempt_isDenied() {
        val roots = listOf("/data/user/0/com.vvf.smartmanager/files")
        assertFalse(
            StoragePathPolicy.isPathWithinApprovedRoots(
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
            StoragePathPolicy.isPathWithinApprovedRoots(
                "/storage/emulated/0/Download/a.pdf",
                roots
            )
        )
    }

    @Test
    fun emptyRoots_neverAllowAbsoluteRoot() {
        assertFalse(
            StoragePathPolicy.isPathWithinApprovedRoots("/", emptyList())
        )
        assertFalse(
            StoragePathPolicy.isPathWithinApprovedRoots("/data", emptyList())
        )
    }

    @Test
    fun rootPrefixMustNotMatchPartialName() {
        val roots = listOf("/data/user/0/com.vvf.smartmanager/files")
        assertFalse(
            StoragePathPolicy.isPathWithinApprovedRoots(
                "/data/user/0/com.vvf.smartmanager/files_extra/secret",
                roots
            )
        )
    }

    @Test
    fun blankAndNullBytePaths_areDenied() {
        val roots = listOf("/data/user/0/com.vvf.smartmanager/files")
        assertFalse(StoragePathPolicy.isPathWithinApprovedRoots("", roots))
        assertFalse(StoragePathPolicy.isPathWithinApprovedRoots("   ", roots))
        assertFalse(
            StoragePathPolicy.isPathWithinApprovedRoots(
                "/data/user/0/com.vvf.smartmanager/files\u0000evil",
                roots
            )
        )
    }

    @Test
    fun rootPrefixConfusion_isDenied() {
        val roots = listOf("/data/user/0/com.vvf.smartmanager/files")
        assertFalse(
            StoragePathPolicy.isPathWithinApprovedRoots(
                "/data/user/0/com.vvf.smartmanager/files_extra/secret",
                roots
            )
        )
    }
}
