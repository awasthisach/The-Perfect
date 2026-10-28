package com.vvf.smartmanager.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

class VaultBiometricCryptoHelperTest {
    @Test
    fun arbitrarySoftwareCipherCannotIssueBiometricProof() {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(ByteArray(32).also(SecureRandom()::nextBytes), "AES"))
        assertNull(
            "A caller-supplied software cipher must not mint an unlock capability",
            VaultBiometricCryptoHelper.proveAuthenticatedCipher(cipher)
        )
    }

    @Test
    fun biometricProofCanOnlyBeConsumedOnce() {
        val proof = VaultBiometricProof.issue()

        assertTrue("First verified biometric proof consumption should succeed", proof.consume())
        assertFalse("Replayed biometric proof must not unlock a second session", proof.consume())
    }
}
