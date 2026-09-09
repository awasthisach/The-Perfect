package com.vvf.smartmanager.core.security

import android.content.Context
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.SecretKey

/**
 * Hardware-backed vault cipher for BiometricPrompt.CryptoObject.
 * Same AndroidKeyStore alias as CryptoSecurityManager vault key.
 * No reflection — ProGuard/R8 safe.
 */
object VaultBiometricCryptoHelper {
    private const val VAULT_KEY_ALIAS = "vvf_vault_master_key_v1"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFS_NAME = "vvf_secure_vault_prefs"

    fun createVaultBiometricCipher(): Cipher? {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = keyStore.getKey(VAULT_KEY_ALIAS, null) as? SecretKey ?: return null
            Cipher.getInstance(AES_GCM_TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, key)
            }
        } catch (_: Exception) {
            null
        }
    }

    fun isAutoIndexOcrEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("auto_index_ocr_enabled", true)

    fun setAutoIndexOcrEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("auto_index_ocr_enabled", enabled).apply()
    }

    fun isStrictOfflineMode(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("strict_offline_mode", true)

    fun setStrictOfflineMode(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("strict_offline_mode", enabled).apply()
    }
}
