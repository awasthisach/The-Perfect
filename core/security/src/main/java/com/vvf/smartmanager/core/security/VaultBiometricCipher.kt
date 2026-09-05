package com.vvf.smartmanager.core.security

import android.security.keystore.UserNotAuthenticatedException
import javax.crypto.Cipher
import javax.crypto.SecretKey

/**
 * Creates a Cipher bound to the vault Keystore key for BiometricPrompt.CryptoObject.
 * Returns null when the user-auth validity window is closed.
 */
fun CryptoSecurityManager.createVaultBiometricCipher(): Cipher? {
    return try {
        val method = CryptoSecurityManager::class.java.getDeclaredMethod(
            "getSecretKey",
            String::class.java
        )
        method.isAccessible = true
        val key = method.invoke(this, "vvf_vault_master_key_v1") as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        cipher
    } catch (e: UserNotAuthenticatedException) {
        null
    } catch (e: java.lang.reflect.InvocationTargetException) {
        if (e.cause is UserNotAuthenticatedException) null
        else throw e
    }
}

fun CryptoSecurityManager.isVaultKeyAuthenticationRequired(): Boolean = true
