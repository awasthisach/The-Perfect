package com.vvf.smartmanager

/** Biometric enrollment is allowed only after both device capability and vault PIN proof succeed. */
internal fun canEnableBiometricVault(
    strongBiometricAvailable: Boolean,
    vaultPinVerified: Boolean
): Boolean = strongBiometricAvailable && vaultPinVerified
