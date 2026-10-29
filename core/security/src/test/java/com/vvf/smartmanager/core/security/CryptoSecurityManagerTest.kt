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
    fun databasePassphraseMigrationCanBeRolledBack() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val original = cryptoSecurityManager.getOrCreateDatabasePassphrase()
        val snapshot = java.io.File(context.cacheDir, "db-key-state-snapshot").apply {
            deleteRecursively()
            mkdirs()
        }
        val replacement = ByteArray(32) { (it + 1).toByte() }
        try {
            cryptoSecurityManager.snapshotDatabasePassphraseState(snapshot)
            cryptoSecurityManager.replaceDatabasePassphraseForRestore(replacement)
            val imported = cryptoSecurityManager.getOrCreateDatabasePassphrase()
            assertArrayEquals(replacement, imported)
            imported.fill(0)

            cryptoSecurityManager.restoreDatabasePassphraseState(snapshot)
            val restored = cryptoSecurityManager.getOrCreateDatabasePassphrase()
            assertArrayEquals(original, restored)
            restored.fill(0)
        } finally {
            original.fill(0)
            replacement.fill(0)
            snapshot.deleteRecursively()
        }
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
    fun realPinMigratesLegacyVaultFilesAndLocksVersionedDataKey() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vaultDir = java.io.File(context.filesDir, "vvf_vault_encrypted")
        vaultDir.deleteRecursively()
        assertTrue(vaultDir.mkdirs())
        val legacyFile = java.io.File(vaultDir, "enc_legacy.vvf")
        val plaintext = "legacy vault payload that must survive migration".toByteArray()
        val (ciphertext, iv) = cryptoSecurityManager.encryptBytes(
            plaintext,
            alias = "vvf_vault_master_key_v1"
        )
        legacyFile.writeBytes(iv + ciphertext)
        val restored = java.io.File(context.cacheDir, "vault-migration-restored.bin")
        try {
            assertTrue(cryptoSecurityManager.setupVaultPin("482951"))
            assertTrue(legacyFile.readBytes().copyOfRange(0, 4).contentEquals(byteArrayOf(86, 86, 70, 50)))
            cryptoSecurityManager.decryptFile(legacyFile, restored)
            assertArrayEquals(plaintext, restored.readBytes())

            cryptoSecurityManager.lockVaultDataKey()
            assertThrowsSecurityFailure {
                cryptoSecurityManager.decryptFile(legacyFile, java.io.File(context.cacheDir, "locked-vault-output.bin"))
            }
        } finally {
            restored.delete()
            java.io.File(context.cacheDir, "locked-vault-output.bin").delete()
            vaultDir.deleteRecursively()
        }
    }

    @Test
    fun testStreamingEncryptionDecryptionRoundtrip() {
        assertTrue(cryptoSecurityManager.setupVaultPin("482951"))
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

    @Test
    fun lockoutStateIsAuthenticatedAndUsesMonotonicRuntimeTime() {
        assertTrue(cryptoSecurityManager.setupVaultPin("482951"))
        repeat(4) {
            assertTrue(
                cryptoSecurityManager.verifyVaultPinWithResult("000000") is
                    CryptoSecurityManager.VaultAuthResult.INVALID_PIN
            )
        }
        val fifth = cryptoSecurityManager.verifyVaultPinWithResult("000000")
        assertTrue(fifth is CryptoSecurityManager.VaultAuthResult.INVALID_PIN)
        assertTrue(cryptoSecurityManager.getRemainingLockoutSeconds() > 0)
        assertTrue(
            cryptoSecurityManager.verifyVaultPinWithResult("482951") is
                CryptoSecurityManager.VaultAuthResult.LOCKED_OUT
        )

        val prefs = ApplicationProvider.getApplicationContext<android.content.Context>()
            .getSharedPreferences("vvf_secure_vault_prefs", android.content.Context.MODE_PRIVATE)
        assertFalse(prefs.contains("vault_failed_attempts"))
        assertFalse(prefs.contains("vault_lockout_until_ms"))
        assertTrue(prefs.contains("vault_lockout_state_v2"))
    }

    @Test
    fun portableBackupEncryptionWorksWithoutDeviceKeystoreKey() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val source = java.io.File(context.cacheDir, "portable-backup-source.bin").apply {
            writeText("portable backup payload")
        }
        val encrypted = java.io.File(context.cacheDir, "portable-backup.vvfbak")
        val restored = java.io.File(context.cacheDir, "portable-backup-restored.bin")
        try {
            val portableCrypto = PortableBackupCrypto()
            portableCrypto.encryptFile(source, encrypted, "portable backup passphrase".toCharArray())
            portableCrypto.decryptFile(encrypted, restored, "portable backup passphrase".toCharArray())

            assertEquals(source.readText(), restored.readText())
        } finally {
            source.delete()
            encrypted.delete()
            restored.delete()
            CryptoSecurityManager.clearInMemoryKeysForTests()
        }
    }

    @Test
    fun customKeyStoreProviderMustNotEnableProductionInMemoryFallback() {
        assertFalse(
            CryptoSecurityManager.shouldAllowInMemoryFallback(
                keyStoreProvider = "InjectedProvider",
                isJvmUnitTestEnvironment = false
            )
        )
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
