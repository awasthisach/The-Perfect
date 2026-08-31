package com.vvf.smartmanager.core.data.repository

import com.vvf.smartmanager.core.database.dao.VaultDao
import com.vvf.smartmanager.core.database.dao.VaultJournalDao
import com.vvf.smartmanager.core.database.model.VaultItemEntity
import com.vvf.smartmanager.core.database.model.VaultJournalEntity
import com.vvf.smartmanager.core.model.VaultItem
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * Concrete repository managing encrypted file vault, AES-256-GCM streaming encryption,
 * zero-knowledge PIN management, transaction journaling, and metadata persistence.
 */
class SecureVaultRepository(
    private val vaultDao: VaultDao,
    private val cryptoManager: CryptoSecurityManager,
    private val vaultDirectory: File,
    private val vaultJournalDao: VaultJournalDao
) {
    fun getAllVaultItems(isDecoy: Boolean = false): Flow<List<VaultItem>> =
        vaultDao.getAllVaultItems(isDecoy).map { list -> list.map { it.toDomainModel() } }

    fun getVaultItemsByCategory(category: String, isDecoy: Boolean = false): Flow<List<VaultItem>> =
        vaultDao.getVaultItemsByCategory(category, isDecoy).map { list -> list.map { it.toDomainModel() } }

    fun getVaultItemCount(isDecoy: Boolean = false): Flow<Int> = vaultDao.getVaultItemCount(isDecoy)

    fun getVaultTotalSizeBytes(isDecoy: Boolean = false): Flow<Long?> = vaultDao.getVaultTotalBytes(isDecoy)

    suspend fun getVaultItemById(id: String): VaultItem? =
        vaultDao.getVaultItemById(id)?.toDomainModel()

    suspend fun recoverOrphanedJournals(): Int {
        val pendingJournals = vaultJournalDao.getPendingJournals("PENDING")
        var recoveredCount = 0

        for (journal in pendingJournals) {
            try {
                when (journal.operationType) {
                    "ENCRYPT" -> {
                        val encFile = File(journal.vaultPath)
                        if (encFile.exists() && encFile.length() == 0L) {
                            encFile.delete()
                        }
                        vaultJournalDao.updateJournal(journal.copy(status = "FAILED"))
                        recoveredCount++
                    }
                    "DECRYPT" -> {
                        vaultJournalDao.updateJournal(journal.copy(status = "FAILED"))
                        recoveredCount++
                    }
                }
            } catch (_: Exception) {
                vaultJournalDao.updateJournal(journal.copy(status = "FAILED"))
            }
        }
        vaultJournalDao.purgeCompletedJournals()
        return recoveredCount
    }

    suspend fun lockFileInVault(
        sourceFile: File,
        category: String = "Other",
        notes: String = "",
        deleteOriginal: Boolean = false,
        isDecoy: Boolean = false
    ): Result<VaultItem> = runCatching {
        require(sourceFile.exists() && sourceFile.isFile) {
            "Source file does not exist or is a directory: ${sourceFile.absolutePath}"
        }

        val canonicalSource = sourceFile.canonicalFile
        val canonicalVault = vaultDirectory.canonicalFile
        require(!isInsideDirectory(canonicalSource, canonicalVault)) {
            "Source file cannot already be inside the vault directory: ${sourceFile.absolutePath}"
        }

        require(vaultDirectory.exists() || vaultDirectory.mkdirs()) {
            "Unable to create secure vault directory: ${vaultDirectory.absolutePath}"
        }

        val fileId = UUID.randomUUID().toString()
        val encryptedFileName = "enc_${fileId}.vvf"
        val destinationFile = File(vaultDirectory, encryptedFileName)
        require(!isInsideDirectory(destinationFile.canonicalFile, canonicalVault).not()) {
            "Encrypted destination escaped the secure vault directory"
        }

        val journalId = vaultJournalDao.insertJournal(
            VaultJournalEntity(
                operationType = "ENCRYPT",
                originalPath = sourceFile.absolutePath,
                vaultPath = destinationFile.absolutePath,
                status = "PENDING"
            )
        ) ?: 0L

        try {
            val result = cryptoManager.encryptFile(sourceFile, destinationFile)

            val entity = VaultItemEntity(
                id = fileId,
                encryptedFileName = encryptedFileName,
                originalName = sourceFile.name,
                originalPath = sourceFile.absolutePath,
                sizeBytes = sourceFile.length(),
                mimeType = getMimeType(sourceFile.name),
                category = category,
                encryptionIv = result.ivBase64,
                createdAt = System.currentTimeMillis(),
                notes = notes.ifBlank { null },
                isDecoy = isDecoy
            )

            vaultDao.insertVaultItem(entity)

            if (deleteOriginal) {
                require(cryptoManager.secureShredFile(sourceFile)) {
                    "Encrypted vault copy was created, but original plaintext could not be securely removed"
                }
            }

            if (journalId > 0L) {
                vaultJournalDao.updateJournal(
                    VaultJournalEntity(
                        id = journalId,
                        operationType = "ENCRYPT",
                        originalPath = sourceFile.absolutePath,
                        vaultPath = destinationFile.absolutePath,
                        status = "COMPLETED"
                    )
                )
            }

            entity.toDomainModel()
        } catch (e: Exception) {
            if (journalId > 0L) {
                vaultJournalDao.updateJournal(
                    VaultJournalEntity(
                        id = journalId,
                        operationType = "ENCRYPT",
                        originalPath = sourceFile.absolutePath,
                        vaultPath = destinationFile.absolutePath,
                        status = "FAILED"
                    )
                )
            }
            if (destinationFile.exists()) {
                cryptoManager.secureShredFile(destinationFile)
            }
            throw e
        }
    }

    suspend fun restoreFileFromVault(
        vaultItemId: String,
        destinationDir: File? = null
    ): Result<File> = runCatching {
        val entity = vaultDao.getVaultItemById(vaultItemId)
            ?: throw IllegalStateException("Vault item not found in database: $vaultItemId")

        val encryptedFile = File(vaultDirectory, entity.encryptedFileName).canonicalFile
        val canonicalVault = vaultDirectory.canonicalFile
        require(isInsideDirectory(encryptedFile, canonicalVault)) {
            "Encrypted vault path escaped the isolated secure vault directory"
        }
        if (!encryptedFile.exists() || !encryptedFile.isFile) {
            throw IllegalStateException("Encrypted file not found on disk: ${entity.encryptedFileName}")
        }

        val targetFile = if (destinationDir != null) {
            require(destinationDir.exists() || destinationDir.mkdirs()) {
                "Unable to create restore destination: ${destinationDir.absolutePath}"
            }
            File(destinationDir, entity.originalName)
        } else {
            val originalFile = File(entity.originalPath)
            val parent = originalFile.parentFile
            if (parent != null) {
                require(parent.exists() || parent.mkdirs()) {
                    "Unable to create restore destination: ${parent.absolutePath}"
                }
            }
            originalFile
        }

        val targetCanonical = targetFile.canonicalFile
        require(!isInsideDirectory(targetCanonical, canonicalVault)) {
            "Restore destination cannot be inside the isolated secure vault directory"
        }
        require(!targetCanonical.exists()) {
            "Restore destination already exists; refusing to overwrite plaintext: ${targetCanonical.absolutePath}"
        }

        // GCM authentication is only known after EOF. Decrypt to a same-directory temporary file
        // and publish it only after authenticated decryption succeeds.
        val tempFile = createSiblingTempFile(targetCanonical)
        try {
            cryptoManager.decryptFile(encryptedFile, tempFile)
            require(tempFile.renameTo(targetCanonical)) {
                "Unable to atomically publish restored file: ${targetCanonical.absolutePath}"
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }

        cryptoManager.secureShredFile(encryptedFile)
        vaultDao.deleteById(vaultItemId)

        targetCanonical
    }

    suspend fun exportFileFromVault(
        vaultItemId: String,
        destinationFile: File
    ): Result<File> = runCatching {
        val canonicalDest = destinationFile.canonicalFile
        val canonicalVault = vaultDirectory.canonicalFile
        require(!isInsideDirectory(canonicalDest, canonicalVault)) {
            "Export destination cannot be inside the isolated secure vault directory"
        }
        require(!canonicalDest.exists()) {
            "Export destination already exists; refusing to overwrite plaintext: ${canonicalDest.absolutePath}"
        }

        val entity = vaultDao.getVaultItemById(vaultItemId)
            ?: throw IllegalStateException("Vault item not found in database: $vaultItemId")

        val encryptedFile = File(vaultDirectory, entity.encryptedFileName).canonicalFile
        require(isInsideDirectory(encryptedFile, canonicalVault)) {
            "Encrypted vault path escaped the isolated secure vault directory"
        }
        if (!encryptedFile.exists() || !encryptedFile.isFile) {
            throw IllegalStateException("Encrypted file not found on disk: ${entity.encryptedFileName}")
        }

        val parent = canonicalDest.parentFile
        if (parent != null) {
            require(parent.exists() || parent.mkdirs()) {
                "Unable to create export destination: ${parent.absolutePath}"
            }
        }

        val tempFile = createSiblingTempFile(canonicalDest)
        try {
            cryptoManager.decryptFile(encryptedFile, tempFile)
            require(tempFile.renameTo(canonicalDest)) {
                "Unable to publish exported file: ${canonicalDest.absolutePath}"
            }
        } catch (e: Exception) {
            tempFile.delete()
            throw e
        }

        canonicalDest
    }

    suspend fun deleteVaultItemPermanently(vaultItemId: String): Result<Boolean> = runCatching {
        val entity = vaultDao.getVaultItemById(vaultItemId)
        if (entity != null) {
            val encryptedFile = File(vaultDirectory, entity.encryptedFileName).canonicalFile
            val canonicalVault = vaultDirectory.canonicalFile
            require(isInsideDirectory(encryptedFile, canonicalVault)) {
                "Encrypted vault path escaped the isolated secure vault directory"
            }
            if (encryptedFile.exists()) {
                require(cryptoManager.secureShredFile(encryptedFile)) {
                    "Unable to securely remove encrypted vault file"
                }
            }
            vaultDao.deleteById(vaultItemId)
        }
        true
    }

    fun isVaultConfigured(): Boolean = cryptoManager.isVaultConfigured()
    fun isDecoyVaultConfigured(): Boolean = cryptoManager.isDecoyVaultConfigured()
    fun setupPin(pin: String): Boolean = cryptoManager.setupVaultPin(pin)
    fun setupDecoyPin(pin: String): Boolean = cryptoManager.setupDecoyPin(pin)
    fun removeDecoyPin(): Boolean = cryptoManager.removeDecoyPin()
    fun verifyPin(pin: String): Boolean = cryptoManager.verifyVaultPin(pin)
    fun verifyPinWithResult(pin: String): CryptoSecurityManager.VaultAuthResult =
        cryptoManager.verifyVaultPinWithResult(pin)
    fun getRemainingLockoutSeconds(): Int = cryptoManager.getRemainingLockoutSeconds()
    fun getFailedAttemptsCount(): Int = cryptoManager.getFailedAttemptsCount()
    fun changePin(oldPin: String, newPin: String): Boolean = cryptoManager.changeVaultPin(oldPin, newPin)
    fun isBiometricEnabled(): Boolean = cryptoManager.isBiometricEnabled()
    fun setBiometricEnabled(enabled: Boolean) = cryptoManager.setBiometricEnabled(enabled)
    fun getAutoLockTimeoutSeconds(): Int = cryptoManager.getAutoLockTimeoutSeconds()
    fun setAutoLockTimeoutSeconds(seconds: Int) = cryptoManager.setAutoLockTimeoutSeconds(seconds)

    private fun isInsideDirectory(file: File, directory: File): Boolean {
        val filePath = file.canonicalPath
        val directoryPath = directory.canonicalPath
        return filePath == directoryPath || filePath.startsWith(directoryPath + File.separator)
    }

    private fun createSiblingTempFile(target: File): File {
        val parent = target.parentFile ?: throw IOException("Target file has no parent directory")
        require(parent.exists() || parent.mkdirs()) { "Unable to create target directory: ${parent.absolutePath}" }
        return File(parent, ".${target.name}.${UUID.randomUUID()}.tmp").also {
            require(!it.exists()) { "Temporary restore file unexpectedly exists" }
        }
    }

    private fun VaultItemEntity.toDomainModel(): VaultItem {
        return VaultItem(
            id = id,
            encryptedFileName = encryptedFileName,
            originalName = originalName,
            originalPath = originalPath,
            mimeType = mimeType,
            sizeBytes = sizeBytes,
            category = category,
            encryptionIv = encryptionIv,
            createdAt = createdAt,
            notes = notes,
            isDecoy = isDecoy,
            vaultRelativePath = encryptedFileName,
            fileName = originalName,
            encryptedTimestamp = createdAt,
            ivBase64 = encryptionIv
        )
    }

    private fun getMimeType(fileName: String): String {
        val extension = fileName.substringAfterLast('.', "").lowercase()
        return when (extension) {
            "jpg", "jpeg", "png", "webp", "gif", "bmp", "heic" -> "image/$extension"
            "mp4", "mkv", "mov", "avi", "3gp", "webm" -> "video/$extension"
            "mp3", "wav", "m4a", "flac", "aac", "ogg" -> "audio/$extension"
            "pdf" -> "application/pdf"
            "doc", "docx" -> "application/msword"
            "xls", "xlsx" -> "application/vnd.ms-excel"
            "ppt", "pptx" -> "application/vnd.ms-powerpoint"
            "txt", "csv", "log", "json", "xml" -> "text/plain"
            "zip", "rar", "7z", "tar", "gz" -> "application/zip"
            "apk" -> "application/vnd.android.package-archive"
            else -> "application/octet-stream"
        }
    }
}
