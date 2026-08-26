package com.vvf.smartmanager.core.security

import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File

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
        assertEquals(32, passphrase1.size) // 256 bits

        // Subsequent call must return identical decrypted passphrase
        val passphrase2 = cryptoSecurityManager.getOrCreateDatabasePassphrase()
        assertArrayEquals(passphrase1, passphrase2)
    }

    @Test
    fun testByteEncryptionDecryptionRoundtrip() {
        val originalText = "VVF Smart Manager Ultra Confidential Note #12345"
        val originalBytes = originalText.toByteArray(Charsets.UTF_8)

        val (encryptedBytes, iv) = cryptoSecurityManager.encryptBytes(originalBytes)
        assertNotNull(encryptedBytes)
        assertNotNull(iv)
        assertEquals(12, iv.size) // Standard 12-byte GCM IV

        val decryptedBytes = cryptoSecurityManager.decryptBytes(encryptedBytes, iv)
        val decryptedText = String(decryptedBytes, Charsets.UTF_8)

        assertEquals(originalText, decryptedText)
    }

    @Test
    fun testStreamingEncryptionDecryptionRoundtrip() {
        val payload = "Testing AES-256-GCM Streaming File Encryption for Secure Vault.\n".repeat(500)
        val originalBytes = payload.toByteArray(Charsets.UTF_8)

        val sourceIn = ByteArrayInputStream(originalBytes)
        val encryptedOut = ByteArrayOutputStream()

        val result = cryptoSecurityManager.encryptStream(sourceIn, encryptedOut)
        assertTrue(result.totalBytesEncrypted > 0)
        assertNotNull(result.ivBase64)

        val encryptedBytes = encryptedOut.toByteArray()
        assertTrue(encryptedBytes.size > originalBytes.size) // Contains 12-byte IV + GCM tag

        val encryptedIn = ByteArrayInputStream(encryptedBytes)
        val decryptedOut = ByteArrayOutputStream()

        val totalDecrypted = cryptoSecurityManager.decryptStream(encryptedIn, decryptedOut)
        assertEquals(originalBytes.size.toLong(), totalDecrypted)

        val decryptedText = String(decryptedOut.toByteArray(), Charsets.UTF_8)
        assertEquals(payload, decryptedText)
    }
}
