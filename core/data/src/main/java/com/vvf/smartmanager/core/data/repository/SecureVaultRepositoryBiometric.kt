package com.vvf.smartmanager.core.data.repository

import com.vvf.smartmanager.core.security.CryptoSecurityManager
import com.vvf.smartmanager.core.security.createVaultBiometricCipher
import com.vvf.smartmanager.core.security.isVaultKeyAuthenticationRequired
import javax.crypto.Cipher

/**
 * Biometric cipher helpers for [SecureVaultRepository].
 * Delegates to CryptoSecurityManager extension (VaultBiometricCipher.kt).
 */
fun SecureVaultRepository.createVaultBiometricCipher(): Cipher? {
    val field = SecureVaultRepository::class.java.getDeclaredField("cryptoManager")
    field.isAccessible = true
    val crypto = field.get(this) as CryptoSecurityManager
    return crypto.createVaultBiometricCipher()
}

fun SecureVaultRepository.isVaultKeyAuthenticationRequired(): Boolean {
    val field = SecureVaultRepository::class.java.getDeclaredField("cryptoManager")
    field.isAccessible = true
    val crypto = field.get(this) as CryptoSecurityManager
    return crypto.isVaultKeyAuthenticationRequired()
}
