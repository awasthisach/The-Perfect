package com.vvf.smartmanager.core.domain

import com.vvf.smartmanager.core.data.repository.SecureVaultRepository
import com.vvf.smartmanager.core.model.VaultItem
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import kotlinx.coroutines.flow.Flow
import java.io.File

/**
 * Domain use case for retrieving encrypted files in the Vault.
 */
class GetVaultItemsUseCase(
    private val repository: SecureVaultRepository
) {
    operator fun invoke(isDecoy: Boolean = false): Flow<List<VaultItem>> =
        repository.getAllVaultItems(isDecoy)

    fun getByCategory(category: String, isDecoy: Boolean = false): Flow<List<VaultItem>> =
        repository.getVaultItemsByCategory(category, isDecoy)

    fun getTotalSizeBytes(isDecoy: Boolean = false): Flow<Long?> =
        repository.getVaultTotalSizeBytes(isDecoy)

    fun getItemCount(isDecoy: Boolean = false): Flow<Int> =
        repository.getVaultItemCount(isDecoy)
}

/**
 * Domain use case for encrypting and locking a plaintext file into the Vault.
 */
class LockFileInVaultUseCase(
    private val repository: SecureVaultRepository
) {
    suspend operator fun invoke(
        sourceFile: File,
        category: String = "Other",
        notes: String = "",
        deleteOriginal: Boolean = true,
        isDecoy: Boolean = false
    ): Result<VaultItem> = repository.lockFileInVault(sourceFile, category, notes, deleteOriginal, isDecoy)
}

/**
 * Domain use case for restoring/decrypting a vault file back to storage.
 */
class RestoreVaultItemUseCase(
    private val repository: SecureVaultRepository
) {
    suspend operator fun invoke(
        vaultItemId: String,
        destinationDir: File? = null
    ): Result<File> = repository.restoreFileFromVault(vaultItemId, destinationDir)
}

/**
 * Domain use case for exporting a decrypted copy of a vault item.
 */
class ExportVaultItemUseCase(
    private val repository: SecureVaultRepository
) {
    suspend operator fun invoke(
        vaultItemId: String,
        destinationFile: File
    ): Result<File> = repository.exportFileFromVault(vaultItemId, destinationFile)
}

/**
 * Domain use case for permanently shredding and deleting a vault file.
 */
class DeleteVaultItemUseCase(
    private val repository: SecureVaultRepository
) {
    suspend operator fun invoke(vaultItemId: String): Result<Boolean> =
        repository.deleteVaultItemPermanently(vaultItemId)
}

/**
 * Domain use case for vault authentication, PIN setup, biometrics, auto-lock, and Decoy Vault.
 */
class VaultAuthUseCase(
    private val repository: SecureVaultRepository
) {
    fun isVaultConfigured(): Boolean = repository.isVaultConfigured()

    fun isDecoyConfigured(): Boolean = repository.isDecoyVaultConfigured()

    fun setupPin(pin: String): Boolean = repository.setupPin(pin)

    fun setupDecoyPin(pin: String): Boolean = repository.setupDecoyPin(pin)

    fun removeDecoyPin(): Boolean = repository.removeDecoyPin()

    fun verifyPin(pin: String): Boolean = repository.verifyPin(pin)

    fun isSameAsVaultPin(pin: String): Boolean = repository.isSameAsVaultPin(pin)

    fun verifyPinWithResult(pin: String): CryptoSecurityManager.VaultAuthResult =
        repository.verifyPinWithResult(pin)

    fun getRemainingLockoutSeconds(): Int = repository.getRemainingLockoutSeconds()

    fun getFailedAttemptsCount(): Int = repository.getFailedAttemptsCount()

    fun changePin(oldPin: String, newPin: String): Boolean = repository.changePin(oldPin, newPin)

    fun isBiometricEnabled(): Boolean = repository.isBiometricEnabled()

    fun setBiometricEnabled(enabled: Boolean) = repository.setBiometricEnabled(enabled)

    fun getAutoLockTimeoutSeconds(): Int = repository.getAutoLockTimeoutSeconds()

    fun setAutoLockTimeoutSeconds(seconds: Int) = repository.setAutoLockTimeoutSeconds(seconds)
}
