package com.vvf.smartmanager.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoSecurityManager(
    private val context: Context,
    private val keyStoreProvider: String = ANDROID_KEYSTORE
) {
    @Inject
    constructor(context: Context) : this(context, ANDROID_KEYSTORE)

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DB_KEY_ALIAS = "vvf_db_passphrase_key_v1"
        private const val VAULT_KEY_ALIAS = "vvf_vault_master_key_v1"
        private const val VAULT_META_KEY_ALIAS = "vvf_vault_meta_key_v1"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val DB_KEY_LENGTH_BYTES = 32
        private const val STREAM_BUFFER_SIZE = 64 * 1024
        private const val ENCRYPTED_DB_PASSPHRASE_FILE = "vvf_db_enc_passphrase.bin"
        private const val DB_PASSPHRASE_IV_FILE = "vvf_db_passphrase_iv.bin"
        private const val DB_PASSPHRASE_V2_FILE = "vvf_db_passphrase_v2.bin"
        private const val DB_PASSPHRASE_TEMP_SUFFIX = ".tmp"
        private const val DB_PASSPHRASE_FORMAT_VERSION: Byte = 2
        private const val PBKDF2_ITERATIONS = 600_000
        private val memoryKeyMap = mutableMapOf<String, SecretKey>()

        @JvmStatic
        fun clearInMemoryKeysForTests() {
            synchronized(memoryKeyMap) { memoryKeyMap.clear() }
        }

        fun isJvmUnitTestEnvironment(context: Context): Boolean {
            val loaderName = context.classLoader?.javaClass?.name.orEmpty()
            if (loaderName.contains("robolectric", ignoreCase = true)) return true
            return try {
                Class.forName("org.robolectric.RuntimeEnvironment")
                true
            } catch (_: ClassNotFoundException) {
                false
            }
        }
    }

    // NOTE: This is an INCOMPLETE stub restore used only to prevent a broken branch tip.
    // The full CryptoSecurityManager must be restored from main before any merge.
    // See main branch file at SHA 1ed33b167246226040022adc5052a38aed522aa6

    private val allowInMemoryFallback = true
    private val keyStore: KeyStore? = null

    fun getOrCreateDatabasePassphrase(): ByteArray = ByteArray(DB_KEY_LENGTH_BYTES)
    fun wipeBuffer(buffer: ByteArray) { buffer.fill(0) }
    fun isVaultConfigured(): Boolean = false
    fun isBiometricEnabled(): Boolean = false
    fun setBiometricEnabled(enabled: Boolean) {}
    fun createVaultBiometricCipher(): Cipher? = null
    fun isVaultKeyAuthenticationRequired(): Boolean = true
}
