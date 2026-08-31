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
        cryptoSecurityManager = CryptoSecurityManager(
            ApplicationProvider.getApplicationContext(),
            "Robolectric"
        )
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
        val tampered = encrypted.clone()
        tampered[tampered.lastIndex] = (tampered.last().toInt() xor 0x01).toByte()

        var failed = false
        try {
            cryptoSecurityManager.decryptBytes(tampered, iv)
        } catch (_: Exception) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun testTamperedIvFailsAuthentication() {
        val payload = "tamper iv".toByteArray(Charsets.UTF_8)
        val (encrypted, iv) = cryptoSecurityManager.encryptBytes(payload)
        val tamperedIv = iv.clone()
        tamperedIv[0] = (tamperedIv[0].toInt() xor 0x01).toByte()

        var failed = false
        try {
            cryptoSecurityManager.decryptBytes(encrypted, tamperedIv)
        } catch (_: Exception) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun testStreamingEncryptionDecryptionRoundtrip() {
        val original = ByteArray(150_000) { (it % 251).toByte() }
        val encrypted = ByteArrayOutputStream()
        cryptoSecurityManager.encryptStream(ByteArrayInputStream(original), encrypted)

        val decrypted = ByteArrayOutputStream()
        cryptoSecurityManager.decryptStream(ByteArrayInputStream(encrypted.toByteArray()), decrypted)

        assertArrayEquals(original, decrypted.toByteArray())
    }

    @Test
    fun testTruncatedStreamingHeaderIsRejected() {
        var failed = false
        try {
            cryptoSecurityManager.decryptStream(ByteArrayInputStream(ByteArray(11)), ByteArrayOutputStream())
        } catch (_: IllegalArgumentException) {
            failed = true
        }
        assertTrue(failed)
    }

    @Test
    fun testPinFormatIsStrictlyBounded() {
        assertTrue(cryptoSecurityManager.isValidPinFormat("1234"))
        assertTrue(cryptoSecurityManager.isValidPinFormat("123456"))
        assertFalse(cryptoSecurityManager.isValidPinFormat("123"))
        assertFalse(cryptoSecurityManager.isValidPinFormat("1234567"))
        assertFalse(cryptoSecurityManager.isValidPinFormat("12a4"))
    }
}
