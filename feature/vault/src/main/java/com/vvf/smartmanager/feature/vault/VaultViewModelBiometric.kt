package com.vvf.smartmanager.feature.vault

import com.vvf.smartmanager.core.domain.VaultAuthUseCase
import javax.crypto.Cipher

/**
 * Creates a Cipher bound to the vault Keystore key for BiometricPrompt.CryptoObject.
 * Uses the ViewModel's VaultAuthUseCase (reflective access to private field).
 */
fun VaultViewModel.createBiometricUnlockCipher(): Cipher? {
    return try {
        val field = VaultViewModel::class.java.getDeclaredField("vaultAuthUseCase")
        field.isAccessible = true
        val useCase = field.get(this) as VaultAuthUseCase
        if (!useCase.isBiometricEnabled()) return null
        useCase.createVaultBiometricCipher()
    } catch (e: Exception) {
        null
    }
}
