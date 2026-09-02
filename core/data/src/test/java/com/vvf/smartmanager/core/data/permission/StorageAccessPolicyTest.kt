package com.vvf.smartmanager.core.data.permission

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class StorageAccessPolicyTest {
    @Test
    fun api34WithoutGrantsIsNoneAndRequestsMedia() {
        val d = StorageAccessPolicy.evaluate(StoragePermissionSnapshot(sdkInt = 34))
        assertEquals(StorageAccessLevel.NONE, d.level)
        assertFalse(d.canBrowsePrimaryTree)
        assertTrue(d.requiresRuntimeMediaRequest)
        assertTrue(d.requiresSettingsAllFilesAccess)
    }

    @Test
    fun api34WithMediaOnlyCannotBrowseTree() {
        val d = StorageAccessPolicy.evaluate(
            StoragePermissionSnapshot(sdkInt = 34, hasReadMediaImages = true)
        )
        assertEquals(StorageAccessLevel.MEDIA_ONLY, d.level)
        assertFalse(d.canBrowsePrimaryTree)
        assertTrue(d.canListMediaCategories)
        assertTrue(d.requiresSettingsAllFilesAccess)
    }

    @Test
    fun api34WithManageExternalGetsAllFiles() {
        val d = StorageAccessPolicy.evaluate(
            StoragePermissionSnapshot(sdkInt = 34, hasManageExternalStorage = true)
        )
        assertEquals(StorageAccessLevel.ALL_FILES, d.level)
        assertTrue(d.canBrowsePrimaryTree)
        assertFalse(d.requiresSettingsAllFilesAccess)
    }

    @Test
    fun api30WithoutManageIsNone() {
        val d = StorageAccessPolicy.evaluate(StoragePermissionSnapshot(sdkInt = 30))
        assertEquals(StorageAccessLevel.NONE, d.level)
        assertTrue(d.requiresSettingsAllFilesAccess)
    }

    @Test
    fun api28WithReadGetsLegacyFull() {
        val d = StorageAccessPolicy.evaluate(
            StoragePermissionSnapshot(sdkInt = 28, hasReadExternalStorage = true)
        )
        assertEquals(StorageAccessLevel.LEGACY_FULL, d.level)
        assertTrue(d.canBrowsePrimaryTree)
    }

    @Test(expected = IllegalArgumentException::class)
    fun assertBrowseFailsWhenNone() {
        val d = StorageAccessPolicy.evaluate(StoragePermissionSnapshot(sdkInt = 34))
        StorageAccessPolicy.assertCanBrowsePrimaryTree(d)
    }
}
