package com.vvf.smartmanager.core.domain.restore.impl

import com.vvf.smartmanager.core.domain.restore.BackupDecryptor
import com.vvf.smartmanager.core.domain.restore.DecryptedBackup
import com.vvf.smartmanager.core.domain.restore.RestoreException
import com.vvf.smartmanager.core.domain.restore.VerifiedArtifact
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import com.vvf.smartmanager.core.security.PortableBackupCrypto
import java.io.File
import java.util.zip.ZipInputStream

/** Decrypts a verified .vvfbak archive into isolated staging (zip-slip safe). */
class CryptoBackupDecryptor(
    private val crypto: CryptoSecurityManager,
    private val portableCrypto: PortableBackupCrypto = PortableBackupCrypto()
) : BackupDecryptor {
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
            val databaseDir = File(stagingDir, "database")
            val databaseFile = databaseDir.walkTopDown().firstOrNull {
                it.isFile && !it.name.endsWith("-wal") && !it.name.endsWith("-shm")
            } ?: databaseDir
            val vaultDir = File(stagingDir, "vault").also { if (!it.exists()) it.mkdirs() }
            val databasePassphraseFile = File(stagingDir, "key-material/database-passphrase.bin")
            if (isPortable && verifiedArtifact.backupInfo.includesDatabase) {
                require(databasePassphraseFile.isFile && databasePassphraseFile.length() == 32L) {
                    "Portable archive is missing valid database key material"
                }
            }
            if (isPortable && verifiedArtifact.backupInfo.includesVault) {
                vaultDir.walkTopDown().filter { it.isFile }.toList().forEach { plainFile ->
                    val encryptedTemp = File(plainFile.parentFile, plainFile.name + ".local-encrypted")
                    crypto.encryptFile(plainFile, encryptedTemp)
                    if (!plainFile.delete()) {
                        encryptedTemp.delete()
                        throw SecurityException("Unable to remove plaintext vault staging file")
                    }
                    if (!encryptedTemp.renameTo(plainFile)) {
                        encryptedTemp.copyTo(plainFile, overwrite = true)
                        if (!encryptedTemp.delete()) throw SecurityException("Unable to clean temporary encrypted vault file")
                    }
                }
            }
            Result.success(
                DecryptedBackup(
                    stagingDir = stagingDir,
                    databaseFile = databaseFile,
                    vaultDir = vaultDir,
                    backupInfo = verifiedArtifact.backupInfo,
                    timestamp = System.currentTimeMillis(),
                    databasePassphraseFile = databasePassphraseFile.takeIf { isPortable && it.isFile }
                )
            )
        } catch (error: Throwable) {
            passphrase?.fill(0.toChar())
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
