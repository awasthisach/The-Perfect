package com.vvf.smartmanager.core.data.repository

import com.vvf.smartmanager.core.security.VaultBiometricCryptoHelper
import javax.crypto.Cipher

/** Vault biometric cipher API without reflecting private fields. */
fun SecureVaultRepository.createVaultBiometricCipher(): Cipher? =
    VaultBiometricCryptoHelper.createVaultBiometricCipher()

fun SecureVaultRepository.isVaultKeyAuthenticationRequired(): Boolean =
    isBiometricEnabled()
