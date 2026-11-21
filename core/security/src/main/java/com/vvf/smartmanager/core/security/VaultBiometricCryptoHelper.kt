package com.vvf.smartmanager.core.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyPermanentlyInvalidatedException
import android.security.keystore.KeyProperties
import android.util.Log
import java.security.KeyStore
import java.util.WeakHashMap
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey

internal interface VaultBiometricKeyStore {
    fun getKey(alias: String): SecretKey?
    fun deleteEntry(alias: String)
}

private object AndroidVaultBiometricKeyStore : VaultBiometricKeyStore {
    override fun getKey(alias: String): SecretKey? =
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
            .getKey(alias, null) as? SecretKey

    override fun deleteEntry(alias: String) {
        KeyStore.getInstance("AndroidKeyStore").apply { load(null) }.deleteEntry(alias)
    }
}

/**
 * Hardware-backed biometric authorization cipher for BiometricPrompt.CryptoObject.
 * The authorization key is separate from the vault data key so PIN fallback remains available.
 */
object VaultBiometricCryptoHelper {
    private const val TAG = "VaultBiometric"
    private const val BIOMETRIC_AUTH_KEY_ALIAS = "vvf_vault_biometric_auth_key_v1"
    private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
    private const val PREFS_NAME = "vvf_secure_vault_prefs"
    private val BIOMETRIC_PROOF = "VVF_VAULT_BIOMETRIC_PROOF_V1".toByteArray(Charsets.UTF_8)
    private val issuedCiphers = WeakHashMap<Cipher, Unit>()

    fun createVaultBiometricCipher(): Cipher? =
        createVaultBiometricCipher(AndroidVaultBiometricKeyStore)

    internal fun createVaultBiometricCipher(keyStore: VaultBiometricKeyStore): Cipher? {
        return try {
            val key = keyStore.getKey(BIOMETRIC_AUTH_KEY_ALIAS) ?: generateBiometricAuthKey()
            val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION).apply {
                init(Cipher.ENCRYPT_MODE, key)
            }
            synchronized(issuedCiphers) { issuedCiphers[cipher] = Unit }
            cipher
        } catch (error: KeyPermanentlyInvalidatedException) {
            // Delete only the biometric authorization alias, never the vault data/master key.
            try {
                keyStore.deleteEntry(BIOMETRIC_AUTH_KEY_ALIAS)
            } catch (cleanupError: Exception) {
                Log.e(TAG, "Unable to delete invalidated biometric authorization key", cleanupError)
            }
            Log.w(TAG, "Biometric key invalidated; PIN authentication is required.")
            null
        } catch (error: Exception) {
            Log.e(TAG, "Biometric cipher initialization failed; PIN fallback remains available.", error)
            null
        }
    }

    fun proveAuthenticatedCipher(cipher: Cipher): VaultBiometricProof? {
        val wasIssuedHere = synchronized(issuedCiphers) { issuedCiphers.remove(cipher) != null }
        if (!wasIssuedHere) return null
        return try {
            val proof = cipher.doFinal(BIOMETRIC_PROOF)
            try {
                if (proof.isNotEmpty()) VaultBiometricProof.issue() else null
            } finally {
                proof.fill(0)
            }
        } catch (_: Exception) {
            null
        }
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
            builder.setUserAuthenticationParameters(0, KeyProperties.AUTH_BIOMETRIC_STRONG)
        } else {
            @Suppress("DEPRECATION")
            builder.setUserAuthenticationValidityDurationSeconds(-1)
        }
        keyGenerator.init(builder.build())
        return keyGenerator.generateKey()
    }

    fun isAutoIndexOcrEnabled(context: Context): Boolean =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean("auto_index_ocr_enabled", false)

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

class VaultBiometricProof private constructor() {
    private var available = true

    @Synchronized
    fun consume(): Boolean {
        if (!available) return false
        available = false
        return true
    }

    companion object {
        internal fun issue(): VaultBiometricProof = VaultBiometricProof()
    }
}
