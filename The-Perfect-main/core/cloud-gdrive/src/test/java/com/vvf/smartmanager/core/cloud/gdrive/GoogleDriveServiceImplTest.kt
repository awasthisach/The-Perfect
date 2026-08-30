package com.vvf.smartmanager.core.cloud.gdrive

import android.content.Context
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/**
 * Ensures production Drive service never pretends to be connected without a token.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class GoogleDriveServiceImplTest {

    private lateinit var context: Context
    private lateinit var service: GoogleDriveServiceImpl

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        service = GoogleDriveServiceImpl(context)
        service.disconnect()
    }

    @Test
    fun authenticate_withoutToken_fails() = runBlocking {
        val result = service.authenticate()
        assertTrue(result.isFailure)
        val msg = result.exceptionOrNull()?.message.orEmpty()
        assertTrue(msg.contains("token", ignoreCase = true) || msg.contains("OAuth", ignoreCase = true))
    }

    @Test
    fun listDriveFiles_withoutToken_fails() = runBlocking {
        val result = service.listDriveFiles("root")
        assertTrue(result.isFailure)
    }

    @Test
    fun getStorageQuota_withoutToken_fails() = runBlocking {
        val result = service.getStorageQuota()
        assertTrue(result.isFailure)
    }

    @Test
    fun disconnect_clearsConnectedState() {
        service.setAccessToken("dummy-not-used-without-network")
        service.disconnect()
        assertTrue(!service.getAccountInfo().isConnected)
    }

    @Test
    fun driveNetwork_createApi_succeeds() {
        val api = DriveNetwork.createApi(debugLogging = false)
        assertTrue(api != null)
    }
}
