package com.vvf.smartmanager.feature.vault

import com.vvf.smartmanager.core.model.VaultItem

enum class PinSetupStep {
    ENTER_NEW,
    CONFIRM_NEW,
    ENTER_OLD_FOR_CHANGE
}

enum class VaultSessionMode {
    LOCKED,
    REAL,
    DECOY
}

enum class VaultViewMode {
    GRID,
    LIST
}

data class VaultUiState(
    val isConfigured: Boolean = false,
    val isUnlocked: Boolean = false,
    val sessionMode: VaultSessionMode = VaultSessionMode.LOCKED,
    val isDecoyConfigured: Boolean = false,
    val isSettingUpPin: Boolean = false,
    val pinSetupStep: PinSetupStep = PinSetupStep.ENTER_NEW,
    val enteredPin: String = "",
    val firstEnteredPin: String = "",
    val oldPinForChange: String = "",
    val isChangingPin: Boolean = false,
    val isSettingUpDecoyPin: Boolean = false,
    val decoyPinStep: PinSetupStep = PinSetupStep.ENTER_NEW,
    val enteredDecoyPin: String = "",
    val firstEnteredDecoyPin: String = "",
    val errorMessage: String? = null,
    val userMessage: String? = null,
    val failedAttempts: Int = 0,
    val isLockedOut: Boolean = false,
    val lockoutRemainingSeconds: Int = 0,
    val isPinVerifying: Boolean = false,
    val rawVaultItems: List<VaultItem> = emptyList(),
    val filteredItems: List<VaultItem> = emptyList(),
    val selectedCategory: String = "ALL",
    val searchQuery: String = "",
    val viewMode: VaultViewMode = VaultViewMode.LIST,
    val isLoading: Boolean = false,
    val isProcessing: Boolean = false,
    val processingMessage: String = "",
    val selectedItem: VaultItem? = null,
    val showSettingsDialog: Boolean = false,
    val showAddFileDialog: Boolean = false,
    val showItemDetailDialog: Boolean = false,
    val showChangePinDialog: Boolean = false,
    val showSetupDecoyDialog: Boolean = false,
    val showConfirmDeleteDialog: Boolean = false,
    val isBiometricSupported: Boolean = true,
    val isBiometricEnabled: Boolean = false,
    val autoLockSeconds: Int = 60,
    val totalVaultSizeBytes: Long = 0L,
    val totalVaultItemCount: Int = 0
)
