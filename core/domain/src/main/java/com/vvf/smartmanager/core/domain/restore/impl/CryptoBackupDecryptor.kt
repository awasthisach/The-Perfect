package com.vvf.smartmanager.core.domain.restore.impl

import com.vvf.smartmanager.core.domain.restore.BackupDecryptor
import com.vvf.smartmanager.core.domain.restore.DecryptedBackup
import com.vvf.smartmanager.core.domain.restore.RestoreException
import com.vvf.smartmanager.core.domain.restore.VerifiedArtifact
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import java.io.File
import java.util.zip.ZipInputStream

/** Decrypts a verified .vvfbak archive into isolated staging (zip-slip safe). */
class CryptoBackupDecryptor(
    private val crypto: CryptoSecurityManager
) : BackupDecryptor {
    override suspend fun decrypt(
        verifiedArtifact: VerifiedArtifact,
        stagingDir: File
    ): Result<DecryptedBackup> {
        return try {
            if (!stagingDir.exists() && !stagingDir.mkdirs()) {
                return Result.failure(RestoreException("Unable to create decrypt staging directory"))
            }
            val decryptedZip = File(stagingDir, "payload.zip")
            crypto.decryptFile(verifiedArtifact.file, decryptedZip)
            if (!decryptedZip.isFile || decryptedZip.length() == 0L) {
                return Result.failure(RestoreException("Decryption produced no payload"))
            }
            unzip(decryptedZip, stagingDir)
            decryptedZip.delete()
            val databaseFile = File(stagingDir, "database").walkTopDown().firstOrNull { it.isFile }
                ?: File(stagingDir, "database")
            val vaultDir = File(stagingDir, "vault").also { if (!it.exists()) it.mkdirs() }
            Result.success(
                DecryptedBackup(
                    stagingDir = stagingDir,
                    databaseFile = databaseFile,
                    vaultDir = vaultDir,
                    backupInfo = verifiedArtifact.backupInfo,
                    timestamp = System.currentTimeMillis()
                )
            )
        } catch (error: Throwable) {
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
