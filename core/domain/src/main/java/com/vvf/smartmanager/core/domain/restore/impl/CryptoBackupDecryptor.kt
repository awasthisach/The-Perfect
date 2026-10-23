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
            // Never select a WAL/SHM sidecar as the primary DB. Accept the legacy root
            // layout only for the two documented database names, not an arbitrary first file.
            val databaseDir = File(stagingDir, "database")
            val candidates = databaseDir.listFiles().orEmpty().filter {
                it.isFile && !it.name.endsWith("-wal") && !it.name.endsWith("-shm")
            }.ifEmpty {
                listOf("vvf_smart_manager_enc.db", "vvf_smart_manager.db").map { File(stagingDir, it) }.filter { it.isFile }
            }
            require(candidates.size <= 1) { "Ambiguous database payload" }
            val databaseFile = candidates.singleOrNull() ?: File(databaseDir, "missing.db")
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
        var totalBytes = 0L
        val names = HashSet<String>()
        ZipInputStream(zipFile.inputStream()).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                require(names.add(entry.name) && names.size <= 100_000) { "Duplicate or excessive archive entries" }
                val out = File(targetDir, entry.name)
                require(out.canonicalFile != zipFile.canonicalFile) { "Archive overwrites input" }
                val normalized = out.canonicalFile
                if (!normalized.path.startsWith(targetDir.canonicalFile.path + File.separator) &&
                    normalized != targetDir.canonicalFile
                ) {
                    throw RestoreException("Zip slip blocked for entry ${entry.name}")
                }
                if (entry.isDirectory) out.mkdirs()
                else {
                    out.parentFile?.mkdirs()
                    out.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val count = zis.read(buffer)
                            if (count < 0) break
                            totalBytes += count
                            require(totalBytes <= 4L * 1024 * 1024 * 1024) { "Archive exceeds restore limit" }
                            output.write(buffer, 0, count)
                        }
                    }
                }
                zis.closeEntry()
            }
        }
    }
}
