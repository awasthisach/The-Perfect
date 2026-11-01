package com.vvf.smartmanager.core.cloud.gdrive

import android.app.Activity
import androidx.test.core.app.ApplicationProvider
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Status
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleDriveAuthTest {
    @Test
    fun `access token must be present`() {
        assertEquals("token", GoogleDriveAuth.accessTokenResult("token").getOrThrow())
        assertTrue(GoogleDriveAuth.accessTokenResult(null).isFailure)
        assertTrue(GoogleDriveAuth.accessTokenResult(" ").isFailure)
    }

    @Test
    fun `cancelled authorization without data fails`() {
        val auth = GoogleDriveAuth(ApplicationProvider.getApplicationContext(), "client-id")
        val result = auth.handleSignInActivityResult(Activity.RESULT_CANCELED, null)
        assertTrue(result.isFailure)
    }

    @Test
    fun `developer error gives OAuth setup guidance`() {
        val message = GoogleDriveAuth.mapApiException(ApiException(Status(10)))
        assertTrue(message.contains("SHA-1"))
    }
}
