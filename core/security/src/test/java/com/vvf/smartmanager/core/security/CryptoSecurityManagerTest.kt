package com.vvf.smartmanager.core.security

import androidx.test.core.app.ApplicationProvider
import android.content.Context
import android.util.Base64
import android.content.ContextWrapper
import java.io.File
import java.util.UUID
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
    fun corruptedDatabaseKeySnapshotIsRejectedWithoutDestroyingLiveKey() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val original = cryptoSecurityManager.getOrCreateDatabasePassphrase()
        val snapshot = File(context.cacheDir, "corrupt-db-key-snapshot").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            cryptoSecurityManager.snapshotDatabasePassphraseState(snapshot)
            val snapshotKey = File(snapshot, "vvf_db_passphrase_v2.bin")
            val bytes = snapshotKey.readBytes()
            bytes[bytes.lastIndex] = (bytes.last().toInt() xor 0x01).toByte()
            snapshotKey.writeBytes(bytes)

            try {
                cryptoSecurityManager.restoreDatabasePassphraseState(snapshot)
                throw AssertionError("Corrupted rollback snapshot must be rejected")
            } catch (expected: Throwable) {
                assertTrue(
                    expected is SecurityException ||
                        expected is IllegalArgumentException ||
                        expected is javax.crypto.AEADBadTagException ||
                        expected.cause is javax.crypto.AEADBadTagException
                )
            }

            val stillLive = cryptoSecurityManager.getOrCreateDatabasePassphrase()
            assertArrayEquals(original, stillLive)
            stillLive.fill(0)
        } finally {
            original.fill(0)
            snapshot.deleteRecursively()
        }
    }

    @Test
    fun emptyDatabaseKeySnapshotIsRejectedWithoutDestroyingLiveKey() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val original = cryptoSecurityManager.getOrCreateDatabasePassphrase()
        val snapshot = File(context.cacheDir, "empty-db-key-snapshot").apply {
            deleteRecursively()
            mkdirs()
        }
        try {
            File(snapshot, "present-files.txt").writeText("")
            try {
                cryptoSecurityManager.restoreDatabasePassphraseState(snapshot)
                throw AssertionError("Empty rollback snapshot must be rejected")
            } catch (expected: IllegalArgumentException) {
                assertTrue(expected.message.orEmpty().contains("no database passphrase recovery material"))
            }

            val stillLive = cryptoSecurityManager.getOrCreateDatabasePassphrase()
            assertArrayEquals(original, stillLive)
            stillLive.fill(0)
        } finally {
            original.fill(0)
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
    fun corruptedRealPinMetadataDoesNotCountAsAnIncorrectPin() {
        val isolated = isolatedContext()
        val manager = CryptoSecurityManager(isolated, "Robolectric")
        assertTrue(manager.setupVaultPin("482951"))
        isolated.getSharedPreferences("vvf_secure_vault_prefs", Context.MODE_PRIVATE)
            .edit().putString("vault_pin_hash", "%%%").commit()

        val result = manager.verifyVaultPinWithResult("000000")

        assertEquals(CryptoSecurityManager.VaultAuthResult.CORRUPTED_STATE, result)
        assertEquals(0, manager.getFailedAttemptsCount())
    }

    @Test
    fun tamperedPinMetadataGcmTagIsCorruptedStateNotAnIncorrectPin() {
        val isolated = isolatedContext()
        val manager = CryptoSecurityManager(isolated, "Robolectric")
        assertTrue(manager.setupVaultPin("482951"))
        val prefs = isolated.getSharedPreferences("vvf_secure_vault_prefs", Context.MODE_PRIVATE)
        val encryptedHash = Base64.decode(prefs.getString("vault_pin_hash", null), Base64.NO_WRAP)
        encryptedHash[encryptedHash.lastIndex] = (encryptedHash.last().toInt() xor 0x01).toByte()
        prefs.edit().putString(
            "vault_pin_hash",
            Base64.encodeToString(encryptedHash, Base64.NO_WRAP)
        ).commit()

        val result = manager.verifyVaultPinWithResult("000000")

        assertEquals(CryptoSecurityManager.VaultAuthResult.CORRUPTED_STATE, result)
        assertEquals(0, manager.getFailedAttemptsCount())
    }

    @Test
    fun wrongSharedPreferenceTypeIsCorruptedStateNotAnIncorrectPin() {
        val isolated = isolatedContext()
        val manager = CryptoSecurityManager(isolated, "Robolectric")
        assertTrue(manager.setupVaultPin("482951"))
        isolated.getSharedPreferences("vvf_secure_vault_prefs", Context.MODE_PRIVATE)
            .edit().putInt("vault_pin_hash", 12345).commit()

        val result = manager.verifyVaultPinWithResult("000000")

        assertEquals(CryptoSecurityManager.VaultAuthResult.CORRUPTED_STATE, result)
        assertEquals(0, manager.getFailedAttemptsCount())
    }

    @Test
    fun incompleteDecoyPinMetadataBlocksUnlockWithoutIncreasingFailedAttempts() {
        val isolated = isolatedContext()
        val manager = CryptoSecurityManager(isolated, "Robolectric")
        assertTrue(manager.setupVaultPin("482951"))
        assertTrue(manager.setupDecoyPin("135790"))
        isolated.getSharedPreferences("vvf_secure_vault_prefs", Context.MODE_PRIVATE)
            .edit().remove("vault_decoy_pin_salt_iv").commit()

        val result = manager.verifyVaultPinWithResult("000000")

        assertEquals(CryptoSecurityManager.VaultAuthResult.CORRUPTED_STATE, result)
        assertEquals(0, manager.getFailedAttemptsCount())
    }

    @Test
    fun genuineIncorrectPinStillIncrementsFailedAttemptCount() {
        val isolated = isolatedContext()
        val manager = CryptoSecurityManager(isolated, "Robolectric")
        assertTrue(manager.setupVaultPin("482951"))

        val result = manager.verifyVaultPinWithResult("000000")

        assertTrue(result is CryptoSecurityManager.VaultAuthResult.INVALID_PIN)
        assertEquals(1, manager.getFailedAttemptsCount())
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

    @Test
    fun missingDatabaseKeyDoesNotRegenerateWhenEncryptedPassphraseExists() {
        val context = isolatedContext()
        try {
            val original = CryptoSecurityManager(context, "Robolectric")
            original.getOrCreateDatabasePassphrase().fill(0)
            CryptoSecurityManager.clearInMemoryKeysForTests()

            try {
                CryptoSecurityManager(context, "Robolectric")
                throw AssertionError("Missing database key must not be silently regenerated")
            } catch (expected: SecurityException) {
                assertTrue(expected.message.orEmpty().contains("Refusing to generate a replacement key"))
            }
        } finally {
            CryptoSecurityManager.clearInMemoryKeysForTests()
        }
    }

    @Test
    fun missingVaultKeyDoesNotRegenerateWhenEncryptedVaultFileExists() {
        val context = isolatedContext()
        try {
            val original = CryptoSecurityManager(context, "Robolectric")
            val (encrypted, _) = original.encryptBytes("vault-data".toByteArray())
            val vaultDirectory = File(context.filesDir, "vvf_vault_encrypted").apply { mkdirs() }
            File(vaultDirectory, "enc_fixture.vvf").writeBytes(encrypted)
            CryptoSecurityManager.clearInMemoryKeysForTests()

            try {
                CryptoSecurityManager(context, "Robolectric")
                throw AssertionError("Missing vault key must not be silently regenerated")
            } catch (expected: SecurityException) {
                assertTrue(expected.message.orEmpty().contains("Refusing to generate a replacement key"))
            }
        } finally {
            CryptoSecurityManager.clearInMemoryKeysForTests()
        }
    }

    @Test
    fun missingVaultMetadataKeyDoesNotRegenerateWhenPinMetadataExists() {
        val context = isolatedContext()
        try {
            val original = CryptoSecurityManager(context, "Robolectric")
            assertTrue(original.setupVaultPin("482951"))
            CryptoSecurityManager.clearInMemoryKeysForTests()

            try {
                CryptoSecurityManager(context, "Robolectric")
                throw AssertionError("Missing vault metadata key must not be silently regenerated")
            } catch (expected: SecurityException) {
                assertTrue(expected.message.orEmpty().contains("Refusing to generate a replacement key"))
            }
        } finally {
            CryptoSecurityManager.clearInMemoryKeysForTests()
        }
    }

    @Test
    fun missingDatabasePassphraseDoesNotCreateReplacementWhenDatabaseExists() {
        val context = isolatedContext()
        try {
            val manager = CryptoSecurityManager(context, "Robolectric")
            File(context.filesDir, "vvf-existing.db").writeText("encrypted-database-placeholder")

            try {
                manager.getOrCreateDatabasePassphrase()
                throw AssertionError("Must not create a new database passphrase over an existing database")
            } catch (expected: SecurityException) {
                assertTrue(expected.message.orEmpty().contains("recovery material is missing"))
            }
            assertFalse(File(context.filesDir, "vvf_db_passphrase_v2.bin").exists())
        } finally {
            CryptoSecurityManager.clearInMemoryKeysForTests()
        }
    }

    private fun isolatedContext(): Context {
        val base = ApplicationProvider.getApplicationContext<Context>()
        val root = File(base.cacheDir, "keystore-recovery-${UUID.randomUUID()}").apply { mkdirs() }
        return object : ContextWrapper(base) {
            override fun getFilesDir(): File = File(root, "files").apply { mkdirs() }
            override fun getSharedPreferences(name: String, mode: Int) =
                base.getSharedPreferences("$name-${root.name}", mode)
            override fun getDatabasePath(name: String): File =
                File(getFilesDir(), name)
            override fun databaseList(): Array<String> =
                getFilesDir().listFiles().orEmpty().filter { it.extension == "db" }.map { it.name }.toTypedArray()
        }
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
