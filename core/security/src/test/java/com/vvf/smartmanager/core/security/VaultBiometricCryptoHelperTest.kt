package com.vvf.smartmanager.core.security

import android.security.keystore.KeyPermanentlyInvalidatedException
import javax.crypto.SecretKey
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class VaultBiometricCryptoHelperTest {
    @Test
    fun invalidatedKeyIsDeletedAndNoCipherIsReturned() {
        var deleteAttempted = false
        val provider = object : VaultBiometricKeyStore {
            override fun getKey(alias: String): SecretKey? =
                throw KeyPermanentlyInvalidatedException("test invalidation")

            override fun deleteEntry(alias: String) {
                assertTrue(alias.contains("biometric"))
                deleteAttempted = true
            }
        }

        assertNull(VaultBiometricCryptoHelper.createVaultBiometricCipher(provider))
        assertTrue(deleteAttempted)
    }

    @Test
    fun invalidatedKeyCleanupFailureStillFailsClosed() {
        val provider = object : VaultBiometricKeyStore {
            override fun getKey(alias: String): SecretKey? =
                throw KeyPermanentlyInvalidatedException("test invalidation")

            override fun deleteEntry(alias: String) {
                throw IllegalStateException("keystore cleanup unavailable")
            }
        }
        assertNull(VaultBiometricCryptoHelper.createVaultBiometricCipher(provider))
    }

    @Test
    fun arbitrarySoftwareCipherCannotIssueBiometricProof() {
        val cipher = javax.crypto.Cipher.getInstance("AES/GCM/NoPadding")
        assertNull(VaultBiometricCryptoHelper.proveAuthenticatedCipher(cipher))
    }

    @Test
    fun biometricProofCanOnlyBeConsumedOnce() {
        val proof = VaultBiometricProof.issue()
        assertTrue(proof.consume())
        assertFalse(proof.consume())
    }
}
