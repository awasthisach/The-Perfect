package com.vvf.smartmanager.core.security

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.After
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
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class CryptoSecurityManagerTest {
    private lateinit var context: Context
    private lateinit var crypto: CryptoSecurityManager

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        clearTestState()
        crypto = CryptoSecurityManager(context, "Robolectric")
    }

    @After
    fun tearDown() {
        clearTestState()
    }

    @Test
    fun databasePassphraseIsGeneratedAndPersistsAcrossInstances() {
        val first = crypto.getOrCreateDatabasePassphrase()
        assertEquals(32, first.size)

        val second = CryptoSecurityManager(context, "Robolectric")
            .getOrCreateDatabasePassphrase()
        assertArrayEquals(first, second)
    }

    @Test
    fun byteEncryptionDecryptsToOriginalPayload() {
        val original = "VVF Smart Manager confidential metadata".toByteArray(Charsets.UTF_8)
        val (encrypted, iv) = crypto.encryptBytes(original)

        assertEquals(12, iv.size)
        assertArrayEquals(original, crypto.decryptBytes(encrypted, iv))
    }

    @Test
    fun repeatedPayloadUsesUniqueIv() {
        val payload = "same secret payload".toByteArray(Charsets.UTF_8)
        val firstIv = crypto.encryptBytes(payload).second
        val secondIv = crypto.encryptBytes(payload).second

        assertFalse(firstIv.contentEquals(secondIv))
    }

    @Test
    fun tamperedCiphertextFailsAuthentication() {
        val (encrypted, iv) = crypto.encryptBytes("tamper detection".toByteArray())
        encrypted[encrypted.lastIndex] = (encrypted.last().toInt() xor 1).toByte()

        assertThrowsSecurityFailure { crypto.decryptBytes(encrypted, iv) }
    }

    @Test
    fun tamperedIvFailsAuthentication() {
        val (encrypted, iv) = crypto.encryptBytes("tamper detection".toByteArray())
        iv[0] = (iv[0].toInt() xor 1).toByte()

        assertThrowsSecurityFailure { crypto.decryptBytes(encrypted, iv) }
    }

    @Test
    fun streamingEncryptionDecryptsToOriginalPayload() {
        val original = "Streaming AES-GCM vault content.\n".repeat(500).toByteArray(Charsets.UTF_8)
        val encryptedOutput = ByteArrayOutputStream()
        val result = crypto.encryptStream(ByteArrayInputStream(original), encryptedOutput)

        assertEquals(original.size.toLong(), result.totalBytesEncrypted)
        assertNotNull(result.ivBase64)

        val decryptedOutput = ByteArrayOutputStream()
        val decryptedBytes = crypto.decryptStream(
            ByteArrayInputStream(encryptedOutput.toByteArray()),
            decryptedOutput
        )
        assertEquals(original.size.toLong(), decryptedBytes)
        assertArrayEquals(original, decryptedOutput.toByteArray())
    }

    @Test
    fun truncatedStreamingHeaderIsRejected() {
        assertThrowsSecurityFailure {
            crypto.decryptStream(ByteArrayInputStream(ByteArray(11)), ByteArrayOutputStream())
        }
    }

    @Test
    fun pinFormatIsStrictlyBounded() {
        assertTrue(crypto.isValidPinFormat("1234"))
        assertTrue(crypto.isValidPinFormat("123456"))
        assertFalse(crypto.isValidPinFormat("123"))
        assertFalse(crypto.isValidPinFormat("1234567"))
        assertFalse(crypto.isValidPinFormat("12a4"))
        assertFalse(crypto.isValidPinFormat(""))
    }

    @Test
    fun realAndDecoyPinsRemainDistinct() {
        assertTrue(crypto.setupVaultPin("2468"))
        assertTrue(crypto.setupDecoyPin("654321"))

        assertEquals(
            CryptoSecurityManager.VaultAuthResult.SUCCESS_REAL,
            crypto.verifyVaultPinWithResult("2468")
        )
        assertEquals(
            CryptoSecurityManager.VaultAuthResult.SUCCESS_DECOY,
            crypto.verifyVaultPinWithResult("654321")
        )
        assertTrue(crypto.verifyVaultPinWithResult("1357") is CryptoSecurityManager.VaultAuthResult.INVALID_PIN)
    }

    @Test
    fun wipeBufferOverwritesEveryByte() {
        val buffer = byteArrayOf(1, 2, 3)
        crypto.wipeBuffer(buffer)

        assertTrue(buffer.all { it == 0.toByte() })
    }

    private fun clearTestState() {
        CryptoSecurityManager.clearInMemoryKeysForTests()
        context.getSharedPreferences("vvf_secure_vault_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        listOf(
            "vvf_db_enc_passphrase_v2.bin",
            "vvf_db_enc_passphrase.bin",
            "vvf_db_passphrase_iv.bin",
            "vvf_db_enc_passphrase_v2.bin.tmp"
        ).forEach { File(context.filesDir, it).delete() }
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
