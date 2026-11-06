package com.vvf.smartmanager.core.data.repository

import android.util.Log
import com.vvf.smartmanager.core.database.dao.FileDao
import com.vvf.smartmanager.core.database.dao.VaultDao
import com.vvf.smartmanager.core.database.dao.VaultJournalDao
import com.vvf.smartmanager.core.data.storage.PrimaryStorageMutationLock
import com.vvf.smartmanager.core.database.model.VaultItemEntity
import com.vvf.smartmanager.core.database.model.VaultJournalEntity
import com.vvf.smartmanager.core.model.VaultItem
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
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
    private val vaultJournalDao: VaultJournalDao,
    private val fileDao: FileDao,
    private val secureShredFile: (File) -> Boolean = cryptoManager::secureShredFile,
    private val awaitStartupReady: suspend () -> Unit = {},
    private val startupIsReady: () -> Boolean = { true }
) {
    fun getAllVaultItems(isDecoy: Boolean = false): Flow<List<VaultItem>> = flow {
        awaitStartupReady()
        emitAll(vaultDao.getAllVaultItems(isDecoy).map { list -> list.map { it.toDomainModel() } })
    }

    fun getVaultItemsByCategory(category: String, isDecoy: Boolean = false): Flow<List<VaultItem>> = flow {
        awaitStartupReady()
        emitAll(vaultDao.getVaultItemsByCategory(category, isDecoy).map { list -> list.map { it.toDomainModel() } })
    }

    fun getVaultItemCount(isDecoy: Boolean = false): Flow<Int> = flow {
        awaitStartupReady()
        emitAll(vaultDao.getVaultItemCount(isDecoy))
    }

    fun getVaultTotalSizeBytes(isDecoy: Boolean = false): Flow<Long?> = flow {
        awaitStartupReady()
        emitAll(vaultDao.getVaultTotalBytes(isDecoy))
    }

    suspend fun getVaultItemById(id: String): VaultItem? {
        awaitStartupReady()
        return vaultDao.getVaultItemById(id)?.toDomainModel()
    }

    /**
     * Remove stale ordinary-search rows for vault items whose plaintext source no longer exists.
     * Copy-only vault items keep their source metadata while the original file is still present.
     */
    suspend fun reconcileVaultedSourceMetadata(): Int {
        val vaultItems = vaultDao.getAllVaultItems(isDecoy = false).first() +
            vaultDao.getAllVaultItems(isDecoy = true).first()
        val missingSourcePaths = vaultItems
            .filter { item -> !File(item.originalPath).exists() }
            .flatMap { item ->
                val source = File(item.originalPath)
                listOf(item.originalPath, source.canonicalPath)
            }
            .distinct()

        if (missingSourcePaths.isNotEmpty()) {
            PrimaryStorageMutationLock.mutex.withLock {
                fileDao.deleteByPaths(missingSourcePaths)
            }
        }
        return missingSourcePaths.size
    }

    suspend fun recoverOrphanedJournals(): Int {
        val pendingJournals = vaultJournalDao.getPendingJournals("PENDING")
        val canonicalVault = vaultDirectory.canonicalFile
        var recoveredCount = 0

        for (journal in pendingJournals) {
            try {
                when (journal.operationType) {
                    "ENCRYPT" -> {
                        val encFile = File(journal.vaultPath).canonicalFile
                        // Never trust a persisted journal path for filesystem mutation.
                        // A corrupted/tampered journal must not become an arbitrary-file delete primitive.
                        if (!isInsideDirectory(encFile, canonicalVault)) {
                            // Do not mutate an untrusted path. Leave the journal pending so startup
                            // fails closed and the path can be reviewed rather than silently ignored.
                            Log.e(TAG, "Vault journal ${journal.id} points outside the vault directory")
                            continue
                        }

                        val vaultItem = vaultDao.getVaultItemByEncryptedName(encFile.name)
                        val sourceFile = File(journal.originalPath).canonicalFile
                        val sourceExists = sourceFile.exists()
                        when {
                            !encFile.exists() -> {
                                if (!sourceExists) {
                                    // Both copies are missing. Preserve the journal and fail closed;
                                    // silently deleting the only recovery record would hide data loss.
                                    Log.e(TAG, "Vault journal ${journal.id} has no source or encrypted copy")
                                    continue
                                }
                                vaultItem?.let { vaultDao.deleteById(it.id) }
                                vaultJournalDao.updateJournal(journal.copy(status = "FAILED"))
                                recoveredCount++
                            }
                            encFile.length() == 0L -> {
                                if (!sourceExists) {
                                    Log.e(TAG, "Vault journal ${journal.id} has an empty encrypted copy and no source")
                                    continue
                                }
                                // The plaintext is intact, so an empty staging copy can be removed.
                                if (secureShredFile(encFile)) {
                                    vaultItem?.let { vaultDao.deleteById(it.id) }
                                    vaultJournalDao.updateJournal(journal.copy(status = "FAILED"))
                                    recoveredCount++
                                }
                                // If deletion fails, leave the journal PENDING for a later retry.
                            }
                            vaultItem == null -> {
                                if (!sourceExists) {
                                    // This may be the only encrypted copy. Keep it for manual recovery.
                                    Log.e(TAG, "Vault journal ${journal.id} has an encrypted file but no vault row or source")
                                    continue
                                }
                                // The original is intact, so a non-empty encrypted orphan can be removed.
                                if (secureShredFile(encFile)) {
                                    vaultJournalDao.updateJournal(journal.copy(status = "FAILED"))
                                    recoveredCount++
                                }
                            }
                            !sourceExists -> {
                                // The plaintext was removed and the encrypted item is present.
                                // Finish the metadata cleanup that may have been interrupted.
                                PrimaryStorageMutationLock.mutex.withLock {
                                    fileDao.deleteByPaths(
                                        listOf(journal.originalPath, sourceFile.absolutePath).distinct()
                                    )
                                }
                                vaultJournalDao.updateJournal(journal.copy(status = "COMPLETED"))
                                recoveredCount++
                            }
                            else -> {
                                // Both copies remain: retain the encrypted vault item and the
                                // searchable source metadata, but record that shredding did not finish.
                                vaultJournalDao.updateJournal(journal.copy(status = "FAILED_SHRED"))
                                recoveredCount++
                            }
                        }
                    }
                    "DECRYPT" -> {
                        vaultJournalDao.updateJournal(journal.copy(status = "FAILED"))
                        recoveredCount++
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.e(TAG, "Vault journal recovery failed for ${journal.id}; leaving it for review", error)
            }
        }
        val unresolved = vaultJournalDao.getPendingJournals("PENDING")
        check(unresolved.isEmpty()) {
            "Vault journal recovery left ${unresolved.size} unresolved operation(s); refusing to continue"
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
    ): Result<VaultItem> = runCatchingCancellable {
        awaitStartupReady()
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
        val destinationFile = File(vaultDirectory, encryptedFileName).canonicalFile
        require(isInsideDirectory(destinationFile, canonicalVault)) {
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

        var preserveEncryptedCopy = false
        var encryptedEntity: VaultItemEntity? = null
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
            encryptedEntity = entity
            vaultDao.insertVaultItem(entity)

            if (deleteOriginal) {
                // Keep the original metadata until shredding succeeds. The shared mutation lock
                // prevents an in-flight scan snapshot from re-inserting the source after deletion.
                PrimaryStorageMutationLock.mutex.withLock {
                    // Preserve the encrypted copy only after acquiring the mutation lock and
                    // immediately before source removal begins. Cancellation while waiting for
                    // the lock must still roll back the encrypted staging copy.
                    preserveEncryptedCopy = true
                    require(secureShredFile(sourceFile)) {
                        "Encrypted vault copy was retained, but original plaintext could not be securely removed"
                    }
                    // Once the source is gone, metadata removal must finish even if the caller is
                    // cancelled; otherwise an old name/path could remain searchable.
                    withContext(NonCancellable) {
                        fileDao.deleteByPaths(
                            listOf(sourceFile.absolutePath, canonicalSource.absolutePath).distinct()
                        )
                    }
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
        } catch (cancelled: CancellationException) {
            // Before source shredding begins, roll back the encrypted staging copy. Once source
            // removal has started, preserve the encrypted copy and reconcile metadata according
            // to whether the plaintext path still exists.
            withContext(NonCancellable) {
                try {
                    if (!preserveEncryptedCopy) {
                        val destinationRemoved =
                            !destinationFile.exists() || secureShredFile(destinationFile)
                        if (destinationRemoved) {
                            vaultDao.deleteById(fileId)
                        } else {
                            encryptedEntity?.let { vaultDao.insertVaultItem(it) }
                        }
                        if (journalId > 0L) {
                            vaultJournalDao.updateJournal(
                                VaultJournalEntity(
                                    id = journalId,
                                    operationType = "ENCRYPT",
                                    originalPath = sourceFile.absolutePath,
                                    vaultPath = destinationFile.absolutePath,
                                    status = if (destinationRemoved) "FAILED" else "FAILED_SHRED"
                                )
                            )
                        }
                    } else {
                        if (!sourceFile.exists()) {
                            PrimaryStorageMutationLock.mutex.withLock {
                                fileDao.deleteByPaths(
                                    listOf(sourceFile.absolutePath, canonicalSource.absolutePath).distinct()
                                )
                            }
                        }
                        if (journalId > 0L) {
                            vaultJournalDao.updateJournal(
                                VaultJournalEntity(
                                    id = journalId,
                                    operationType = "ENCRYPT",
                                    originalPath = sourceFile.absolutePath,
                                    vaultPath = destinationFile.absolutePath,
                                    status = if (sourceFile.exists()) "FAILED_SHRED" else "COMPLETED"
                                )
                            )
                        }
                    }
                } catch (cleanupError: Throwable) {
                    Log.e(TAG, "Vault encryption cancellation cleanup failed; journal retained for recovery", cleanupError)
                }
            }
            throw cancelled
        } catch (e: Exception) {
            withContext(NonCancellable) {
                try {
                    if (!preserveEncryptedCopy) {
                        val destinationRemoved =
                            !destinationFile.exists() || cryptoManager.secureShredFile(destinationFile)
                        if (destinationRemoved) {
                            vaultDao.deleteById(fileId)
                        } else {
                            encryptedEntity?.let { vaultDao.insertVaultItem(it) }
                        }
                        if (journalId > 0L) {
                            vaultJournalDao.updateJournal(
                                VaultJournalEntity(
                                    id = journalId,
                                    operationType = "ENCRYPT",
                                    originalPath = sourceFile.absolutePath,
                                    vaultPath = destinationFile.absolutePath,
                                    status = if (destinationRemoved) "FAILED" else "FAILED_SHRED"
                                )
                            )
                        }
                    } else {
                        if (!sourceFile.exists()) {
                            PrimaryStorageMutationLock.mutex.withLock {
                                fileDao.deleteByPaths(
                                    listOf(sourceFile.absolutePath, canonicalSource.absolutePath).distinct()
                                )
                            }
                        }
                        if (journalId > 0L) {
                            vaultJournalDao.updateJournal(
                                VaultJournalEntity(
                                    id = journalId,
                                    operationType = "ENCRYPT",
                                    originalPath = sourceFile.absolutePath,
                                    vaultPath = destinationFile.absolutePath,
                                    status = if (sourceFile.exists()) "FAILED_SHRED" else "COMPLETED"
                                )
                            )
                        }
                    }
                } catch (cleanupError: Throwable) {
                    Log.e(TAG, "Vault encryption failure cleanup failed; journal retained for recovery", cleanupError)
                }
            }
            throw e
        }
    }

    suspend fun restoreFileFromVault(
        vaultItemId: String,
        destinationDir: File? = null
    ): Result<File> = runCatchingCancellable {
        awaitStartupReady()
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

        // Plaintext is now successfully restored. If secure shredding fails, retain the encrypted
        // source and DB record rather than creating a false state in which the item disappears.
        if (cryptoManager.secureShredFile(encryptedFile)) {
            vaultDao.deleteById(vaultItemId)
        }

        targetCanonical
    }

    suspend fun exportFileFromVault(
        vaultItemId: String,
        destinationFile: File
    ): Result<File> = runCatchingCancellable {
        awaitStartupReady()
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

    suspend fun deleteVaultItemPermanently(vaultItemId: String): Result<Boolean> = runCatchingCancellable {
        awaitStartupReady()
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

    fun isVaultConfigured(): Boolean { requireStartupReady(); return cryptoManager.isVaultConfigured() }
    fun isDecoyVaultConfigured(): Boolean { requireStartupReady(); return cryptoManager.isDecoyVaultConfigured() }
    fun setupPin(pin: String): Boolean { requireStartupReady(); return cryptoManager.setupVaultPin(pin) }
    fun setupDecoyPin(pin: String): Boolean { requireStartupReady(); return cryptoManager.setupDecoyPin(pin) }
    fun removeDecoyPin(): Boolean { requireStartupReady(); return cryptoManager.removeDecoyPin() }
    fun verifyPin(pin: String): Boolean { requireStartupReady(); return cryptoManager.verifyVaultPin(pin) }
    fun isSameAsVaultPin(pin: String): Boolean { requireStartupReady(); return cryptoManager.isSameAsVaultPin(pin) }
    fun verifyPinWithResult(pin: String): CryptoSecurityManager.VaultAuthResult {
        requireStartupReady()
        return cryptoManager.verifyVaultPinWithResult(pin)
    }
    fun getRemainingLockoutSeconds(): Int { requireStartupReady(); return cryptoManager.getRemainingLockoutSeconds() }
    fun getFailedAttemptsCount(): Int { requireStartupReady(); return cryptoManager.getFailedAttemptsCount() }
    fun changePin(oldPin: String, newPin: String): Boolean { requireStartupReady(); return cryptoManager.changeVaultPin(oldPin, newPin) }
    fun isBiometricEnabled(): Boolean { requireStartupReady(); return cryptoManager.isBiometricEnabled() }
    fun setBiometricEnabled(enabled: Boolean) { requireStartupReady(); cryptoManager.setBiometricEnabled(enabled) }
    fun getAutoLockTimeoutSeconds(): Int { requireStartupReady(); return cryptoManager.getAutoLockTimeoutSeconds() }
    fun setAutoLockTimeoutSeconds(seconds: Int) { requireStartupReady(); cryptoManager.setAutoLockTimeoutSeconds(seconds) }

    private fun requireStartupReady() {
        check(startupIsReady()) { "Vault operation is blocked until secure startup recovery reaches Ready" }
    }


    /**
     * Convert ordinary operation failures to Result without swallowing structured cancellation.
     */
    private suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

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

    private companion object {
        const val TAG = "SecureVaultRepository"
    }
}
