package com.vvf.smartmanager.core.security

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File
import java.nio.charset.StandardCharsets

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class PortableBackupCryptoTest {
    private val crypto = PortableBackupCrypto()

    @Test
    fun roundTripUsesPortablePassphraseEnvelope() {
        withTempDirectory { dir ->
            val source = File(dir, "source.bin").apply {
                writeBytes(ByteArray(150_000) { (it % 251).toByte() })
            }
            val encrypted = File(dir, "backup.vvfbak")
            val restored = File(dir, "restored.bin")
            crypto.encryptFile(source, encrypted, "long backup passphrase 2026".toCharArray())
            crypto.decryptFile(encrypted, restored, "long backup passphrase 2026".toCharArray())

            assertArrayEquals(source.readBytes(), restored.readBytes())
            assertTrue(encrypted.readBytes().copyOfRange(0, 4).contentEquals("VVFB".toByteArray(StandardCharsets.US_ASCII)))
        }
    }

    @Test
    fun encryptAndDecryptConsumePassphraseBuffers() {
        withTempDirectory { dir ->
            val source = File(dir, "source.bin").apply { writeText("private payload") }
            val encrypted = File(dir, "backup.vvfbak")
            val restored = File(dir, "restored.bin")
            val encryptionPassphrase = "long encryption passphrase".toCharArray()
            crypto.encryptFile(source, encrypted, encryptionPassphrase)
            assertTrue(encryptionPassphrase.all { it == 0.toChar() })

            val decryptionPassphrase = "long encryption passphrase".toCharArray()
            crypto.decryptFile(encrypted, restored, decryptionPassphrase)
            assertTrue(decryptionPassphrase.all { it == 0.toChar() })
            assertArrayEquals(source.readBytes(), restored.readBytes())
        }
    }

    @Test
    fun wrongPassphraseFailsWithoutPublishingPlaintext() {
        assertDecryptFailsWithoutOutput { dir, encrypted, destination ->
            crypto.encryptFile(encrypted.first, encrypted.second, "correct passphrase".toCharArray())
            crypto.decryptFile(encrypted.second, destination, "incorrect passphrase".toCharArray())
        }
    }

    @Test
    fun tamperedHeaderFailsWithoutPublishingPlaintext() {
        assertDecryptFailsWithoutOutput { dir, encrypted, destination ->
            crypto.encryptFile(encrypted.first, encrypted.second, "header passphrase".toCharArray())
            val bytes = encrypted.second.readBytes()
            bytes[5] = (bytes[5].toInt() xor 0x40).toByte()
            encrypted.second.writeBytes(bytes)
            crypto.decryptFile(encrypted.second, destination, "header passphrase".toCharArray())
        }
    }

    @Test
    fun tamperedCiphertextFailsWithoutPublishingPlaintext() {
        assertDecryptFailsWithoutOutput { _, encrypted, destination ->
            crypto.encryptFile(encrypted.first, encrypted.second, "cipher passphrase".toCharArray())
            val bytes = encrypted.second.readBytes()
            bytes[bytes.lastIndex - 1] = (bytes[bytes.lastIndex - 1].toInt() xor 0x01).toByte()
            encrypted.second.writeBytes(bytes)
            crypto.decryptFile(encrypted.second, destination, "cipher passphrase".toCharArray())
        }
    }

    @Test
    fun truncatedHeaderFailsWithoutPublishingPlaintext() {
        withTempDirectory { dir ->
            val encrypted = File(dir, "broken.vvfbak").apply { writeBytes(byteArrayOf(0x56, 0x56)) }
            val destination = File(dir, "restored.bin")
            val result = runCatching {
                crypto.decryptFile(encrypted, destination, "passphrase".toCharArray())
            }
            assertTrue(result.isFailure)
            assertFalse(destination.exists())
            assertNoTempOutput(dir, destination)
        }
    }

    @Test
    fun truncatedCiphertextFailsWithoutPublishingPlaintext() {
        assertDecryptFailsWithoutOutput { _, encrypted, destination ->
            crypto.encryptFile(encrypted.first, encrypted.second, "truncate passphrase".toCharArray())
            val bytes = encrypted.second.readBytes()
            encrypted.second.writeBytes(bytes.copyOf(bytes.size - 5))
            crypto.decryptFile(encrypted.second, destination, "truncate passphrase".toCharArray())
        }
    }

    private fun assertDecryptFailsWithoutOutput(
        operation: (File, Pair<File, File>, File) -> Unit
    ) {
        withTempDirectory { dir ->
            val source = File(dir, "source.bin").apply { writeText("private payload") }
            val encrypted = File(dir, "backup.vvfbak")
            val destination = File(dir, "restored.bin")
            val result = runCatching { operation(dir, source to encrypted, destination) }
            assertTrue("Expected decryption to fail", result.isFailure)
            assertFalse("Plaintext destination must not be published on failure", destination.exists())
            assertNoTempOutput(dir, destination)
        }
    }

    private fun assertNoTempOutput(dir: File, destination: File) {
        assertTrue(
            "Temporary plaintext output must be removed after failure",
            dir.listFiles().orEmpty().none { it.name.startsWith(".${destination.name}.") && it.name.endsWith(".tmp") }
        )
    }

    private fun withTempDirectory(block: (File) -> Unit) {
        val dir = File(System.getProperty("java.io.tmpdir"), "portable-backup-test-${System.nanoTime()}")
        check(dir.mkdirs())
        try {
            block(dir)
        } finally {
            dir.deleteRecursively()
        }
    }
}
