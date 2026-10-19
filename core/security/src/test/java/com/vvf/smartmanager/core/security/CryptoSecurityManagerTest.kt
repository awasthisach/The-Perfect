package com.vvf.smartmanager.core.security
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class CryptoSecurityManagerTest {
    private lateinit var crypto: CryptoSecurityManager
    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<Context>()
        CryptoSecurityManager.clearInMemoryKeysForTests()
        crypto = CryptoSecurityManager(ctx)
    }
    @Test fun pinFormat() {
        assertTrue(crypto.isValidPinFormat("1234"))
        assertFalse(crypto.isValidPinFormat("12"))
    }
    @Test fun realAndDecoyPins() {
        assertTrue(crypto.setupVaultPin("2468"))
        assertTrue(crypto.setupDecoyPin("654321"))
        assertEquals(CryptoSecurityManager.VaultAuthResult.SUCCESS_REAL, crypto.verifyVaultPinWithResult("2468"))
        assertEquals(CryptoSecurityManager.VaultAuthResult.SUCCESS_DECOY, crypto.verifyVaultPinWithResult("654321"))
    }
    @Test fun wipeBuffer() {
        val b = byteArrayOf(1,2,3); crypto.wipeBuffer(b); assertTrue(b.all { it == 0.toByte() })
    }
}
