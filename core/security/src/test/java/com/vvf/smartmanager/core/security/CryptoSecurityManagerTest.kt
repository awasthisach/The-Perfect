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
        CryptoSecurityManager.clearInMemoryKeysForTests()
        ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("vvf_secure_vault_prefs", android.content.Context.MODE_PRIVATE)
            .edit().clear().commit()
        cryptoSecurityManager = CryptoSecurityManager(
            ApplicationProvider.getApplicationContext(),
            "Robolectric"
        )
    }

    @Test
    fun completeLegacyAuthImportUsesOriginalIterationDefault() {
        val salt = ByteArray(16) { it.toByte() }
        val spec = javax.crypto.spec.PBEKeySpec("482951".toCharArray(), salt, 600_000, 256)
        val hash = javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded
        spec.clearPassword()
        val (encryptedHash, hashIv) = cryptoSecurityManager.encryptBytes(hash, "vvf_vault_meta_key_v1")
        val (encryptedSalt, saltIv) = cryptoSecurityManager.encryptBytes(salt, "vvf_vault_meta_key_v1")
        fun encode(bytes: ByteArray) = android.util.Base64.encodeToString(bytes, android.util.Base64.NO_WRAP)
        assertTrue(cryptoSecurityManager.importVaultAuthMetadata(mapOf(
            "vault_pin_hash" to encode(encryptedHash), "vault_pin_hash_iv" to encode(hashIv),
            "vault_pin_salt" to encode(encryptedSalt), "vault_pin_salt_iv" to encode(saltIv))))
        assertTrue(cryptoSecurityManager.verifyVaultPin("482951"))
        assertFalse(cryptoSecurityManager.verifyVaultPin("751923"))
        hash.fill(0); salt.fill(0)
    }

    @Test
    fun partialAuthImportCannotMutateExistingPinAndRollbackReplacesExactState() {
        assertTrue(cryptoSecurityManager.setupVaultPin("482951"))
        val before = cryptoSecurityManager.exportVaultAuthMetadata()
        assertFalse(cryptoSecurityManager.importVaultAuthMetadata(mapOf("vault_pin_hash" to "partial")))
        assertEquals(before, cryptoSecurityManager.exportVaultAuthMetadata())
        assertTrue(cryptoSecurityManager.setupVaultPin("751923"))
        assertTrue(cryptoSecurityManager.restoreVaultAuthMetadata(before))
        assertTrue(cryptoSecurityManager.verifyVaultPin("482951"))
        assertFalse(cryptoSecurityManager.verifyVaultPin("751923"))
    }

    @Test
    fun vaultPinUsesDerivedHashAndRejectsWrongPin() {
        assertTrue(cryptoSecurityManager.setupVaultPin("482951"))
        assertTrue(cryptoSecurityManager.verifyVaultPin("482951"))
        assertFalse(cryptoSecurityManager.verifyVaultPin("482952"))
    }

    @Test
    fun lockoutStateIsAuthenticatedAndUsesMonotonicRuntimeTime() {
        assertTrue(cryptoSecurityManager.setupVaultPin("482951"))
        repeat(4) { assertTrue(cryptoSecurityManager.verifyVaultPinWithResult("000000") is CryptoSecurityManager.VaultAuthResult.INVALID_PIN) }
        val fifth = cryptoSecurityManager.verifyVaultPinWithResult("000000")
        assertTrue(fifth is CryptoSecurityManager.VaultAuthResult.INVALID_PIN)
        assertTrue(cryptoSecurityManager.getRemainingLockoutSeconds() > 0)
        assertTrue(cryptoSecurityManager.verifyVaultPinWithResult("482951") is CryptoSecurityManager.VaultAuthResult.LOCKED_OUT)

        val prefs = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("vvf_secure_vault_prefs", android.content.Context.MODE_PRIVATE)
        assertFalse(prefs.contains("vault_failed_attempts"))
        assertFalse(prefs.contains("vault_lockout_until_ms"))
        assertTrue(prefs.contains("vault_lockout_state_v2"))
    }

    @Test
    fun testDatabasePassphraseGenerationAndPersistence() {
        val passphrase1 = cryptoSecurityManager.getOrCreateDatabasePassphrase()
        assertNotNull(passphrase1)
        assertEquals(32, passphrase1.size)

        val passphrase2 = cryptoSecurityManager.getOrCreateDatabasePassphrase()
        assertArrayEquals(passphrase1, passphrase2)
        assertTrue(passphrase1.any { it != 0.toByte() })
        passphrase1.fill(0)
        val reopened = cryptoSecurityManager.getOrCreateDatabasePassphrase()
        assertArrayEquals(passphrase2, reopened)
        passphrase2.fill(0)
        reopened.fill(0)
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
