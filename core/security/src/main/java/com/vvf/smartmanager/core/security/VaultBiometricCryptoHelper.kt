package com.vvf.smartmanager.core.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

/**
 * Hardware-backed biometric authorization cipher for BiometricPrompt.CryptoObject.
 *
 * The biometric authorization key is deliberately separate from the vault data key:
 * the vault data key must remain usable through the application's PIN fallback.
 * The auth-per-use key is used for a real cryptographic proof operation inside the
 * BiometricPrompt flow, so a successful callback cannot be produced by merely
 * checking that a CryptoObject exists.
 */
object VaultBiometricCryptoHelper {
    private const val BIOMETRIC_AUTH_KEY_ALIAS = "vvf_vault_biometric_auth_key_v1"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFS_NAME = "vvf_secure_vault_prefs"
    private val BIOMETRIC_PROOF = "VVF_VAULT_BIOMETRIC_PROOF_V1".toByteArray(Charsets.UTF_8)

    fun createVaultBiometricCipher(): Cipher? {
        return try {
            val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            val key = (keyStore.getKey(BIOMETRIC_AUTH_KEY_ALIAS, null) as? SecretKey)
                ?: generateBiometricAuthKey()
            Cipher.getInstance(AES_GCM_TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, key)
            }
        } catch (_: Exception) {
            null
        }
    }

    /** Performs a real operation with the authenticated CryptoObject key. */
    fun proveAuthenticatedCipher(cipher: Cipher): Boolean = try {
        cipher.doFinal(BIOMETRIC_PROOF).isNotEmpty()
    } catch (_: Exception) {
        false
    }

    private fun generateBiometricAuthKey(): SecretKey {
        val keyGenerator = KeyGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_AES,
            "AndroidKeyStore"
        )
        val builder = KeyGenParameterSpec.Builder(
            BIOMETRIC_AUTH_KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .setRandomizedEncryptionRequired(true)
            .setUserAuthenticationRequired(true)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            builder.setUserAuthenticationParameters(
                0,
                KeyProperties.AUTH_BIOMETRIC_STRONG
            )
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }

        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
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
