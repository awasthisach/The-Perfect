package com.vvf.smartmanager.core.security

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VaultBiometricCryptoHelperTest {
    @Test
    fun biometricProofCanOnlyBeConsumedOnce() {
        val proof = VaultBiometricProof.issue()

        assertTrue("First verified biometric proof consumption should succeed", proof.consume())
        assertFalse("Replayed biometric proof must not unlock a second session", proof.consume())
    }
}
