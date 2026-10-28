package com.vvf.smartmanager.core.domain.restore.impl

import com.vvf.smartmanager.core.domain.backup.ArchiveMetadata
import com.vvf.smartmanager.core.domain.restore.BackupDecryptor
import com.vvf.smartmanager.core.domain.restore.DecryptedBackup
import com.vvf.smartmanager.core.domain.restore.RestoreException
import com.vvf.smartmanager.core.domain.restore.VerifiedArtifact
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import com.vvf.smartmanager.core.security.PortableBackupCrypto
import kotlinx.serialization.json.Json
import java.io.File
import java.util.zip.ZipInputStream

/** Decrypts a verified .vvfbak archive into isolated staging (zip-slip safe). */
class CryptoBackupDecryptor(
    private val crypto: CryptoSecurityManager,
    private val portableCrypto: PortableBackupCrypto = PortableBackupCrypto()
) : BackupDecryptor {
    private val json = Json { ignoreUnknownKeys = true }
    override suspend fun decrypt(
        verifiedArtifact: VerifiedArtifact,
        stagingDir: File,
        passphrase: CharArray?
    ): Result<DecryptedBackup> {
        return try {
            if (!stagingDir.exists() && !stagingDir.mkdirs()) {
                return Result.failure(RestoreException("Unable to create decrypt staging directory"))
            }
            val decryptedZip = File(stagingDir, "payload.zip")
            val magic = ByteArray(4)
            val isPortable = verifiedArtifact.file.inputStream().use { input ->
                var offset = 0
                while (offset < magic.size) {
                    val count = input.read(magic, offset, magic.size - offset)
                    if (count < 0) break
                    offset += count
                }
                offset == 4 && magic.contentEquals(byteArrayOf(0x56, 0x56, 0x46, 0x42))
            }
            if (isPortable) {
                val portablePassphrase = passphrase
                    ?: throw SecurityException("Backup passphrase is required for this portable archive")
                require(portablePassphrase.isNotEmpty()) { "Backup passphrase must not be empty" }
                portableCrypto.decryptFile(verifiedArtifact.file, decryptedZip, portablePassphrase)
            } else {
                crypto.decryptFile(verifiedArtifact.file, decryptedZip)
            }
            if (!decryptedZip.isFile || decryptedZip.length() == 0L) {
                return Result.failure(RestoreException("Decryption produced no payload"))
            }
            unzip(decryptedZip, stagingDir)
            decryptedZip.delete()
            val metadataFile = File(stagingDir, "metadata.json")
            val metadata = metadataFile.takeIf { it.isFile }?.let {
                json.decodeFromString(ArchiveMetadata.serializer(), it.readText())
            }
            val databaseDir = File(stagingDir, "database")
            val databaseInCurrentLayout = databaseDir.walkTopDown().firstOrNull {
                it.isFile && !it.name.endsWith("-wal") && !it.name.endsWith("-shm")
            }
            // Archives created before the database/ staging layout stored the DB at the ZIP root.
            // Keep those device-bound archives restorable on the same device without treating
            // metadata or legacy PIN metadata as a database payload.
            val legacyRootDatabase = if (databaseInCurrentLayout == null) {
                stagingDir.listFiles().orEmpty().firstOrNull {
                    it.isFile &&
                        it.name != "metadata.json" &&
                        it.name != "vault_auth.json" &&
                        it.name != "payload.zip" &&
                        it.name.endsWith(".db", ignoreCase = true) &&
                        !it.name.endsWith("-wal", ignoreCase = true) &&
                        !it.name.endsWith("-shm", ignoreCase = true)
                }
            } else null
            val databaseFile = databaseInCurrentLayout ?: legacyRootDatabase ?: databaseDir
            val hasDatabasePayload = databaseFile.isFile && databaseFile.length() > 0L
            val vaultDir = File(stagingDir, "vault").also { if (!it.exists()) it.mkdirs() }
            val vaultItemCount = vaultDir.walkTopDown().count { it.isFile }
            // v3 records explicit flags. Older archives are inferred from actual payloads because
            // the remote provider metadata does not carry the includeVault/includeDatabase fields.
            val includesDatabase = if (metadata != null && metadata.version >= 3) {
                metadata.includesDatabase
            } else {
                hasDatabasePayload
            }
            if (metadata != null && metadata.version !in 1..3) {
                throw SecurityException("Unsupported archive metadata version: ${metadata.version}")
            }
            val includesVault = if (metadata != null && metadata.version >= 3) {
                metadata.includesVault
            } else {
                (metadata?.vaultItemCount ?: vaultItemCount) > 0
            }
            if (includesDatabase && !hasDatabasePayload) {
                throw SecurityException("Archive declares a database but contains no database payload")
            }
            if (metadata != null && metadata.version >= 3) {
                if (!includesDatabase && hasDatabasePayload) {
                    throw SecurityException("Archive metadata excludes a database that is present in the payload")
                }
                if (!includesVault && vaultItemCount > 0) {
                    throw SecurityException("Archive metadata excludes vault contents that are present in the payload")
                }
            }
            if (metadata != null) {
                val actualFileCount = stagingDir.walkTopDown()
                    .count { it.isFile && it.name != "metadata.json" }
                if (actualFileCount < metadata.fileCount) {
                    throw SecurityException("Archive payload is incomplete: expected ${metadata.fileCount} files, found $actualFileCount")
                }
                if (vaultItemCount < metadata.vaultItemCount) {
                    throw SecurityException("Archive vault payload is incomplete: expected ${metadata.vaultItemCount} items, found $vaultItemCount")
                }
            }
            val actualBackupInfo = verifiedArtifact.backupInfo.copy(
                includesDatabase = includesDatabase,
                includesVault = includesVault
            )
            val databasePassphraseFile = File(stagingDir, "key-material/database-passphrase.bin")
            if (isPortable && actualBackupInfo.includesDatabase) {
                require(databasePassphraseFile.isFile && databasePassphraseFile.length() == 32L) {
                    "Portable archive is missing valid database key material"
                }
            }
            if (isPortable && actualBackupInfo.includesVault) {
                vaultDir.walkTopDown().filter { it.isFile }.toList().forEach { plainFile ->
                    val encryptedTemp = File(plainFile.parentFile, plainFile.name + ".local-encrypted")
                    try {
                        crypto.encryptFile(plainFile, encryptedTemp)
                        if (!crypto.secureShredFile(plainFile) && plainFile.exists()) {
                            throw SecurityException("Unable to securely remove plaintext vault staging file")
                        }
                        if (!encryptedTemp.renameTo(plainFile)) {
                            encryptedTemp.copyTo(plainFile, overwrite = true)
                            if (!encryptedTemp.delete()) {
                                throw SecurityException("Unable to clean temporary encrypted vault file")
                            }
                        }
                    } catch (error: Throwable) {
                        // A failed conversion can leave plaintext plus a partial ciphertext file.
                        // Shred both immediately; outer staging cleanup is only a last resort.
                        val encryptedRemoved = crypto.secureShredFile(encryptedTemp)
                        val plaintextRemoved = crypto.secureShredFile(plainFile)
                        if ((!encryptedRemoved && encryptedTemp.exists()) ||
                            (!plaintextRemoved && plainFile.exists())
                        ) {
                            throw SecurityException(
                                "Unable to securely remove failed vault conversion artifacts",
                                error
                            )
                        }
                        throw error
                    }
                }
            }
            Result.success(
                DecryptedBackup(
                    stagingDir = stagingDir,
                    databaseFile = databaseFile,
                    vaultDir = vaultDir,
                    backupInfo = actualBackupInfo,
                    timestamp = System.currentTimeMillis(),
                    databasePassphraseFile = databasePassphraseFile.takeIf { isPortable && it.isFile }
                )
            )
        } catch (error: Throwable) {
            passphrase?.fill(0.toChar())
            // This directory is isolated and owned by the restore attempt; it may contain
            // plaintext vault payload or the imported SQLCipher passphrase after partial unzip.
            stagingDir.deleteRecursively()
            Result.failure(RestoreException("Backup decryption failed", error))
        }
    }

    private fun unzip(zipFile: File, targetDir: File) {
        ZipInputStream(zipFile.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                val out = File(targetDir, entry.name)
                val normalized = out.canonicalFile
                if (!normalized.path.startsWith(targetDir.canonicalFile.path + File.separator) &&
                    normalized != targetDir.canonicalFile
                ) {
                    throw RestoreException("Zip slip blocked for entry ${entry.name}")
                }
                if (entry.isDirectory) out.mkdirs()
                else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { zis.copyTo(it) }
                }
                zis.closeEntry()
            }
        }
    }
}
