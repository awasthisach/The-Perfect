package com.vvf.smartmanager.core.security

import java.io.IOException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Versioned envelope for a random vault data key.
 *
 * The PIN is combined with an app-private device pepper protected by Android Keystore before
 * PBKDF2. This prevents an offline attacker who copies preferences/files from testing a short
 * 4–6 digit PIN without also possessing the device Keystore secret. Portable backups must use
 * their separate high-entropy backup passphrase and must never export this device pepper.
 */
internal class PinWrappedVaultKey(
    private val random: SecureRandom = SecureRandom()
) {
    companion object {
        private val MAGIC = byteArrayOf('V'.code.toByte(), 'V'.code.toByte(), 'P'.code.toByte(), 'K'.code.toByte())
        private const val VERSION: Byte = 1
        private const val SALT_BYTES = 16
        private const val NONCE_BYTES = 12
        private const val KEY_BYTES = 32
        private const val TAG_BITS = 128
        private const val ITERATIONS = 600_000
        private const val HEADER_BYTES = 4 + 1 + SALT_BYTES + NONCE_BYTES
        private const val MIN_ENVELOPE_BYTES = HEADER_BYTES + KEY_BYTES + 16
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }

    fun wrap(dataKey: ByteArray, pin: CharArray, devicePepper: ByteArray): ByteArray {
        require(dataKey.size == KEY_BYTES) { "Vault data key must be 256 bits" }
        require(devicePepper.size >= KEY_BYTES) { "Device pepper must contain at least 256 bits" }
        try {
            val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
            val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
            val header = ByteArray(HEADER_BYTES)
            System.arraycopy(MAGIC, 0, header, 0, MAGIC.size)
            header[4] = VERSION
            System.arraycopy(salt, 0, header, 5, salt.size)
            System.arraycopy(nonce, 0, header, 5 + salt.size, nonce.size)
            val derived = deriveKey(pin, salt, devicePepper)
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.ENCRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(TAG_BITS, nonce))
                cipher.updateAAD(header)
                val encrypted = cipher.doFinal(dataKey)
                return header + encrypted
            } finally {
                derived.fill(0)
                salt.fill(0)
                nonce.fill(0)
            }
        } finally {
            pin.fill('\u0000')
        }
    }

    fun unwrap(envelope: ByteArray, pin: CharArray, devicePepper: ByteArray): ByteArray {
        try {
            if (envelope.size < MIN_ENVELOPE_BYTES) throw IOException("Vault key envelope is truncated")
            if (!envelope.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
                throw IOException("Invalid vault key envelope magic")
            }
            if (envelope[4] != VERSION) throw IOException("Unsupported vault key envelope version")
            require(devicePepper.size >= KEY_BYTES) { "Device pepper must contain at least 256 bits" }
            val salt = envelope.copyOfRange(5, 5 + SALT_BYTES)
            val nonce = envelope.copyOfRange(5 + SALT_BYTES, HEADER_BYTES)
            val header = envelope.copyOfRange(0, HEADER_BYTES)
            val ciphertext = envelope.copyOfRange(HEADER_BYTES, envelope.size)
            val derived = deriveKey(pin, salt, devicePepper)
            try {
                val cipher = Cipher.getInstance(TRANSFORMATION)
                cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(derived, "AES"), GCMParameterSpec(TAG_BITS, nonce))
                cipher.updateAAD(header)
                return cipher.doFinal(ciphertext).also {
                    if (it.size != KEY_BYTES) {
                        it.fill(0)
                        throw IOException("Invalid unwrapped vault data key length")
                    }
                }
            } finally {
                salt.fill(0)
                nonce.fill(0)
                header.fill(0)
                ciphertext.fill(0)
                derived.fill(0)
            }
        } finally {
            pin.fill('\u0000')
        }
    }

    private fun deriveKey(pin: CharArray, salt: ByteArray, devicePepper: ByteArray): ByteArray {
        val combinedSalt = ByteArray(salt.size + devicePepper.size)
        System.arraycopy(salt, 0, combinedSalt, 0, salt.size)
        System.arraycopy(devicePepper, 0, combinedSalt, salt.size, devicePepper.size)
        val spec = PBEKeySpec(pin, combinedSalt, ITERATIONS, KEY_BYTES * 8)
        return try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
            combinedSalt.fill(0)
        }
    }
}
