package com.vvf.smartmanager.core.security

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CryptoSecurityManagerTest {

    private lateinit var cryptoSecurityManager: CryptoSecurityManager

    @Before
    fun setup() {
        cryptoSecurityManager = CryptoSecurityManager(ApplicationProvider.getApplicationContext())
    }

    @Test
    fun testDatabasePassphraseGenerationAndPersistence() {
        val passphrase1 = cryptoSecurityManager.getOrCreateDatabasePassphrase()
        assertNotNull(passphrase1)
        assertEquals(32, passphrase1.size)

        val passphrase2 = cryptoSecurityManager.getOrCreateDatabasePassphrase()
        assertArrayEquals(passphrase1, passphrase2)
    }

    @Test
    fun testByteEncryptionDecryptionRoundtrip() {
        val originalBytes = "VVF Smart Manager Ultra Confidential Note #12345".toByteArray(Charsets.UTF_8)

        val (encryptedBytes, iv) = cryptoSecurityManager.encryptBytes(originalBytes)
        assertNotNull(encryptedBytes)
        assertEquals(12, iv.size)

        assertArrayEquals(originalBytes, cryptoSecurityManager.decryptBytes(encryptedBytes, iv))
    }

    @Test
    fun testEncryptionUsesUniqueIvForRepeatedPayloads() {
        val payload = "same secret payload".toByteArray(Charsets.UTF_8)
        val (_, iv1) = cryptoSecurityManager.encryptBytes(payload)
        val (_, iv2) = cryptoSecurityManager.encryptBytes(payload)

        assertFalse(iv1.contentEquals(iv2))
    }

    @Test
    fun testTamperedCiphertextFailsAuthentication() {
        val payload = "tamper detection".toByteArray(Charsets.UTF_8)
        val (encrypted, iv) = cryptoSecurityManager.encryptBytes(payload)
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 0x01).toByte()

        assertThrowsSecurityFailure {
            cryptoSecurityManager.decryptBytes(encrypted, iv)
        }
    }

    @Test
    fun testTamperedIvFailsAuthentication() {
        val payload = "tamper detection".toByteArray(Charsets.UTF_8)
        val (encrypted, iv) = cryptoSecurityManager.encryptBytes(payload)
        iv[0] = (iv[0].toInt() xor 0x01).toByte()

        assertThrowsSecurityFailure {
            cryptoSecurityManager.decryptBytes(encrypted, iv)
        }
    }

    @Test
    fun testStreamingEncryptionDecryptionRoundtrip() {
        val payload = "Testing AES-256-GCM Streaming File Encryption for Secure Vault.\n".repeat(500)
        val originalBytes = payload.toByteArray(Charsets.UTF_8)

        val encryptedOut = ByteArrayOutputStream()
        val result = cryptoSecurityManager.encryptStream(
            ByteArrayInputStream(originalBytes),
            encryptedOut
        )
        assertTrue(result.totalBytesEncrypted == originalBytes.size.toLong())
        assertNotNull(result.ivBase64)

        val encryptedBytes = encryptedOut.toByteArray()
        assertTrue(encryptedBytes.size > originalBytes.size)

        val decryptedOut = ByteArrayOutputStream()
        val totalDecrypted = cryptoSecurityManager.decryptStream(
            ByteArrayInputStream(encryptedBytes),
            decryptedOut
        )
        assertEquals(originalBytes.size.toLong(), totalDecrypted)
        assertArrayEquals(originalBytes, decryptedOut.toByteArray())
    }

    @Test
    fun testTruncatedStreamingHeaderIsRejected() {
        assertThrowsSecurityFailure {
            cryptoSecurityManager.decryptStream(
                ByteArrayInputStream(ByteArray(11)),
                ByteArrayOutputStream()
            )
        }
    }

    @Test
    fun testPinFormatIsStrictlyBounded() {
        assertTrue(cryptoSecurityManager.isValidPinFormat("1234"))
        assertTrue(cryptoSecurityManager.isValidPinFormat("123456"))
        assertFalse(cryptoSecurityManager.isValidPinFormat("123"))
        assertFalse(cryptoSecurityManager.isValidPinFormat("1234567"))
        assertFalse(cryptoSecurityManager.isValidPinFormat("12a4"))
        assertFalse(cryptoSecurityManager.isValidPinFormat(""))
    }

    private fun assertThrowsSecurityFailure(block: () -> Unit) {
        try {
            block()
            throw AssertionError("Expected cryptographic authentication/format failure")
        } catch (expected: Throwable) {
            assertTrue(
                "Unexpected exception type: ${expected::class.java.name}",
                expected is SecurityException ||
                    expected is IllegalArgumentException ||
                    expected is javax.crypto.AEADBadTagException ||
                    expected.cause is javax.crypto.AEADBadTagException
            )
        }
    }
}
