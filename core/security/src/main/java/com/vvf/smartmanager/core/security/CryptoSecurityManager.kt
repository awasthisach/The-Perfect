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

/**
 * Enterprise-grade Cryptographic Security Manager for VVF Smart Manager.
 *
 * Implements:
 * 1. Hardware-backed Android Keystore Master Keys (AES-256-GCM).
 * 2. Protected Random 256-bit DB Passphrase generation and storage for SQLCipher.
 * 3. Low-Memory Streaming File Encryption/Decryption (64 KB buffers) for Secure Vault.
 * 4. In-Memory byte payload encryption for sensitive metadata/tokens.
 *
 * Fail-closed: production builds never fall back to in-memory keys when
 * AndroidKeyStore is unavailable. Robolectric JVM unit tests are the sole
 * exception (detected via classloader name), so Application wiring can be
 * exercised without hardware-backed keystore.
 */
@Singleton
class CryptoSecurityManager(
    private val context: Context,
    private val keyStoreProvider: String = ANDROID_KEYSTORE
) {

    @Inject
    constructor(context: Context) : this(context, ANDROID_KEYSTORE)

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        /**
         * Database master key. Deliberately does NOT require user authentication:
         * the SQLCipher DB must open at app startup without a biometric prompt.
         * Metadata protection relies on AndroidKeyStore hardware binding, not user
         * presence. Rotating this key invalidates the stored DB passphrase file.
         */
        private const val DB_KEY_ALIAS = "vvf_db_passphrase_key_v1"
        private const val VAULT_KEY_ALIAS = "vvf_vault_master_key_v1"
        /**
         * Vault metadata key (PIN hash/salt encryption). No user auth required:
         * it must be usable while evaluating PIN entry before unlock. Compromise
         * does not expose vault file contents.
         */
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
        /**
         * JVM/Robolectric-only fallback keys. Production refuses this path
         * ([allowInMemoryFallback] is false when AndroidKeyStore is required).
         * Cleared via [clearInMemoryKeysForTests] between unit tests.
         */
        private val memoryKeyMap = mutableMapOf<String, SecretKey>()

        /** Test isolation: drop in-memory fallback keys (no-op impact on device Keystore). */
        @JvmStatic
        fun clearInMemoryKeysForTests() {
            synchronized(memoryKeyMap) {
                memoryKeyMap.clear()
            }
        }

        /**
         * Public so composition roots in other modules (e.g. :app) can select
         * in-memory Room under Robolectric without loading SQLCipher natives.
         * Must never return true on a real device runtime classloader.
         */
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

    private val allowInMemoryFallback: Boolean =
        keyStoreProvider != ANDROID_KEYSTORE || isJvmUnitTestEnvironment(context)

    private val isAndroidKeyStoreAvailable: Boolean = try {
        Security.getProvider(ANDROID_KEYSTORE) != null
    } catch (_: Exception) {
        false
    }

    private val keyStore: KeyStore? = if (isAndroidKeyStoreAvailable && keyStoreProvider == ANDROID_KEYSTORE) {
        try {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        } catch (_: Exception) {
            null
        }
    } else {
        null
    }

    init {
        ensureMasterKeyExists(DB_KEY_ALIAS)
        ensureMasterKeyExists(VAULT_KEY_ALIAS)
        ensureMasterKeyExists(VAULT_META_KEY_ALIAS)
    }

    private fun ensureMasterKeyExists(alias: String) {
        val ks = keyStore
        if (ks != null) {
            if (!ks.containsAlias(alias)) {
                val keyGenerator = KeyGenerator.getInstance(
                    KeyProperties.KEY_ALGORITHM_AES,
                    ANDROID_KEYSTORE
                )
                val builder = KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)

                if (alias == VAULT_KEY_ALIAS && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    builder.setUserAuthenticationRequired(true)
                    builder.setUserAuthenticationParameters(
                        300,
                        KeyProperties.AUTH_BIOMETRIC_STRONG or KeyProperties.AUTH_DEVICE_CREDENTIAL
                    )
                } else if (alias == VAULT_KEY_ALIAS && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    builder.setUserAuthenticationRequired(true)
                    @Suppress("DEPRECATION")
                    builder.setUserAuthenticationValidityDurationSeconds(300)
                }

                keyGenerator.init(builder.build())
                keyGenerator.generateKey()
            }
        } else {
            if (!allowInMemoryFallback) {
                throw IllegalStateException(
                    "AndroidKeyStore is unavailable in a production environment; refusing to use in-memory keys"
                )
            }
            if (!memoryKeyMap.containsKey(alias)) {
                val rawKey = ByteArray(32)
                SecureRandom().nextBytes(rawKey)
                memoryKeyMap[alias] = SecretKeySpec(rawKey, "AES")
            }
        }
    }

    private fun getSecretKey(alias: String): SecretKey {
        val ks = keyStore
        return if (ks != null && ks.containsAlias(alias)) {
            (ks.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
        } else {
            if (!allowInMemoryFallback) {
                throw IllegalStateException("KeyStore unavailable for alias: $alias")
            }
            memoryKeyMap.getOrPut(alias) {
                val rawKey = ByteArray(32)
                SecureRandom().nextBytes(rawKey)
                SecretKeySpec(rawKey, "AES")
            }
        }
    }

    @Synchronized
    fun getOrCreateDatabasePassphrase(): ByteArray {
        val v2File = File(context.filesDir, DB_PASSPHRASE_V2_FILE)
        val legacyEncFile = File(context.filesDir, ENCRYPTED_DB_PASSPHRASE_FILE)
        val legacyIvFile = File(context.filesDir, DB_PASSPHRASE_IV_FILE)

        if (v2File.exists()) {
            return try {
                readV2DatabasePassphrase(v2File)
            } catch (e: Throwable) {
                throw SecurityException(
                    "Secure database initialization failed: invalid v2 database passphrase file",
                    e
                )
            }
        }

        if (legacyEncFile.exists() || legacyIvFile.exists()) {
            if (!legacyEncFile.exists() || !legacyIvFile.exists()) {
                throw SecurityException("Secure database initialization failed: incomplete legacy passphrase files")
            }

            val migratedPassphrase = try {
                decryptWithKeystore(legacyEncFile.readBytes(), legacyIvFile.readBytes(), DB_KEY_ALIAS)
            } catch (e: Throwable) {
                throw SecurityException(
                    "Secure database initialization failed: unable to decrypt legacy database passphrase",
                    e
                )
            }

            writeV2DatabasePassphraseAtomically(v2File, migratedPassphrase)
            val verified = readV2DatabasePassphrase(v2File)
            if (!MessageDigest.isEqual(migratedPassphrase, verified)) {
                throw SecurityException("Secure database passphrase migration verification failed")
            }

            check(legacyEncFile.delete()) { "Failed to remove legacy encrypted database passphrase" }
            check(legacyIvFile.delete()) { "Failed to remove legacy database passphrase IV" }
            return migratedPassphrase
        }

        val freshPassphrase = ByteArray(DB_KEY_LENGTH_BYTES)
        SecureRandom().nextBytes(freshPassphrase)
        writeV2DatabasePassphraseAtomically(v2File, freshPassphrase)
        val verified = readV2DatabasePassphrase(v2File)
        if (!MessageDigest.isEqual(freshPassphrase, verified)) {
            throw SecurityException("Secure database passphrase write verification failed")
        }
        return freshPassphrase
    }

    private fun readV2DatabasePassphrase(file: File): ByteArray {
        val bytes = file.readBytes()
        val minimumSize = 1 + GCM_IV_LENGTH_BYTES + 16
        require(bytes.size >= minimumSize) { "Invalid v2 database passphrase file" }
        require(bytes[0] == DB_PASSPHRASE_FORMAT_VERSION) { "Unsupported database passphrase version" }

        val iv = bytes.copyOfRange(1, 1 + GCM_IV_LENGTH_BYTES)
        val encrypted = bytes.copyOfRange(1 + GCM_IV_LENGTH_BYTES, bytes.size)
        return decryptWithKeystore(encrypted, iv, DB_KEY_ALIAS)
    }

    private fun writeV2DatabasePassphraseAtomically(file: File, passphrase: ByteArray) {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(DB_KEY_ALIAS))
        val iv = cipher.iv
        val encrypted = cipher.doFinal(passphrase)
        val payload = ByteArray(1 + iv.size + encrypted.size)
        payload[0] = DB_PASSPHRASE_FORMAT_VERSION
        System.arraycopy(iv, 0, payload, 1, iv.size)
        System.arraycopy(encrypted, 0, payload, 1 + iv.size, encrypted.size)

        val tempFile = File(file.parentFile, file.name + DB_PASSPHRASE_TEMP_SUFFIX)
        try {
            FileOutputStream(tempFile).use { output ->
                output.write(payload)
                output.flush()
                output.fd.sync()
            }
            check(tempFile.renameTo(file)) { "Failed to atomically replace database passphrase file" }
        } catch (e: Throwable) {
            tempFile.delete()
            throw e
        }
    }

    data class StreamEncryptionResult(
        val ivBase64: String,
        val totalBytesEncrypted: Long
    )

    fun encryptStream(sourceStream: InputStream, destinationStream: OutputStream): StreamEncryptionResult {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(VAULT_KEY_ALIAS))
        val iv = cipher.iv
        destinationStream.write(iv)

        var totalBytes: Long = 0
        CipherOutputStream(destinationStream, cipher).use { cos ->
            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            var bytesRead: Int
            while (sourceStream.read(buffer).also { bytesRead = it } != -1) {
                cos.write(buffer, 0, bytesRead)
                totalBytes += bytesRead
            }
            cos.flush()
        }
        return StreamEncryptionResult(Base64.encodeToString(iv, Base64.NO_WRAP), totalBytes)
    }

    fun encryptFile(sourceFile: File, destinationFile: File): StreamEncryptionResult =
        FileInputStream(sourceFile).use { fis -> FileOutputStream(destinationFile).use { fos -> encryptStream(fis, fos) } }

    fun decryptStream(sourceStream: InputStream, destinationStream: OutputStream): Long {
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        require(sourceStream.read(iv) == GCM_IV_LENGTH_BYTES) { "Invalid encrypted stream: Missing or corrupt IV header" }
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(VAULT_KEY_ALIAS), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))

        var totalBytes: Long = 0
        CipherInputStream(sourceStream, cipher).use { cis ->
            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            var bytesRead: Int
            while (cis.read(buffer).also { bytesRead = it } != -1) {
                destinationStream.write(buffer, 0, bytesRead)
                totalBytes += bytesRead
            }
            destinationStream.flush()
        }
        return totalBytes
    }

    fun decryptFile(sourceFile: File, destinationFile: File): Long =
        FileInputStream(sourceFile).use { fis -> FileOutputStream(destinationFile).use { fos -> decryptStream(fis, fos) } }

    fun encryptBytes(data: ByteArray, alias: String = VAULT_KEY_ALIAS): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(alias))
        return Pair(cipher.doFinal(data), cipher.iv)
    }

    fun decryptBytes(encryptedData: ByteArray, iv: ByteArray, alias: String = VAULT_KEY_ALIAS): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(alias), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(encryptedData)
    }

    private fun decryptWithKeystore(encryptedData: ByteArray, iv: ByteArray, alias: String): ByteArray =
        decryptBytes(encryptedData, iv, alias)

    fun secureShredFile(file: File): Boolean {
        if (!file.exists()) return true
        return try {
            if (file.isFile && file.length() > 0) {
                val length = file.length()
                FileOutputStream(file).use { fos ->
                    val zeroBuffer = ByteArray(STREAM_BUFFER_SIZE)
                    var written = 0L
                    while (written < length) {
                        val toWrite = minOf(zeroBuffer.size.toLong(), length - written).toInt()
                        fos.write(zeroBuffer, 0, toWrite)
                        written += toWrite
                    }
                    fos.flush()
                    fos.fd.sync()
                }
            }
            file.delete()
        } catch (_: Exception) {
            file.delete()
        }
    }

    sealed interface VaultAuthResult {
        data object SUCCESS_REAL : VaultAuthResult
        data object SUCCESS_DECOY : VaultAuthResult
        data class INVALID_PIN(val failedAttempts: Int, val lockoutSeconds: Int) : VaultAuthResult
        data class LOCKED_OUT(val remainingSeconds: Int) : VaultAuthResult
        data object INVALID_FORMAT : VaultAuthResult
    }

    private val prefs by lazy { context.getSharedPreferences("vvf_secure_vault_prefs", Context.MODE_PRIVATE) }

    fun isValidPinFormat(pin: String): Boolean = pin.length in 4..6 && pin.all { it.isDigit() }
    fun isVaultPinSet(): Boolean = isVaultConfigured()
    fun saveVaultPin(pin: String): Boolean = setupVaultPin(pin)
    fun isDecoyPinSet(): Boolean = isDecoyVaultConfigured()
    fun saveDecoyPin(pin: String): Boolean = setupDecoyPin(pin)

    fun getRemainingLockoutSeconds(): Int {
        val lockoutUntilMs = prefs.getLong("vault_lockout_until_ms", 0L)
        val now = System.currentTimeMillis()
        return if (lockoutUntilMs > now) ((lockoutUntilMs - now) / 1000L).toInt().coerceAtLeast(1) else 0
    }

    fun getFailedAttemptsCount(): Int = prefs.getInt("vault_failed_attempts", 0)

    private fun recordFailedAttempt(): VaultAuthResult.INVALID_PIN {
        val attempts = getFailedAttemptsCount() + 1
        val lockoutSec = when {
            attempts >= 15 -> 1800
            attempts >= 10 -> 300
            attempts >= 5 -> 30
            else -> 0
        }
        val editor = prefs.edit().putInt("vault_failed_attempts", attempts)
        if (lockoutSec > 0) editor.putLong("vault_lockout_until_ms", System.currentTimeMillis() + lockoutSec * 1000L)
        editor.apply()
        return VaultAuthResult.INVALID_PIN(attempts, lockoutSec)
    }

    private fun resetFailedAttempts() {
        prefs.edit().remove("vault_failed_attempts").remove("vault_lockout_until_ms").apply()
    }

    fun isVaultConfigured(): Boolean = prefs.contains("vault_pin_hash") && prefs.contains("vault_pin_salt")
    fun isDecoyVaultConfigured(): Boolean = prefs.contains("vault_decoy_pin_hash") && prefs.contains("vault_decoy_pin_salt")

    /**
     * Export vault auth material already stored as Keystore-wrapped ciphertext in prefs.
     * Safe to include inside an encrypted backup archive (not plaintext PINs).
     */
    fun exportVaultAuthMetadata(): Map<String, String> {
        val keys = listOf(
            "vault_pin_hash", "vault_pin_hash_iv", "vault_pin_salt", "vault_pin_salt_iv",
            "vault_decoy_pin_hash", "vault_decoy_pin_hash_iv", "vault_decoy_pin_salt", "vault_decoy_pin_salt_iv",
            "vault_biometric_enabled"
        )
        val out = linkedMapOf<String, String>()
        for (key in keys) {
            when (val value = prefs.all[key]) {
                is String -> out[key] = value
                is Boolean -> out[key] = value.toString()
            }
        }
        return out
    }

    /** Restore vault auth metadata from backup. Does not accept raw PIN values. */
    fun importVaultAuthMetadata(metadata: Map<String, String>): Boolean {
        if (metadata.isEmpty()) return false
        val editor = prefs.edit()
        var wrote = false
        metadata.forEach { (key, value) ->
            when (key) {
                "vault_biometric_enabled" -> {
                    editor.putBoolean(key, value.equals("true", ignoreCase = true))
                    wrote = true
                }
                "vault_pin_hash", "vault_pin_hash_iv", "vault_pin_salt", "vault_pin_salt_iv",
                "vault_decoy_pin_hash", "vault_decoy_pin_hash_iv", "vault_decoy_pin_salt", "vault_decoy_pin_salt_iv" -> {
                    editor.putString(key, value)
                    wrote = true
                }
            }
        }
        return if (wrote) editor.commit() else false
    }


    fun setupVaultPin(pin: String): Boolean {
        if (!isValidPinFormat(pin)) return false
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val hash = hashPin(pin, salt)
        val (encHash, hashIv) = encryptBytes(hash, alias = VAULT_META_KEY_ALIAS)
        val (encSalt, saltIv) = encryptBytes(salt, alias = VAULT_META_KEY_ALIAS)
        prefs.edit()
            .putString("vault_pin_hash", Base64.encodeToString(encHash, Base64.NO_WRAP))
            .putString("vault_pin_hash_iv", Base64.encodeToString(hashIv, Base64.NO_WRAP))
            .putString("vault_pin_salt", Base64.encodeToString(encSalt, Base64.NO_WRAP))
            .putString("vault_pin_salt_iv", Base64.encodeToString(saltIv, Base64.NO_WRAP))
            .apply()
        return true
    }

    fun setupDecoyPin(decoyPin: String): Boolean {
        if (!isValidPinFormat(decoyPin)) return false
        // Decoy setup must not consume the lockout budget when the candidate is not the real PIN.
        if (matchesRealPin(decoyPin)) return false
        val salt = ByteArray(16)
        SecureRandom().nextBytes(salt)
        val hash = hashPin(decoyPin, salt)
        val (encHash, hashIv) = encryptBytes(hash, alias = VAULT_META_KEY_ALIAS)
        val (encSalt, saltIv) = encryptBytes(salt, alias = VAULT_META_KEY_ALIAS)
        prefs.edit()
            .putString("vault_decoy_pin_hash", Base64.encodeToString(encHash, Base64.NO_WRAP))
            .putString("vault_decoy_pin_hash_iv", Base64.encodeToString(hashIv, Base64.NO_WRAP))
            .putString("vault_decoy_pin_salt", Base64.encodeToString(encSalt, Base64.NO_WRAP))
            .putString("vault_decoy_pin_salt_iv", Base64.encodeToString(saltIv, Base64.NO_WRAP))
            .apply()
        return true
    }

    fun removeDecoyPin(): Boolean {
        prefs.edit().remove("vault_decoy_pin_hash").remove("vault_decoy_pin_hash_iv")
            .remove("vault_decoy_pin_salt").remove("vault_decoy_pin_salt_iv").apply()
        return true
    }

    fun verifyVaultPin(pin: String): Boolean = verifyVaultPinWithResult(pin) == VaultAuthResult.SUCCESS_REAL
    fun verifyDecoyPin(pin: String): Boolean = verifyVaultPinWithResult(pin) == VaultAuthResult.SUCCESS_DECOY

    /**
     * Compares a candidate with the real PIN without recording a failed unlock attempt.
     * This is exclusively for validating a new decoy PIN before it is stored.
     */
    fun isSameAsVaultPin(pin: String): Boolean = isValidPinFormat(pin) && matchesRealPin(pin)

    fun verifyVaultPinWithResult(pin: String): VaultAuthResult {
        if (!isValidPinFormat(pin)) return VaultAuthResult.INVALID_FORMAT
        val remainingLockout = getRemainingLockoutSeconds()
        if (remainingLockout > 0) return VaultAuthResult.LOCKED_OUT(remainingLockout)

        if (matchesRealPin(pin)) {
            resetFailedAttempts()
            return VaultAuthResult.SUCCESS_REAL
        }
        if (matchesDecoyPin(pin)) {
            resetFailedAttempts()
            return VaultAuthResult.SUCCESS_DECOY
        }
        return recordFailedAttempt()
    }

    private fun matchesRealPin(pin: String): Boolean = matchesStoredPin(
        pin = pin,
        hashKey = "vault_pin_hash",
        hashIvKey = "vault_pin_hash_iv",
        saltKey = "vault_pin_salt",
        saltIvKey = "vault_pin_salt_iv"
    )

    private fun matchesDecoyPin(pin: String): Boolean = matchesStoredPin(
        pin = pin,
        hashKey = "vault_decoy_pin_hash",
        hashIvKey = "vault_decoy_pin_hash_iv",
        saltKey = "vault_decoy_pin_salt",
        saltIvKey = "vault_decoy_pin_salt_iv"
    )

    private fun matchesStoredPin(
        pin: String,
        hashKey: String,
        hashIvKey: String,
        saltKey: String,
        saltIvKey: String
    ): Boolean {
        val hash = prefs.getString(hashKey, null) ?: return false
        val hashIv = prefs.getString(hashIvKey, null) ?: return false
        val salt = prefs.getString(saltKey, null) ?: return false
        val saltIv = prefs.getString(saltIvKey, null) ?: return false
        return try {
            val expectedHash = decryptBytes(
                Base64.decode(hash, Base64.NO_WRAP),
                Base64.decode(hashIv, Base64.NO_WRAP),
                VAULT_META_KEY_ALIAS
            )
            val decodedSalt = decryptBytes(
                Base64.decode(salt, Base64.NO_WRAP),
                Base64.decode(saltIv, Base64.NO_WRAP),
                VAULT_META_KEY_ALIAS
            )
            MessageDigest.isEqual(expectedHash, hashPin(pin, decodedSalt))
        } catch (_: Exception) {
            false
        }
    }

    fun changeVaultPin(oldPin: String, newPin: String): Boolean {
        if (!verifyVaultPin(oldPin)) return false
        return setupVaultPin(newPin)
    }

    fun isBiometricEnabled(): Boolean = prefs.getBoolean("vault_biometric_enabled", false)
    fun setBiometricEnabled(enabled: Boolean) { prefs.edit().putBoolean("vault_biometric_enabled", enabled).apply() }
    fun getAutoLockTimeoutSeconds(): Int = prefs.getInt("vault_auto_lock_seconds", 60)
    fun setAutoLockTimeoutSeconds(seconds: Int) { prefs.edit().putInt("vault_auto_lock_seconds", seconds).apply() }
    fun wipeBuffer(buffer: ByteArray) { buffer.fill(0) }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val pinChars = pin.toCharArray()
        return try {
            val spec = javax.crypto.spec.PBEKeySpec(pinChars, salt, PBKDF2_ITERATIONS, 256)
            try {
                javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            } finally {
                spec.clearPassword()
            }
        } finally {
            pinChars.fill('\u0000')
        }
    }
}
