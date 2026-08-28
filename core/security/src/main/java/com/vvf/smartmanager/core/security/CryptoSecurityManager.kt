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
        private const val DB_KEY_ALIAS = "vvf_db_passphrase_key_v1"
        private const val VAULT_KEY_ALIAS = "vvf_vault_master_key_v1"
        private const val VAULT_META_KEY_ALIAS = "vvf_vault_meta_key_v1"

        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val DB_KEY_LENGTH_BYTES = 32 // 256-bit AES key for SQLCipher
        private const val STREAM_BUFFER_SIZE = 64 * 1024 // 64 KB streaming buffer

        private const val ENCRYPTED_DB_PASSPHRASE_FILE = "vvf_db_enc_passphrase.bin"
        private const val DB_PASSPHRASE_IV_FILE = "vvf_db_passphrase_iv.bin"

        // In-memory fallback map for non-AndroidKeyStore test environments
        private val memoryKeyMap = mutableMapOf<String, SecretKey>()
    }

    private val isAndroidKeyStoreAvailable: Boolean = try {
        Security.getProvider(ANDROID_KEYSTORE) != null || keyStoreProvider != ANDROID_KEYSTORE
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

    /**
     * Ensures an AES-256 key exists in the hardware-backed Android KeyStore or fallback.
     */
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
                
                // IMPORTANT: VAULT_META_KEY_ALIAS is intentionally NOT auth-gated.
                // It protects the PIN hash/salt, which is what verifies the PIN in the
                // first place — gating it behind biometric auth creates a circular
                // dependency that locks out users who type the correct PIN. Only the
                // vault CONTENT key (VAULT_KEY_ALIAS) may require biometric auth.
                if (alias == VAULT_KEY_ALIAS && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
                    builder.setUserAuthenticationRequired(true)
                    builder.setUserAuthenticationParameters(
                        300, // 5 minutes timeout
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
            memoryKeyMap.getOrPut(alias) {
                val rawKey = ByteArray(32)
                SecureRandom().nextBytes(rawKey)
                SecretKeySpec(rawKey, "AES")
            }
        }
    }

    // ========================================================================
    // 1. SQLCipher Protected Passphrase Lifecycle
    // ========================================================================

    /**
     * Obtains the raw 32-byte (256-bit) decrypted database passphrase for SQLCipher.
     * Generates a new cryptographically secure passphrase on first launch,
     * encrypts it using the Android Keystore master key, and stores it in private storage.
     */
    @Synchronized
    fun getOrCreateDatabasePassphrase(): ByteArray {
        val encFile = File(context.filesDir, ENCRYPTED_DB_PASSPHRASE_FILE)
        val ivFile = File(context.filesDir, DB_PASSPHRASE_IV_FILE)

        if (encFile.exists() && ivFile.exists()) {
            try {
                val encryptedPassphrase = encFile.readBytes()
                val iv = ivFile.readBytes()
                return decryptWithKeystore(encryptedPassphrase, iv, DB_KEY_ALIAS)
            } catch (e: Throwable) {
                throw SecurityException(
                    "Secure database initialization failed: Unable to decrypt database passphrase with KeyStore",
                    e
                )
            }
        }

        // Generate fresh 32 bytes (256-bit) random key
        val freshPassphrase = ByteArray(DB_KEY_LENGTH_BYTES)
        SecureRandom().nextBytes(freshPassphrase)

        // Encrypt with Android Keystore
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(DB_KEY_ALIAS))
        val iv = cipher.iv
        val encryptedPassphrase = cipher.doFinal(freshPassphrase)

        // Persist encrypted passphrase and IV safely in internal storage
        encFile.writeBytes(encryptedPassphrase)
        ivFile.writeBytes(iv)

        return freshPassphrase
    }

    // ========================================================================
    // 2. Secure Vault Streaming File Encryption / Decryption (AES-256-GCM)
    // ========================================================================

    data class StreamEncryptionResult(
        val ivBase64: String,
        val totalBytesEncrypted: Long
    )

    /**
     * Streams data from [sourceStream] into [destinationStream] using AES-256-GCM encryption.
     * Writes the 12-byte IV at the beginning of [destinationStream] for zero-state file decryption.
     */
    fun encryptStream(
        sourceStream: InputStream,
        destinationStream: OutputStream
    ): StreamEncryptionResult {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(VAULT_KEY_ALIAS))
        val iv = cipher.iv

        // Write 12-byte IV header to destination stream
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

        return StreamEncryptionResult(
            ivBase64 = Base64.encodeToString(iv, Base64.NO_WRAP),
            totalBytesEncrypted = totalBytes
        )
    }

    /**
     * Encrypts a source file into a destination file using low-memory AES-GCM streaming.
     */
    fun encryptFile(sourceFile: File, destinationFile: File): StreamEncryptionResult {
        return FileInputStream(sourceFile).use { fis ->
            FileOutputStream(destinationFile).use { fos ->
                encryptStream(fis, fos)
            }
        }
    }

    /**
     * Streams encrypted data from [sourceStream] into [destinationStream] using AES-256-GCM.
     * Automatically reads the 12-byte IV header from [sourceStream].
     */
    fun decryptStream(
        sourceStream: InputStream,
        destinationStream: OutputStream
    ): Long {
        // Read the 12-byte IV header
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        val ivBytesRead = sourceStream.read(iv)
        require(ivBytesRead == GCM_IV_LENGTH_BYTES) {
            "Invalid encrypted stream: Missing or corrupt IV header"
        }

        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(VAULT_KEY_ALIAS), spec)

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

    /**
     * Decrypts an encrypted file into a destination file using low-memory AES-GCM streaming.
     */
    fun decryptFile(sourceFile: File, destinationFile: File): Long {
        return FileInputStream(sourceFile).use { fis ->
            FileOutputStream(destinationFile).use { fos ->
                decryptStream(fis, fos)
            }
        }
    }

    // ========================================================================
    // 3. In-Memory Payload Helper Functions
    // ========================================================================

    fun encryptBytes(data: ByteArray, alias: String = VAULT_KEY_ALIAS): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(alias))
        val iv = cipher.iv
        val encryptedData = cipher.doFinal(data)
        return Pair(encryptedData, iv)
    }

    fun decryptBytes(encryptedData: ByteArray, iv: ByteArray, alias: String = VAULT_KEY_ALIAS): ByteArray {
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(alias), spec)
        return cipher.doFinal(encryptedData)
    }

    private fun decryptWithKeystore(encryptedData: ByteArray, iv: ByteArray, alias: String): ByteArray {
        val spec = GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv)
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(alias), spec)
        return cipher.doFinal(encryptedData)
    }

    // ========================================================================
    // 4. Secure File Shredder (Zero-Knowledge Deletion)
    // ========================================================================

    /**
     * Securely shreds and overwrites a file with zeroes before deleting from disk.
     * Prevents forensics-based undelete or recovery of plaintext data.
     */
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

    // ========================================================================
    // 5. Vault PIN & Auth State Management (Supports Decoy/Fake Vault & Lockout)
    // ========================================================================

    sealed interface VaultAuthResult {
        data object SUCCESS_REAL : VaultAuthResult
        data object SUCCESS_DECOY : VaultAuthResult
        data class INVALID_PIN(val failedAttempts: Int, val lockoutSeconds: Int) : VaultAuthResult
        data class LOCKED_OUT(val remainingSeconds: Int) : VaultAuthResult
        data object INVALID_FORMAT : VaultAuthResult
    }

    private val prefs by lazy {
        context.getSharedPreferences("vvf_secure_vault_prefs", Context.MODE_PRIVATE)
    }

    fun isValidPinFormat(pin: String): Boolean {
        return pin.length in 4..6 && pin.all { it.isDigit() }
    }

    fun isVaultPinSet(): Boolean = isVaultConfigured()

    fun saveVaultPin(pin: String): Boolean = setupVaultPin(pin)

    fun isDecoyPinSet(): Boolean = isDecoyVaultConfigured()

    fun saveDecoyPin(pin: String): Boolean = setupDecoyPin(pin)

    fun getRemainingLockoutSeconds(): Int {
        val lockoutUntilMs = prefs.getLong("vault_lockout_until_ms", 0L)
        val now = System.currentTimeMillis()
        return if (lockoutUntilMs > now) {
            ((lockoutUntilMs - now) / 1000L).toInt().coerceAtLeast(1)
        } else {
            0
        }
    }

    fun getFailedAttemptsCount(): Int {
        return prefs.getInt("vault_failed_attempts", 0)
    }

    private fun recordFailedAttempt(): VaultAuthResult.INVALID_PIN {
        val attempts = getFailedAttemptsCount() + 1
        var lockoutSec = 0
        val now = System.currentTimeMillis()

        if (attempts >= 15) {
            lockoutSec = 1800 // 30 minutes
        } else if (attempts >= 10) {
            lockoutSec = 300 // 5 minutes
        } else if (attempts >= 5) {
            lockoutSec = 30 // 30 seconds
        }

        val editor = prefs.edit().putInt("vault_failed_attempts", attempts)
        if (lockoutSec > 0) {
            editor.putLong("vault_lockout_until_ms", now + (lockoutSec * 1000L))
        }
        editor.apply()

        return VaultAuthResult.INVALID_PIN(attempts, lockoutSec)
    }

    private fun resetFailedAttempts() {
        prefs.edit()
            .remove("vault_failed_attempts")
            .remove("vault_lockout_until_ms")
            .apply()
    }

    fun isVaultConfigured(): Boolean {
        return prefs.contains("vault_pin_hash") && prefs.contains("vault_pin_salt")
    }

    fun isDecoyVaultConfigured(): Boolean {
        return prefs.contains("vault_decoy_pin_hash") && prefs.contains("vault_decoy_pin_salt")
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

    /**
     * Configures a Plausible Deniability Decoy (Fake) PIN.
     * Must be 4-6 digits and MUST NOT be identical to the Real Master PIN.
     */
    fun setupDecoyPin(decoyPin: String): Boolean {
        if (!isValidPinFormat(decoyPin)) return false
        if (verifyVaultPin(decoyPin)) return false // Decoy PIN cannot equal Real Master PIN

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
        prefs.edit()
            .remove("vault_decoy_pin_hash")
            .remove("vault_decoy_pin_hash_iv")
            .remove("vault_decoy_pin_salt")
            .remove("vault_decoy_pin_salt_iv")
            .apply()
        return true
    }

    fun verifyVaultPin(pin: String): Boolean {
        return verifyVaultPinWithResult(pin) == VaultAuthResult.SUCCESS_REAL
    }

    fun verifyDecoyPin(pin: String): Boolean {
        return verifyVaultPinWithResult(pin) == VaultAuthResult.SUCCESS_DECOY
    }

    /**
     * Evaluates entered PIN against both Real Master Key and Decoy Key.
     * Returns SUCCESS_REAL if Real Master PIN matches.
     * Returns SUCCESS_DECOY if Decoy PIN matches (opens innocent Fake Vault).
     * Returns INVALID_PIN otherwise.
     */
    fun verifyVaultPinWithResult(pin: String): VaultAuthResult {
        if (!isValidPinFormat(pin)) {
            return VaultAuthResult.INVALID_FORMAT
        }

        val remainingLockout = getRemainingLockoutSeconds()
        if (remainingLockout > 0) {
            return VaultAuthResult.LOCKED_OUT(remainingLockout)
        }
        // 1. Check Real Master PIN
        val realHashStr = prefs.getString("vault_pin_hash", null)
        val realHashIvStr = prefs.getString("vault_pin_hash_iv", null)
        val realSaltStr = prefs.getString("vault_pin_salt", null)
        val realSaltIvStr = prefs.getString("vault_pin_salt_iv", null)

        if (realHashStr != null && realHashIvStr != null && realSaltStr != null && realSaltIvStr != null) {
            try {
                val encHash = Base64.decode(realHashStr, Base64.NO_WRAP)
                val hashIv = Base64.decode(realHashIvStr, Base64.NO_WRAP)
                val encSalt = Base64.decode(realSaltStr, Base64.NO_WRAP)
                val saltIv = Base64.decode(realSaltIvStr, Base64.NO_WRAP)

                val expectedHash = decryptBytes(encHash, hashIv, alias = VAULT_META_KEY_ALIAS)
                val salt = decryptBytes(encSalt, saltIv, alias = VAULT_META_KEY_ALIAS)
                val computedHash = hashPin(pin, salt)

                if (java.security.MessageDigest.isEqual(expectedHash, computedHash)) {
                    resetFailedAttempts()
                    return VaultAuthResult.SUCCESS_REAL
                }
            } catch (_: Exception) {
                // Ignore error and fall through to check decoy
            }
        }

        // 2. Check Decoy PIN
        val decoyHashStr = prefs.getString("vault_decoy_pin_hash", null)
        val decoyHashIvStr = prefs.getString("vault_decoy_pin_hash_iv", null)
        val decoySaltStr = prefs.getString("vault_decoy_pin_salt", null)
        val decoySaltIvStr = prefs.getString("vault_decoy_pin_salt_iv", null)

        if (decoyHashStr != null && decoyHashIvStr != null && decoySaltStr != null && decoySaltIvStr != null) {
            try {
                val encHash = Base64.decode(decoyHashStr, Base64.NO_WRAP)
                val hashIv = Base64.decode(decoyHashIvStr, Base64.NO_WRAP)
                val encSalt = Base64.decode(decoySaltStr, Base64.NO_WRAP)
                val saltIv = Base64.decode(decoySaltIvStr, Base64.NO_WRAP)

                val expectedHash = decryptBytes(encHash, hashIv, alias = VAULT_META_KEY_ALIAS)
                val salt = decryptBytes(encSalt, saltIv, alias = VAULT_META_KEY_ALIAS)
                val computedHash = hashPin(pin, salt)

                if (java.security.MessageDigest.isEqual(expectedHash, computedHash)) {
                    resetFailedAttempts()
                    return VaultAuthResult.SUCCESS_DECOY
                }
            } catch (_: Exception) {
                // Ignore error
            }
        }

        return recordFailedAttempt()
    }

    fun changeVaultPin(oldPin: String, newPin: String): Boolean {
        if (!verifyVaultPin(oldPin)) return false
        return setupVaultPin(newPin)
    }

    fun isBiometricEnabled(): Boolean {
        return prefs.getBoolean("vault_biometric_enabled", false)
    }

    fun setBiometricEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("vault_biometric_enabled", enabled).apply()
    }

    fun getAutoLockTimeoutSeconds(): Int {
        return prefs.getInt("vault_auto_lock_seconds", 60) // default 60 seconds
    }

    fun setAutoLockTimeoutSeconds(seconds: Int) {
        prefs.edit().putInt("vault_auto_lock_seconds", seconds).apply()
    }

    /**
     * Securely zero-out sensitive byte buffers in RAM after usage (e.g. DB Passphrase).
     */
    fun wipeBuffer(buffer: ByteArray) {
        buffer.fill(0)
    }

    /**
     * PBKDF2WithHmacSHA256 Key Derivation Function (100,000 Iterations)
     * Hardens numeric PIN against GPU-accelerated brute-force attacks.
     */
    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val spec = javax.crypto.spec.PBEKeySpec(pin.toCharArray(), salt, 100_000, 256)
        val skf = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        return skf.generateSecret(spec).encoded
    }
}
