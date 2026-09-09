package com.vvf.smartmanager.core.security

import javax.crypto.Cipher

/** Creates a Cipher bound to the vault Keystore key — no reflection. */
fun CryptoSecurityManager.createVaultBiometricCipher(): Cipher? =
    VaultBiometricCryptoHelper.createVaultBiometricCipher()

fun CryptoSecurityManager.isVaultKeyAuthenticationRequired(): Boolean =
    isBiometricEnabled()
