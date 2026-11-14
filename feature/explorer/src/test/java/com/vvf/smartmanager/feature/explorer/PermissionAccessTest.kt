package com.vvf.smartmanager.feature.explorer

import android.Manifest
import android.app.Application
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
class PermissionAccessTest {

    @Test
    @Config(sdk = [29])
    fun android10ReadPermissionDoesNotClaimUnrestrictedBrowseAccess() {
        val app: Application = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)

        assertFalse(PermissionAccess.hasBrowseAccess(app))
    }

    @Test
    @Config(sdk = [28])
    fun android9ReadPermissionStillGrantsLegacyBrowseAccess() {
        val app: Application = RuntimeEnvironment.getApplication()
        Shadows.shadowOf(app).grantPermissions(Manifest.permission.READ_EXTERNAL_STORAGE)

        assertTrue(PermissionAccess.hasBrowseAccess(app))
    }
}
