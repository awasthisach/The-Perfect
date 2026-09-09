package com.vvf.smartmanager.feature.vault

import com.vvf.smartmanager.core.security.VaultBiometricCryptoHelper
import javax.crypto.Cipher

/** Creates a Cipher for BiometricPrompt.CryptoObject — no reflection. */
fun VaultViewModel.createBiometricUnlockCipher(): Cipher? =
    VaultBiometricCryptoHelper.createVaultBiometricCipher()
