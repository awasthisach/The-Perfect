package com.vvf.smartmanager.core.security

import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Portable, passphrase-protected backup envelope.
 *
 * File format: magic (VVFB), version, random salt, random GCM nonce, ciphertext + tag.
 * The complete header is authenticated as GCM AAD. Decryption writes only to a sibling
 * temporary file and publishes it after GCM authentication has succeeded.
 */
class PortableBackupCrypto(
    private val random: SecureRandom = SecureRandom()
) {
    companion object {
        private val MAGIC = byteArrayOf('V'.code.toByte(), 'V'.code.toByte(), 'F'.code.toByte(), 'B'.code.toByte())
        private const val VERSION: Byte = 1
        private const val SALT_BYTES = 16
        private const val NONCE_BYTES = 12
        private const val KEY_BITS = 256
        private const val TAG_BITS = 128
        private const val PBKDF2_ITERATIONS = 600_000
        private const val BUFFER_SIZE = 64 * 1024
        private const val HEADER_SIZE = 4 + 1 + SALT_BYTES + NONCE_BYTES
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
    }

    /**
     * Encrypts a file and consumes [passphrase], zeroing the supplied character array on every exit path.
     */
    @Throws(Exception::class)
    fun encryptFile(source: File, destination: File, passphrase: CharArray) {
        try {
            encryptFileInternal(source, destination, passphrase)
        } finally {
            passphrase.fill(0.toChar())
        }
    }

    private fun encryptFileInternal(source: File, destination: File, passphrase: CharArray) {
        require(passphrase.size >= 12) { "Backup passphrase must be at least 12 characters" }
        require(source.isFile) { "Backup source does not exist" }
        val parent = destination.absoluteFile.parentFile
            ?: throw IOException("Backup destination has no parent directory")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Unable to create backup destination directory")

        val salt = ByteArray(SALT_BYTES).also(random::nextBytes)
        val nonce = ByteArray(NONCE_BYTES).also(random::nextBytes)
        val header = MAGIC + byteArrayOf(VERSION) + salt + nonce
        val key = deriveKey(passphrase, salt)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
        cipher.updateAAD(header)

        val temp = createTempSibling(destination)
        try {
            FileInputStream(source).use { input ->
                FileOutputStream(temp).use { output ->
                    output.write(header)
                    val buffer = ByteArray(BUFFER_SIZE)
                    try {
                        while (true) {
                            val count = input.read(buffer)
                            if (count < 0) break
                            val encrypted = cipher.update(buffer, 0, count)
                            if (encrypted != null && encrypted.isNotEmpty()) output.write(encrypted)
                        }
                        val finalBytes = cipher.doFinal()
                        if (finalBytes.isNotEmpty()) output.write(finalBytes)
                        output.flush()
                        output.fd.sync()
                    } finally {
                        buffer.fill(0)
                    }
                }
            }
            publish(temp, destination)
        } catch (error: Throwable) {
            temp.delete()
            throw error
        } finally {
            salt.fill(0)
            nonce.fill(0)
            header.fill(0)
        }
    }

    /**
     * Decrypts a file and consumes [passphrase], zeroing the supplied character array on every exit path.
     */
    @Throws(Exception::class)
    fun decryptFile(source: File, destination: File, passphrase: CharArray) {
        try {
            decryptFileInternal(source, destination, passphrase)
        } finally {
            passphrase.fill(0.toChar())
        }
    }

    private fun decryptFileInternal(source: File, destination: File, passphrase: CharArray) {
        require(passphrase.isNotEmpty()) { "Backup passphrase must not be empty" }
        require(source.isFile) { "Backup source does not exist" }
        val parent = destination.absoluteFile.parentFile
            ?: throw IOException("Backup destination has no parent directory")
        if (!parent.exists() && !parent.mkdirs()) throw IOException("Unable to create restore destination directory")

        val temp = createTempSibling(destination)
        try {
            FileInputStream(source).use { input ->
                val header = ByteArray(HEADER_SIZE)
                readFully(input, header)
                if (!header.copyOfRange(0, MAGIC.size).contentEquals(MAGIC)) {
                    throw SecurityException("Unsupported backup format")
                }
                if (header[MAGIC.size] != VERSION) throw SecurityException("Unsupported backup version")
                val salt = header.copyOfRange(MAGIC.size + 1, MAGIC.size + 1 + SALT_BYTES)
                val nonce = header.copyOfRange(MAGIC.size + 1 + SALT_BYTES, HEADER_SIZE)
                try {
                    val key = deriveKey(passphrase, salt)
                    val cipher = Cipher.getInstance(TRANSFORMATION)
                    cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(TAG_BITS, nonce))
                    cipher.updateAAD(header)
                    FileOutputStream(temp).use { output ->
                        val buffer = ByteArray(BUFFER_SIZE)
                        try {
                            while (true) {
                                val count = input.read(buffer)
                                if (count < 0) break
                                val plaintext = cipher.update(buffer, 0, count)
                                if (plaintext != null && plaintext.isNotEmpty()) {
                                    try {
                                        output.write(plaintext)
                                    } finally {
                                        plaintext.fill(0)
                                    }
                                }
                            }
                            // GCM authentication is checked here; destination remains untouched until it succeeds.
                            val finalBytes = cipher.doFinal()
                            try {
                                if (finalBytes.isNotEmpty()) output.write(finalBytes)
                            } finally {
                                finalBytes.fill(0)
                            }
                            output.flush()
                            output.fd.sync()
                        } finally {
                            buffer.fill(0)
                        }
                    }
                } finally {
                    salt.fill(0)
                    nonce.fill(0)
                    header.fill(0)
                }
            }
            publish(temp, destination)
        } catch (error: Throwable) {
            try {
                securelyDeletePlaintextTemp(temp)
            } catch (cleanupError: Throwable) {
                cleanupError.addSuppressed(error)
                throw cleanupError
            }
            throw error
        }
    }

    /** Overwrite if possible, but always attempt unlinking failed plaintext output. */
    private fun securelyDeletePlaintextTemp(file: File) {
        if (!file.exists()) return
        var overwriteFailure: Throwable? = null
        try {
            if (file.isFile && file.length() > 0L) {
                val length = file.length()
                val zeros = ByteArray(BUFFER_SIZE)
                try {
                    FileOutputStream(file).use { output ->
                        var written = 0L
                        while (written < length) {
                            val count = minOf(zeros.size.toLong(), length - written).toInt()
                            output.write(zeros, 0, count)
                            written += count
                        }
                        output.flush()
                        output.fd.sync()
                    }
                } finally {
                    zeros.fill(0)
                }
            }
        } catch (cleanupError: Throwable) {
            overwriteFailure = cleanupError
        }

        val removed = !file.exists() || file.delete()
        if (overwriteFailure != null) {
            val failure = IOException(
                if (removed) {
                    "Unable to overwrite failed plaintext restore temporary file; deletion was attempted"
                } else {
                    "Unable to overwrite or remove failed plaintext restore temporary file"
                },
                overwriteFailure
            )
            throw failure
        }
        if (!removed) {
            throw IOException("Unable to remove failed plaintext restore temporary file")
        }
    }

    private fun deriveKey(passphrase: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(passphrase, salt, PBKDF2_ITERATIONS, KEY_BITS)
        return try {
            val raw = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
            try {
                SecretKeySpec(raw, "AES")
            } finally {
                raw.fill(0)
            }
        } finally {
            spec.clearPassword()
        }
    }

    private fun createTempSibling(destination: File): File {
        val parent = destination.absoluteFile.parentFile
            ?: throw IOException("Destination has no parent directory")
        return File.createTempFile(".${destination.name}.", ".tmp", parent)
    }

    private fun readFully(input: FileInputStream, bytes: ByteArray) {
        var offset = 0
        while (offset < bytes.size) {
            val count = input.read(bytes, offset, bytes.size - offset)
            if (count < 0) throw SecurityException("Truncated backup header")
            offset += count
        }
    }

    private fun publish(temp: File, destination: File) {
        if (!temp.renameTo(destination)) {
            throw IOException("Unable to atomically publish backup output")
        }
    }
}
