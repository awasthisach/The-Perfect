package com.vvf.smartmanager.core.security

import android.content.Context
import android.os.SystemClock
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.KeyStore
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Security
import javax.crypto.Cipher
import javax.crypto.CipherInputStream
import javax.crypto.CipherOutputStream
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CryptoSecurityManager(
    private val context: Context,
    private val keyStoreProvider: String = ANDROID_KEYSTORE
) {

    @Inject
    constructor(context: Context) : this(context, ANDROID_KEYSTORE)

    companion object {
        const val ANDROID_KEYSTORE = "AndroidKeyStore"
        private const val DB_KEY_ALIAS = "vvf_db_passphrase_key_v1"
        private const val VAULT_KEY_ALIAS = "vvf_vault_master_key_v1"
        private const val VAULT_META_KEY_ALIAS = "vvf_vault_meta_key_v1"
        private const val AES_GCM_TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_TAG_LENGTH_BITS = 128
        private const val GCM_IV_LENGTH_BYTES = 12
        private const val DB_KEY_LENGTH_BYTES = 32
        private const val STREAM_BUFFER_SIZE = 64 * 1024
        private const val ENCRYPTED_DB_PASSPHRASE_FILE = "vvf_db_enc_passphrase.bin"
        private const val DB_PASSPHRASE_IV_FILE = "vvf_db_passphrase_iv.bin"
        private const val DB_PASSPHRASE_V2_FILE = "vvf_db_passphrase_v2.bin"
        private const val DB_PASSPHRASE_TEMP_SUFFIX = ".tmp"
        private const val DB_PASSPHRASE_FORMAT_VERSION: Byte = 2
        private const val PBKDF2_ITERATIONS = 600_000
        private const val LOCKOUT_STATE_KEY = "vault_lockout_state_v2"
        private const val LOCKOUT_STATE_IV_KEY = "vault_lockout_state_v2_iv"
        private val memoryKeyMap = mutableMapOf<String, SecretKey>()

        @JvmStatic
        fun clearInMemoryKeysForTests() {
            synchronized(memoryKeyMap) { memoryKeyMap.clear() }
        }

        internal fun shouldAllowInMemoryFallback(
            keyStoreProvider: String,
            isJvmUnitTestEnvironment: Boolean
        ): Boolean = keyStoreProvider.isNotBlank() && isJvmUnitTestEnvironment

        fun isJvmUnitTestEnvironment(context: Context): Boolean {
            val loaderName = context.classLoader?.javaClass?.name.orEmpty()
            if (loaderName.contains("robolectric", ignoreCase = true)) return true
            return try {
                Class.forName("org.robolectric.RuntimeEnvironment")
                true
            } catch (_: ClassNotFoundException) {
                false
            }
        }
    }

    private val allowInMemoryFallback: Boolean =
        shouldAllowInMemoryFallback(keyStoreProvider, isJvmUnitTestEnvironment(context))

    private val isAndroidKeyStoreAvailable: Boolean = try {
        Security.getProvider(ANDROID_KEYSTORE) != null
    } catch (_: Exception) {
        false
    }

    private val keyStore: KeyStore? = if (isAndroidKeyStoreAvailable && keyStoreProvider == ANDROID_KEYSTORE) {
        try {
            KeyStore.getInstance(ANDROID_KEYSTORE).apply { load(null) }
        } catch (_: Exception) {
            null
        }
    } else null

    init {
        ensureMasterKeyExists(DB_KEY_ALIAS)
        ensureMasterKeyExists(VAULT_KEY_ALIAS)
        ensureMasterKeyExists(VAULT_META_KEY_ALIAS)
    }

    private fun ensureMasterKeyExists(alias: String) {
        val ks = keyStore
        if (ks != null) {
            if (!ks.containsAlias(alias)) {
                val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE)
                val builder = KeyGenParameterSpec.Builder(
                    alias,
                    KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
                )
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .setRandomizedEncryptionRequired(true)
                keyGenerator.init(builder.build())
                keyGenerator.generateKey()
            }
        } else {
            if (!allowInMemoryFallback) {
                throw IllegalStateException("AndroidKeyStore is unavailable in a production environment; refusing to use in-memory keys")
            }
            val memoryKeyId = "$keyStoreProvider:$alias"
            if (!memoryKeyMap.containsKey(memoryKeyId)) {
                val rawKey = ByteArray(32)
                SecureRandom().nextBytes(rawKey)
                memoryKeyMap[memoryKeyId] = SecretKeySpec(rawKey, "AES")
                rawKey.fill(0)
            }
        }
    }

    private fun getSecretKey(alias: String): SecretKey {
        val ks = keyStore
        return if (ks != null && ks.containsAlias(alias)) {
            (ks.getEntry(alias, null) as KeyStore.SecretKeyEntry).secretKey
        } else {
            if (!allowInMemoryFallback) throw IllegalStateException("KeyStore unavailable for alias: $alias")
            memoryKeyMap.getOrPut("$keyStoreProvider:$alias") {
                val rawKey = ByteArray(32)
                SecureRandom().nextBytes(rawKey)
                try { SecretKeySpec(rawKey, "AES") } finally { rawKey.fill(0) }
            }
        }
    }

    @Synchronized
    fun getOrCreateDatabasePassphrase(): ByteArray {
        val v2File = File(context.filesDir, DB_PASSPHRASE_V2_FILE)
        val legacyEncFile = File(context.filesDir, ENCRYPTED_DB_PASSPHRASE_FILE)
        val legacyIvFile = File(context.filesDir, DB_PASSPHRASE_IV_FILE)
        if (v2File.exists()) {
            return try { readV2DatabasePassphrase(v2File) } catch (e: Throwable) {
                throw SecurityException("Secure database initialization failed: invalid v2 database passphrase file", e)
            }
        }
        if (legacyEncFile.exists() || legacyIvFile.exists()) {
            if (!legacyEncFile.exists() || !legacyIvFile.exists()) {
                throw SecurityException("Secure database initialization failed: incomplete legacy passphrase files")
            }
            val migratedPassphrase = try {
                decryptWithKeystore(legacyEncFile.readBytes(), legacyIvFile.readBytes(), DB_KEY_ALIAS)
            } catch (e: Throwable) {
                throw SecurityException("Secure database initialization failed: unable to decrypt legacy database passphrase", e)
            }
            writeV2DatabasePassphraseAtomically(v2File, migratedPassphrase)
            val verified = readV2DatabasePassphrase(v2File)
            if (!MessageDigest.isEqual(migratedPassphrase, verified)) {
                throw SecurityException("Secure database passphrase migration verification failed")
            }
            check(legacyEncFile.delete()) { "Failed to remove legacy encrypted database passphrase" }
            check(legacyIvFile.delete()) { "Failed to remove legacy database passphrase IV" }
            return migratedPassphrase
        }
        val freshPassphrase = ByteArray(DB_KEY_LENGTH_BYTES)
        SecureRandom().nextBytes(freshPassphrase)
        writeV2DatabasePassphraseAtomically(v2File, freshPassphrase)
        val verified = readV2DatabasePassphrase(v2File)
        if (!MessageDigest.isEqual(freshPassphrase, verified)) {
            throw SecurityException("Secure database passphrase write verification failed")
        }
        return freshPassphrase
    }

    @Synchronized
    fun snapshotDatabasePassphraseState(destination: File) {
        require(destination.exists() || destination.mkdirs()) { "Unable to create key-state snapshot directory" }
        val names = listOf(DB_PASSPHRASE_V2_FILE, ENCRYPTED_DB_PASSPHRASE_FILE, DB_PASSPHRASE_IV_FILE)
        val present = names.filter { name ->
            val source = File(context.filesDir, name)
            if (source.isFile) { source.copyTo(File(destination, name), overwrite = true); true } else false
        }
        File(destination, "present-files.txt").writeText(present.joinToString("\n"))
    }

    @Synchronized
    fun validateDatabasePassphraseStateSnapshot(snapshot: File) {
        val marker = File(snapshot, "present-files.txt")
        require(snapshot.isDirectory && marker.isFile) { "Database key-state rollback snapshot is missing" }
        val allowed = setOf(DB_PASSPHRASE_V2_FILE, ENCRYPTED_DB_PASSPHRASE_FILE, DB_PASSPHRASE_IV_FILE)
        val present = marker.readLines().filter { it.isNotBlank() }
        require(present.size == present.toSet().size) { "Database key-state snapshot contains duplicate entries" }
        require(present.all { it in allowed }) { "Database key-state snapshot contains unexpected entries" }
        require(present.all { File(snapshot, it).isFile }) { "Database key-state snapshot is incomplete" }
    }

    @Synchronized
    fun restoreDatabasePassphraseState(snapshot: File) {
        validateDatabasePassphraseStateSnapshot(snapshot)
        val allowed = setOf(DB_PASSPHRASE_V2_FILE, ENCRYPTED_DB_PASSPHRASE_FILE, DB_PASSPHRASE_IV_FILE)
        val present = File(snapshot, "present-files.txt").readLines().filter { it.isNotBlank() }
        allowed.forEach { name ->
            val live = File(context.filesDir, name)
            if (live.exists() && !live.delete()) throw SecurityException("Unable to clear current database key state")
        }
        present.forEach { name -> File(snapshot, name).copyTo(File(context.filesDir, name), overwrite = true) }
        listOf(
            File(context.filesDir, DB_PASSPHRASE_V2_FILE + ".restore-stage"),
            File(context.filesDir, DB_PASSPHRASE_V2_FILE + ".restore-previous")
        ).forEach { stale ->
            if (stale.exists() && !stale.delete()) throw SecurityException("Unable to remove interrupted database key migration artifact")
        }
    }

    @Synchronized
    fun replaceDatabasePassphraseForRestore(passphrase: ByteArray) {
        require(passphrase.size == DB_KEY_LENGTH_BYTES) { "Invalid database passphrase length" }
        val target = File(context.filesDir, DB_PASSPHRASE_V2_FILE)
        val staged = File(context.filesDir, DB_PASSPHRASE_V2_FILE + ".restore-stage")
        val previous = File(context.filesDir, DB_PASSPHRASE_V2_FILE + ".restore-previous")
        staged.delete(); previous.delete()
        writeV2DatabasePassphraseAtomically(staged, passphrase)
        if (target.exists() && !target.renameTo(previous)) {
            staged.delete()
            throw SecurityException("Unable to preserve previous database key state")
        }
        try {
            if (!staged.renameTo(target)) throw SecurityException("Unable to publish restored database key state")
            val verified = readV2DatabasePassphrase(target)
            try {
                if (!MessageDigest.isEqual(passphrase, verified)) throw SecurityException("Restored database passphrase verification failed")
            } finally { verified.fill(0) }
            File(context.filesDir, ENCRYPTED_DB_PASSPHRASE_FILE).delete()
            File(context.filesDir, DB_PASSPHRASE_IV_FILE).delete()
            previous.delete()
        } catch (error: Throwable) {
            target.delete()
            if (previous.exists() && !previous.renameTo(target)) {
                throw SecurityException("Database key import failed and previous key state could not be restored", error)
            }
            staged.delete()
            throw error
        }
    }

    private fun readV2DatabasePassphrase(file: File): ByteArray {
        val bytes = file.readBytes()
        val minimumSize = 1 + GCM_IV_LENGTH_BYTES + 16
        require(bytes.size >= minimumSize) { "Invalid v2 database passphrase file" }
        require(bytes[0] == DB_PASSPHRASE_FORMAT_VERSION) { "Unsupported database passphrase version" }
        val iv = bytes.copyOfRange(1, 1 + GCM_IV_LENGTH_BYTES)
        val encrypted = bytes.copyOfRange(1 + GCM_IV_LENGTH_BYTES, bytes.size)
        return decryptWithKeystore(encrypted, iv, DB_KEY_ALIAS)
    }

    private fun writeV2DatabasePassphraseAtomically(file: File, passphrase: ByteArray) {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(DB_KEY_ALIAS))
        val iv = cipher.iv
        val encrypted = cipher.doFinal(passphrase)
        val payload = ByteArray(1 + iv.size + encrypted.size)
        payload[0] = DB_PASSPHRASE_FORMAT_VERSION
        System.arraycopy(iv, 0, payload, 1, iv.size)
        System.arraycopy(encrypted, 0, payload, 1 + iv.size, encrypted.size)
        val tempFile = File(file.parentFile, file.name + DB_PASSPHRASE_TEMP_SUFFIX)
        try {
            FileOutputStream(tempFile).use { output ->
                output.write(payload); output.flush(); output.fd.sync()
            }
            check(tempFile.renameTo(file)) { "Failed to atomically replace database passphrase file" }
        } catch (e: Throwable) {
            tempFile.delete(); throw e
        }
    }

    @Synchronized
    private fun rewrapOrCreateVaultDataKey(pin: String) {
        val envelopeFile = File(context.filesDir, VAULT_DATA_KEY_FILE)
        val current = activeVaultDataKey?.copyOf() ?: if (envelopeFile.isFile) {
            pinWrappedVaultKey.unwrap(envelopeFile.readBytes(), pin.toCharArray())
        } else {
            ByteArray(DB_KEY_LENGTH_BYTES).also(SecureRandom()::nextBytes)
        }
        try {
            val keyToWrap = current.copyOf()
            val envelope = try {
                pinWrappedVaultKey.wrap(keyToWrap, pin.toCharArray())
            } finally {
                keyToWrap.fill(0)
            }
            val temporary = File(context.filesDir, "$VAULT_DATA_KEY_FILE.tmp")
            try {
                FileOutputStream(temporary).use { output ->
                    output.write(envelope)
                    output.flush()
                    output.fd.sync()
                }
                check(temporary.renameTo(envelopeFile) || run {
                    if (envelopeFile.exists() && !envelopeFile.delete()) false
                    else temporary.renameTo(envelopeFile)
                }) { "Unable to publish PIN-wrapped vault data key" }
            } finally {
                temporary.delete()
                envelope.fill(0)
            }
            activeVaultDataKey?.fill(0)
            activeVaultDataKey = current.copyOf()
        } finally {
            current.fill(0)
        }
    }

    @Synchronized
    private fun activateVaultDataKey(pin: String) {
        val envelopeFile = File(context.filesDir, VAULT_DATA_KEY_FILE)
        val key = if (envelopeFile.isFile) {
            pinWrappedVaultKey.unwrap(envelopeFile.readBytes(), pin.toCharArray())
        } else {
            ByteArray(DB_KEY_LENGTH_BYTES).also(SecureRandom()::nextBytes).also { fresh ->
                val keyToWrap = fresh.copyOf()
                val envelope = try {
                    pinWrappedVaultKey.wrap(keyToWrap, pin.toCharArray())
                } finally {
                    keyToWrap.fill(0)
                }
                val temporary = File(context.filesDir, "$VAULT_DATA_KEY_FILE.tmp")
                try {
                    FileOutputStream(temporary).use { output ->
                        output.write(envelope)
                        output.flush()
                        output.fd.sync()
                    }
                    check(temporary.renameTo(envelopeFile) || run {
                        if (envelopeFile.exists() && !envelopeFile.delete()) false
                        else temporary.renameTo(envelopeFile)
                    }) { "Unable to publish PIN-wrapped vault data key" }
                } finally {
                    temporary.delete()
                    envelope.fill(0)
                }
            }
        }
        activeVaultDataKey?.fill(0)
        activeVaultDataKey = key
    }

    /**
     * Re-encrypts one legacy device-key vault item under the active PIN-wrapped data key.
     * A same-directory backup allows recovery if the process dies between the two renames.
     */
    @Synchronized
    fun migrateLegacyVaultFile(file: File, vaultDirectory: File): Boolean {
        val canonicalVault = vaultDirectory.canonicalFile
        val canonicalFile = file.canonicalFile
        require(canonicalFile.path.startsWith(canonicalVault.path + File.separator)) {
            "Vault migration target escaped the vault directory"
        }
        val backup = File(file.parentFile, file.name + ".pin-migration-legacy")
        if (!file.exists() && backup.isFile) {
            check(backup.renameTo(file)) { "Unable to recover interrupted vault key migration" }
        }
        if (!file.isFile) throw SecurityException("Vault item is missing during key migration")
        if (isPinWrappedVaultFile(file)) {
            if (backup.exists()) check(backup.delete()) { "Unable to remove completed migration backup" }
            return false
        }
        if (backup.exists()) check(backup.delete()) { "Unable to remove stale legacy migration backup" }
        activeVaultDataKey ?: throw SecurityException("Vault is locked during key migration")
        val plain = File(file.parentFile, ".${file.name}.migration-plain")
        val encrypted = File(file.parentFile, ".${file.name}.migration-new")
        val verify = File(file.parentFile, ".${file.name}.migration-verify")
        try {
            plain.delete()
            encrypted.delete()
            if (verify.exists()) secureShredFile(verify)
            decryptFile(file, plain)
            encryptFile(plain, encrypted)
            decryptFile(encrypted, verify)
            check(
                plain.length() == verify.length() &&
                    MessageDigest.isEqual(sha256File(plain), sha256File(verify))
            ) { "Vault key migration verification mismatch" }
            check(secureShredFile(plain)) { "Unable to remove plaintext during vault key migration" }
            check(file.renameTo(backup)) { "Unable to preserve legacy vault ciphertext" }
            try {
                check(encrypted.renameTo(file)) { "Unable to publish migrated vault ciphertext" }
                check(backup.delete()) { "Unable to remove legacy vault ciphertext after migration" }
            } catch (error: Throwable) {
                if (!file.exists() && backup.exists()) backup.renameTo(file)
                throw error
            }
            return true
        } finally {
            if (plain.exists()) secureShredFile(plain)
            if (verify.exists()) secureShredFile(verify)
            encrypted.delete()
            if (!file.exists() && backup.exists()) backup.renameTo(file)
        }
    }

    /** Recovers or securely removes interrupted per-file migration artifacts before vault use. */
    fun recoverVaultMigrationArtifacts(vaultDirectory: File) {
        if (!vaultDirectory.exists()) return
        val canonicalRoot = vaultDirectory.canonicalFile
        canonicalRoot.walkTopDown().filter { it.isFile }.toList().forEach { artifact ->
            val canonical = artifact.canonicalFile
            if (!canonical.path.startsWith(canonicalRoot.path + File.separator)) {
                throw SecurityException("Vault migration artifact escaped the vault directory")
            }
            when {
                artifact.name.endsWith(".pin-migration-legacy") -> {
                    val original = File(artifact.parentFile, artifact.name.removeSuffix(".pin-migration-legacy"))
                    if (!original.exists()) {
                        check(artifact.renameTo(original)) { "Unable to recover legacy vault ciphertext" }
                    } else if (isPinWrappedVaultFile(original) || original.isFile) {
                        check(artifact.delete()) { "Unable to remove stale legacy vault ciphertext" }
                    }
                }
                artifact.name.endsWith(".migration-plain") ||
                    artifact.name.endsWith(".migration-verify") -> {
                    check(secureShredFile(artifact)) { "Unable to securely remove interrupted plaintext migration artifact" }
                }
                artifact.name.endsWith(".migration-new") -> {
                    check(artifact.delete()) { "Unable to remove interrupted encrypted migration staging file" }
                }
            }
        }
    }

    private fun sha256File(file: File): ByteArray {
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
            buffer.fill(0)
        }
        return digest.digest()
    }

    /** Clears the unwrapped data key when the vault session is locked. */
    @Synchronized
    fun lockVaultDataKey() {
        activeVaultDataKey?.fill(0)
        activeVaultDataKey = null
    }

    /** Legacy device-key encryption is reserved for decoy files and staged restore migration. */
    fun encryptLegacyFile(sourceFile: File, destinationFile: File): StreamEncryptionResult =
        FileInputStream(sourceFile).use { input ->
            FileOutputStream(destinationFile).use { output ->
                encryptStreamWithKey(input, output, getSecretKey(VAULT_KEY_ALIAS), includeMagic = false)
            }
        }

    private fun encryptStreamWithKey(
        sourceStream: InputStream,
        destinationStream: OutputStream,
        key: javax.crypto.SecretKey,
        includeMagic: Boolean
    ): StreamEncryptionResult {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        if (includeMagic) destinationStream.write(vaultFileMagic())
        destinationStream.write(iv)
        var totalBytes = 0L
        CipherOutputStream(destinationStream, cipher).use { encrypted ->
            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            while (true) {
                val count = sourceStream.read(buffer)
                if (count < 0) break
                encrypted.write(buffer, 0, count)
                totalBytes += count
            }
            encrypted.flush()
            buffer.fill(0)
        }
        return StreamEncryptionResult(Base64.encodeToString(iv, Base64.NO_WRAP), totalBytes)
    }

    private fun vaultFileMagic(): ByteArray = byteArrayOf(
        VAULT_FILE_MAGIC_0, VAULT_FILE_MAGIC_1, VAULT_FILE_MAGIC_2, VAULT_FILE_MAGIC_3
    )

    fun isPinWrappedVaultFile(file: File): Boolean {
        if (!file.isFile || file.length() < 4) return false
        return FileInputStream(file).use { input ->
            val prefix = ByteArray(4)
            readFully(input, prefix) && prefix.contentEquals(vaultFileMagic())
        }
    }

    private fun readFully(input: InputStream, target: ByteArray): Boolean {
        var offset = 0
        while (offset < target.size) {
            val count = input.read(target, offset, target.size - offset)
            if (count < 0) return false
            if (count == 0) continue
            offset += count
        }
        return true
    }

    data class StreamEncryptionResult(val ivBase64: String, val totalBytesEncrypted: Long)

    fun encryptStream(sourceStream: InputStream, destinationStream: OutputStream): StreamEncryptionResult {
        val keyBytes = activeVaultDataKey?.copyOf()
            ?: throw SecurityException("Vault is locked; PIN-wrapped data key is unavailable")
        return try {
            encryptStreamWithKey(
                sourceStream,
                destinationStream,
                SecretKeySpec(keyBytes, "AES"),
                includeMagic = true
            )
        } finally {
            keyBytes.fill(0)
        }
    }

    fun encryptFile(sourceFile: File, destinationFile: File): StreamEncryptionResult =
        FileInputStream(sourceFile).use { input ->
            FileOutputStream(destinationFile).use { output -> encryptStream(input, output) }
        }

    fun decryptStream(sourceStream: InputStream, destinationStream: OutputStream): Long {
        val prefix = ByteArray(4)
        if (!readFully(sourceStream, prefix)) {
            throw SecurityException("Invalid encrypted stream: truncated header")
        }
        val pinWrapped = prefix.contentEquals(vaultFileMagic())
        val payloadStream = if (pinWrapped) sourceStream else {
            java.io.SequenceInputStream(java.io.ByteArrayInputStream(prefix), sourceStream)
        }
        val iv = ByteArray(GCM_IV_LENGTH_BYTES)
        if (!readFully(payloadStream, iv)) {
            throw SecurityException("Invalid encrypted stream: missing or corrupt IV header")
        }
        val key = if (pinWrapped) {
            val keyBytes = activeVaultDataKey?.copyOf()
                ?: throw SecurityException("Vault is locked; PIN-wrapped data key is unavailable")
            try { SecretKeySpec(keyBytes, "AES") } finally { keyBytes.fill(0) }
        } else {
            getSecretKey(VAULT_KEY_ALIAS)
        }
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        var totalBytes = 0L
        CipherInputStream(payloadStream, cipher).use { decrypted ->
            val buffer = ByteArray(STREAM_BUFFER_SIZE)
            while (true) {
                val count = decrypted.read(buffer)
                if (count < 0) break
                destinationStream.write(buffer, 0, count)
                totalBytes += count
            }
            destinationStream.flush()
            buffer.fill(0)
        }
        return totalBytes
    }

    fun decryptFile(sourceFile: File, destinationFile: File): Long =
        FileInputStream(sourceFile).use { input ->
            FileOutputStream(destinationFile).use { output -> decryptStream(input, output) }
        }

    fun encryptBytes(data: ByteArray, alias: String = VAULT_KEY_ALIAS): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey(alias))
        return Pair(cipher.doFinal(data), cipher.iv)
    }

    fun decryptBytes(encryptedData: ByteArray, iv: ByteArray, alias: String = VAULT_KEY_ALIAS): ByteArray {
        val cipher = Cipher.getInstance(AES_GCM_TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(alias), GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv))
        return cipher.doFinal(encryptedData)
    }

    private fun decryptWithKeystore(encryptedData: ByteArray, iv: ByteArray, alias: String): ByteArray =
        decryptBytes(encryptedData, iv, alias)

    fun secureShredFile(file: File): Boolean {
        if (!file.exists()) return true
        return try {
            if (file.isFile && file.length() > 0) {
                val length = file.length()
                FileOutputStream(file).use { fos ->
                    val zeroBuffer = ByteArray(STREAM_BUFFER_SIZE)
                    var written = 0L
                    while (written < length) {
                        val toWrite = minOf(zeroBuffer.size.toLong(), length - written).toInt()
                        fos.write(zeroBuffer, 0, toWrite); written += toWrite
                    }
                    fos.flush(); fos.fd.sync()
                }
            }
            file.delete()
        } catch (_: Exception) { file.delete() }
    }

    sealed interface VaultAuthResult {
        data object SUCCESS_REAL : VaultAuthResult
        data object SUCCESS_DECOY : VaultAuthResult
        data class INVALID_PIN(val failedAttempts: Int, val lockoutSeconds: Int) : VaultAuthResult
        data class LOCKED_OUT(val remainingSeconds: Int) : VaultAuthResult
        data object INVALID_FORMAT : VaultAuthResult
    }

    private val prefs by lazy { context.getSharedPreferences("vvf_secure_vault_prefs", Context.MODE_PRIVATE) }

    fun isValidPinFormat(pin: String): Boolean = pin.length in 4..6 && pin.all { it.isDigit() }
    fun isVaultPinSet(): Boolean = isVaultConfigured()
    fun saveVaultPin(pin: String): Boolean = setupVaultPin(pin)
    fun isDecoyPinSet(): Boolean = isDecoyVaultConfigured()
    fun saveDecoyPin(pin: String): Boolean = setupDecoyPin(pin)

    fun getRemainingLockoutSeconds(): Int {
        val state = readLockoutState() ?: return 0
        val nowElapsed = SystemClock.elapsedRealtime()
        val remainingMs = if (nowElapsed >= state.lastElapsedMs) {
            state.untilElapsedMs - nowElapsed
        } else {
            val durationMs = (state.untilElapsedMs - state.lastElapsedMs).coerceAtLeast(0L)
            if (durationMs > 0) {
                writeLockoutState(state.copy(untilElapsedMs = nowElapsed + durationMs, lastElapsedMs = nowElapsed))
            }
            durationMs
        }
        return if (remainingMs > 0) (remainingMs / 1000L).toInt().coerceAtLeast(1) else 0
    }

    fun getFailedAttemptsCount(): Int = readLockoutState()?.failedAttempts ?: 0

    private fun recordFailedAttempt(): VaultAuthResult.INVALID_PIN {
        val attempts = getFailedAttemptsCount() + 1
        val lockoutSec = when {
            attempts >= 15 -> 1800
            attempts >= 10 -> 300
            attempts >= 5 -> 30
            else -> 0
        }
        val nowElapsed = SystemClock.elapsedRealtime()
        writeLockoutState(LockoutState(attempts, nowElapsed + lockoutSec * 1000L, nowElapsed))
        return VaultAuthResult.INVALID_PIN(attempts, lockoutSec)
    }

    private fun resetFailedAttempts() {
        prefs.edit().remove(LOCKOUT_STATE_KEY).remove(LOCKOUT_STATE_IV_KEY)
            .remove("vault_failed_attempts").remove("vault_lockout_until_ms").apply()
    }

    private data class LockoutState(val failedAttempts: Int, val untilElapsedMs: Long, val lastElapsedMs: Long)

    private fun writeLockoutState(state: LockoutState) {
        val plaintext = listOf(state.failedAttempts, state.untilElapsedMs, state.lastElapsedMs)
            .joinToString("|").toByteArray(Charsets.UTF_8)
        val (ciphertext, iv) = encryptBytes(plaintext, VAULT_META_KEY_ALIAS)
        plaintext.fill(0)
        check(prefs.edit()
            .putString(LOCKOUT_STATE_KEY, Base64.encodeToString(ciphertext, Base64.NO_WRAP))
            .putString(LOCKOUT_STATE_IV_KEY, Base64.encodeToString(iv, Base64.NO_WRAP))
            .remove("vault_failed_attempts").remove("vault_lockout_until_ms")
            .commit()) { "Could not persist authenticated vault lockout state" }
    }

    private fun readLockoutState(): LockoutState? {
        val encoded = prefs.getString(LOCKOUT_STATE_KEY, null) ?: return null
        val encodedIv = prefs.getString(LOCKOUT_STATE_IV_KEY, null)
            ?: throw SecurityException("Authenticated vault lockout state is incomplete")
        return try {
            val plaintext = decryptBytes(
                Base64.decode(encoded, Base64.NO_WRAP),
                Base64.decode(encodedIv, Base64.NO_WRAP),
                VAULT_META_KEY_ALIAS
            )
            try {
                val fields = plaintext.toString(Charsets.UTF_8).split('|')
                require(fields.size == 3)
                LockoutState(
                    failedAttempts = fields[0].toInt().coerceAtLeast(0),
                    untilElapsedMs = fields[1].toLong().coerceAtLeast(0L),
                    lastElapsedMs = fields[2].toLong().coerceAtLeast(0L)
                )
            } finally { plaintext.fill(0) }
        } catch (error: Throwable) {
            throw SecurityException("Authenticated vault lockout state is invalid", error)
        }
    }

    fun isVaultConfigured(): Boolean = prefs.contains("vault_pin_hash") && prefs.contains("vault_pin_salt")
    fun isDecoyVaultConfigured(): Boolean = prefs.contains("vault_decoy_pin_hash") && prefs.contains("vault_decoy_pin_salt")

    fun setupVaultPin(pin: String): Boolean {
        if (!isValidPinFormat(pin)) return false
        val salt = ByteArray(16); SecureRandom().nextBytes(salt)
        val hash = hashPin(pin, salt)
        val (encHash, hashIv) = encryptBytes(hash, alias = VAULT_META_KEY_ALIAS)
        val (encSalt, saltIv) = encryptBytes(salt, alias = VAULT_META_KEY_ALIAS)
        prefs.edit()
            .putString("vault_pin_hash", Base64.encodeToString(encHash, Base64.NO_WRAP))
            .putString("vault_pin_hash_iv", Base64.encodeToString(hashIv, Base64.NO_WRAP))
            .putString("vault_pin_salt", Base64.encodeToString(encSalt, Base64.NO_WRAP))
            .putString("vault_pin_salt_iv", Base64.encodeToString(saltIv, Base64.NO_WRAP))
            .apply()
        return true
    }

    fun setupDecoyPin(decoyPin: String): Boolean {
        if (!isValidPinFormat(decoyPin)) return false
        if (matchesRealPin(decoyPin)) return false
        val salt = ByteArray(16); SecureRandom().nextBytes(salt)
        val hash = hashPin(decoyPin, salt)
        val (encHash, hashIv) = encryptBytes(hash, alias = VAULT_META_KEY_ALIAS)
        val (encSalt, saltIv) = encryptBytes(salt, alias = VAULT_META_KEY_ALIAS)
        prefs.edit()
            .putString("vault_decoy_pin_hash", Base64.encodeToString(encHash, Base64.NO_WRAP))
            .putString("vault_decoy_pin_hash_iv", Base64.encodeToString(hashIv, Base64.NO_WRAP))
            .putString("vault_decoy_pin_salt", Base64.encodeToString(encSalt, Base64.NO_WRAP))
            .putString("vault_decoy_pin_salt_iv", Base64.encodeToString(saltIv, Base64.NO_WRAP))
            .apply()
        return true
    }

    fun removeDecoyPin(): Boolean {
        prefs.edit().remove("vault_decoy_pin_hash").remove("vault_decoy_pin_hash_iv")
            .remove("vault_decoy_pin_salt").remove("vault_decoy_pin_salt_iv").apply()
        return true
    }

    fun verifyVaultPin(pin: String): Boolean = verifyVaultPinWithResult(pin) == VaultAuthResult.SUCCESS_REAL
    fun verifyDecoyPin(pin: String): Boolean = verifyVaultPinWithResult(pin) == VaultAuthResult.SUCCESS_DECOY
    fun isSameAsVaultPin(pin: String): Boolean = isValidPinFormat(pin) && matchesRealPin(pin)

    fun verifyVaultPinWithResult(pin: String): VaultAuthResult {
        if (!isValidPinFormat(pin)) return VaultAuthResult.INVALID_FORMAT
        val remainingLockout = getRemainingLockoutSeconds()
        if (remainingLockout > 0) return VaultAuthResult.LOCKED_OUT(remainingLockout)
        if (matchesRealPin(pin)) { resetFailedAttempts(); return VaultAuthResult.SUCCESS_REAL }
        if (matchesDecoyPin(pin)) { resetFailedAttempts(); return VaultAuthResult.SUCCESS_DECOY }
        return recordFailedAttempt()
    }

    private fun matchesRealPin(pin: String): Boolean = matchesStoredPin(pin, "vault_pin_hash", "vault_pin_hash_iv", "vault_pin_salt", "vault_pin_salt_iv")
    private fun matchesDecoyPin(pin: String): Boolean = matchesStoredPin(pin, "vault_decoy_pin_hash", "vault_decoy_pin_hash_iv", "vault_decoy_pin_salt", "vault_decoy_pin_salt_iv")

    private fun matchesStoredPin(pin: String, hashKey: String, hashIvKey: String, saltKey: String, saltIvKey: String): Boolean {
        val hash = prefs.getString(hashKey, null) ?: return false
        val hashIv = prefs.getString(hashIvKey, null) ?: return false
        val salt = prefs.getString(saltKey, null) ?: return false
        val saltIv = prefs.getString(saltIvKey, null) ?: return false
        return try {
            val expectedHash = decryptBytes(Base64.decode(hash, Base64.NO_WRAP), Base64.decode(hashIv, Base64.NO_WRAP), VAULT_META_KEY_ALIAS)
            val decodedSalt = decryptBytes(Base64.decode(salt, Base64.NO_WRAP), Base64.decode(saltIv, Base64.NO_WRAP), VAULT_META_KEY_ALIAS)
            MessageDigest.isEqual(expectedHash, hashPin(pin, decodedSalt))
        } catch (_: Exception) { false }
    }

    fun changeVaultPin(oldPin: String, newPin: String): Boolean {
        if (!verifyVaultPin(oldPin)) return false
        return setupVaultPin(newPin)
    }

    fun isBiometricEnabled(): Boolean = prefs.getBoolean("vault_biometric_enabled", false)
    fun setBiometricEnabled(enabled: Boolean) { prefs.edit().putBoolean("vault_biometric_enabled", enabled).apply() }
    fun getAutoLockTimeoutSeconds(): Int = prefs.getInt("vault_auto_lock_seconds", 60)
    fun setAutoLockTimeoutSeconds(seconds: Int) { prefs.edit().putInt("vault_auto_lock_seconds", seconds).apply() }
    fun wipeBuffer(buffer: ByteArray) { buffer.fill(0) }

    private fun hashPin(pin: String, salt: ByteArray): ByteArray {
        val pinChars = pin.toCharArray()
        return try {
            val spec = javax.crypto.spec.PBEKeySpec(pinChars, salt, PBKDF2_ITERATIONS, 256)
            try { javax.crypto.SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded }
            finally { spec.clearPassword() }
        } finally { pinChars.fill('\u0000') }
    }
}
