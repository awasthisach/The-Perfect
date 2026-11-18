package com.vvf.smartmanager

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BiometricEnrollmentPolicyTest {
    @Test
    fun enrollmentRequiresStrongBiometricAndVerifiedVaultPin() {
        assertTrue(canEnableBiometricVault(strongBiometricAvailable = true, vaultPinVerified = true))
        assertFalse(canEnableBiometricVault(strongBiometricAvailable = false, vaultPinVerified = true))
        assertFalse(canEnableBiometricVault(strongBiometricAvailable = true, vaultPinVerified = false))
        assertFalse(canEnableBiometricVault(strongBiometricAvailable = false, vaultPinVerified = false))
    }
}
