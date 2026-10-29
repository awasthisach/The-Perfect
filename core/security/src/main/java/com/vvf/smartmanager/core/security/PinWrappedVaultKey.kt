package com.vvf.smartmanager.core.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Versioned, authenticated PIN envelope for a random vault data key.
 *
 * The PIN never becomes the vault data key. A random 256-bit key is wrapped with
 * PBKDF2-HMAC-SHA256 + AES-256-GCM; the header is authenticated as AAD so format
 * or parameter changes cannot be silently downgraded.
 */
internal class PinWrappedVaultKey(
    private val random: SecureRandom = SecureRandom(),
    private val iterations: Int = DEFAULT_ITERATIONS
) {
    init {
        require(iterations >= MIN_ITERATIONS) { "PIN KDF work factor is below the security minimum" }
    }

    fun wrap(dataKey: ByteArray, pin: CharArray): ByteArray {
        require(dataKey.size == DATA_KEY_BYTES) { "Vault data key must be 256 bits" }
        require(pin.isNotEmpty()) { "Vault PIN must not be empty" }
        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = byteArrayOf(MAGIC_0, MAGIC_1, VERSION) + intBytes(iterations) + salt + nonce
        val derived = derive(pin, salt, iterations)
        return try {
            val cipher = Cipher.getInstance(TRANSFORMATION)
            cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(TAG_BITS, nonce))
            cipher.updateAAD(header)
            header + cipher.doFinal(dataKey)
        } finally {
            derived.fill(0)
            pin.fill('\u0000')
        }
    }

    fun unwrap(envelope: ByteArray, pin: CharArray): ByteArray {
        require(pin.isNotEmpty()) { "Vault PIN must not be empty" }
        try {
            require(envelope.size == HEADER_BYTES + DATA_KEY_BYTES + TAG_BYTES) {
                "Invalid PIN-wrapped vault key envelope length"
            }
            require(envelope[0] == MAGIC_0 && envelope[1] == MAGIC_1 && envelope[2] == VERSION) {
                "Unsupported PIN-wrapped vault key version"
            }
            val workFactor = readInt(envelope, 3)
            require(workFactor in MIN_ITERATIONS..MAX_ITERATIONS) { "Unsupported PIN KDF work factor" }
            val saltStart = 7
            val nonceStart = saltStart + SALT_BYTES
            val salt = envelope.copyOfRange(saltStart, nonceStart)
            val nonce = envelope.copyOfRange(nonceStart, nonceStart + NONCE_BYTES)
            val header = envelope.copyOfRange(0, HEADER_BYTES)
            val ciphertext = envelope.copyOfRange(HEADER_BYTES, envelope.size)
            val derived = derive(pin, salt, workFactor)
            return try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(TAG_BITS, nonce))
                cipher.updateAAD(header)
                cipher.doFinal(ciphertext).also {
                    if (it.size != DATA_KEY_BYTES) {
                        it.fill(0)
                        throw SecurityException("Invalid unwrapped vault data key length")
                    }
                }
            } catch (error: AEADBadTagException) {
                throw SecurityException("Vault PIN or key envelope authentication is invalid", error)
            } finally {
                derived.fill(0)
            }
        } finally {
            pin.fill('\u0000')
        }
    }

    private fun derive(pin: CharArray, salt: ByteArray, workFactor: Int): ByteArray {
        val spec = PBEKeySpec(pin, salt, workFactor, DATA_KEY_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
    }

    private fun intBytes(value: Int): ByteArray = byteArrayOf(
        (value ushr 24).toByte(), (value ushr 16).toByte(),
        (value ushr 8).toByte(), value.toByte()
    )

    private fun readInt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and 0xff) shl 24) or
            ((bytes[offset + 1].toInt() and 0xff) shl 16) or
            ((bytes[offset + 2].toInt() and 0xff) shl 8) or
            (bytes[offset + 3].toInt() and 0xff)

    companion object {
        private const val MAGIC_0: Byte = 0x56
        private const val MAGIC_1: Byte = 0x4b
        private const val VERSION: Byte = 1
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val DATA_KEY_BYTES = 32
        private const val SALT_BYTES = 16
        private const val NONCE_BYTES = 12
        private const val TAG_BITS = 128
        private const val TAG_BYTES = TAG_BITS / 8
        private const val HEADER_BYTES = 7 + SALT_BYTES + NONCE_BYTES
        private const val DEFAULT_ITERATIONS = 600_000
        private const val MIN_ITERATIONS = 600_000
        private const val MAX_ITERATIONS = 2_000_000
    }
}
