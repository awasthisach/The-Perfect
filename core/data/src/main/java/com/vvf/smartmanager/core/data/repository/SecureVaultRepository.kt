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
import java.util.UUID

/**
 * Concrete repository managing encrypted file vault, AES-256-GCM streaming encryption,
 * zero-knowledge PIN management, transaction journaling, and metadata persistence.
 */
class SecureVaultRepository(
    private val vaultDao: VaultDao,
    private val cryptoManager: CryptoSecurityManager,
    private val vaultDirectory: File,
    private val vaultJournalDao: VaultJournalDao? = null
) {
    fun getAllVaultItems(isDecoy: Boolean = false): Flow<List<VaultItem>> =
        vaultDao.getAllVaultItems(isDecoy).map { list -> list.map { it.toDomainModel() } }

    fun getVaultItemsByCategory(category: String, isDecoy: Boolean = false): Flow<List<VaultItem>> =
        vaultDao.getVaultItemsByCategory(category, isDecoy).map { list -> list.map { it.toDomainModel() } }

    fun getVaultItemCount(isDecoy: Boolean = false): Flow<Int> = vaultDao.getVaultItemCount(isDecoy)

    fun getVaultTotalSizeBytes(isDecoy: Boolean = false): Flow<Long?> = vaultDao.getVaultTotalBytes(isDecoy)

    suspend fun getVaultItemById(id: String): VaultItem? =
        vaultDao.getVaultItemById(id)?.toDomainModel()

    /**
     * Recover any incomplete vault transactions from app crashes using the Vault Operation Journal.
     */
    suspend fun recoverOrphanedJournals(): Int {
        if (vaultJournalDao == null) return 0
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
            } catch (e: Exception) {
                vaultJournalDao.updateJournal(journal.copy(status = "FAILED"))
            }
        }
        vaultJournalDao.purgeCompletedJournals()
        return recoveredCount
    }

    /**
     * Encrypts and locks an external file into the isolated secure vault.
     * Uses Vault Operation Journaling to prevent split-brain states during crashes.
     */
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

        if (!vaultDirectory.exists()) {
            vaultDirectory.mkdirs()
        }

        val fileId = UUID.randomUUID().toString()
        val encryptedFileName = "enc_${fileId}.vvf"
        val destinationFile = File(vaultDirectory, encryptedFileName)

        // Write PENDING Journal Entry for crash protection
        val journalId = vaultJournalDao?.insertJournal(
            VaultJournalEntity(
                operationType = "ENCRYPT",
                originalPath = sourceFile.absolutePath,
                vaultPath = destinationFile.absolutePath,
                status = "PENDING"
            )
        ) ?: 0L

        try {
            // Stream encrypt with Keystore AES-256-GCM
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

            // Secure zero-knowledge file shredding of original plaintext file
            if (deleteOriginal) {
                cryptoManager.secureShredFile(sourceFile)
            }

            if (journalId > 0L) {
                vaultJournalDao?.updateJournal(
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
                vaultJournalDao?.updateJournal(
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
                destinationFile.delete()
            }
            throw e
        }
    }

    /**
     * Restores and decrypts an encrypted vault file back to disk.
     * Decrypts to [destinationDir] or the original path, and removes the encrypted vault file.
     */
    suspend fun restoreFileFromVault(
        vaultItemId: String,
        destinationDir: File? = null
    ): Result<File> = runCatching {
        val entity = vaultDao.getVaultItemById(vaultItemId)
            ?: throw IllegalStateException("Vault item not found in database: $vaultItemId")

        val encryptedFile = File(vaultDirectory, entity.encryptedFileName)
        if (!encryptedFile.exists()) {
            throw IllegalStateException("Encrypted file not found on disk: ${entity.encryptedFileName}")
        }

        val targetFile = if (destinationDir != null) {
            if (!destinationDir.exists()) destinationDir.mkdirs()
            File(destinationDir, entity.originalName)
        } else {
            val originalFile = File(entity.originalPath)
            val parent = originalFile.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
            originalFile
        }

        // Decrypt AES-256-GCM stream
        cryptoManager.decryptFile(encryptedFile, targetFile)

        // Shred encrypted file and delete record from DB
        cryptoManager.secureShredFile(encryptedFile)
        vaultDao.deleteById(vaultItemId)

        targetFile
    }

    /**
     * Decrypts and exports a decrypted copy of the vault item without deleting it from vault.
     */
    suspend fun exportFileFromVault(
        vaultItemId: String,
        destinationFile: File
    ): Result<File> = runCatching {
        val canonicalDest = destinationFile.canonicalPath
        val canonicalVault = vaultDirectory.canonicalPath
        require(!canonicalDest.startsWith(canonicalVault)) {
            "Export destination cannot be inside the isolated secure vault directory"
        }

        val entity = vaultDao.getVaultItemById(vaultItemId)
            ?: throw IllegalStateException("Vault item not found in database: $vaultItemId")

        val encryptedFile = File(vaultDirectory, entity.encryptedFileName)
        if (!encryptedFile.exists()) {
            throw IllegalStateException("Encrypted file not found on disk: ${entity.encryptedFileName}")
        }

        val parent = destinationFile.parentFile
        if (parent != null && !parent.exists()) parent.mkdirs()

        cryptoManager.decryptFile(encryptedFile, destinationFile)
        destinationFile
    }

    /**
     * Permanently and securely deletes a vault item from disk and DB.
     */
    suspend fun deleteVaultItemPermanently(vaultItemId: String): Result<Boolean> = runCatching {
        val entity = vaultDao.getVaultItemById(vaultItemId)
        if (entity != null) {
            val encryptedFile = File(vaultDirectory, entity.encryptedFileName)
            if (encryptedFile.exists()) {
                cryptoManager.secureShredFile(encryptedFile)
            }
            vaultDao.deleteById(vaultItemId)
        }
        true
    }

    // ========================================================================
    // PIN & Biometric Management (Supports Decoy/Fake Vault)
    // ========================================================================

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
