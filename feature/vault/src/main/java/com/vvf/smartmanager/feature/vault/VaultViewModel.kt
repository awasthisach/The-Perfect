package com.vvf.smartmanager.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vvf.smartmanager.core.domain.DeleteVaultItemUseCase
import com.vvf.smartmanager.core.domain.ExportVaultItemUseCase
import com.vvf.smartmanager.core.domain.GetVaultItemsUseCase
import com.vvf.smartmanager.core.domain.LockFileInVaultUseCase
import com.vvf.smartmanager.core.domain.RestoreVaultItemUseCase
import com.vvf.smartmanager.core.domain.VaultAuthUseCase
import com.vvf.smartmanager.core.model.VaultItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class VaultViewModel(
    private val getVaultItemsUseCase: GetVaultItemsUseCase,
    private val lockFileInVaultUseCase: LockFileInVaultUseCase,
    private val restoreVaultItemUseCase: RestoreVaultItemUseCase,
    private val exportVaultItemUseCase: ExportVaultItemUseCase,
    private val deleteVaultItemUseCase: DeleteVaultItemUseCase,
    private val vaultAuthUseCase: VaultAuthUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    private var autoLockJob: Job? = null
    private var lockoutTimerJob: Job? = null
    private var metricsItemsJob: Job? = null
    private var metricsSizeJob: Job? = null

    init {
        checkVaultStatus()
        observeVaultMetrics(isDecoy = false)
    }

    fun checkVaultStatus() {
        val configured = vaultAuthUseCase.isVaultConfigured()
        val decoyConfigured = vaultAuthUseCase.isDecoyConfigured()
        val bioEnabled = vaultAuthUseCase.isBiometricEnabled()
        val autoLockSec = vaultAuthUseCase.getAutoLockTimeoutSeconds()
        val lockoutSec = vaultAuthUseCase.getRemainingLockoutSeconds()
        val failedAttempts = vaultAuthUseCase.getFailedAttemptsCount()

        _uiState.update {
            it.copy(
                isConfigured = configured,
                isDecoyConfigured = decoyConfigured,
                isBiometricEnabled = bioEnabled,
                autoLockSeconds = autoLockSec,
                isSettingUpPin = !configured,
                pinSetupStep = PinSetupStep.ENTER_NEW,
                enteredPin = "",
                firstEnteredPin = "",
                failedAttempts = failedAttempts,
                errorMessage = null
            )
        }

        if (lockoutSec > 0) {
            startLockoutTimer(lockoutSec)
        }
    }

    private fun observeVaultMetrics(isDecoy: Boolean = false) {
        metricsItemsJob?.cancel()
        metricsSizeJob?.cancel()

        metricsItemsJob = viewModelScope.launch {
            getVaultItemsUseCase(isDecoy).catch { emit(emptyList()) }.collectLatest { items ->
                _uiState.update { state ->
                    val filtered = filterItems(items, state.selectedCategory, state.searchQuery)
                    state.copy(
                        rawVaultItems = items,
                        filteredItems = filtered,
                        totalVaultItemCount = items.size
                    )
                }
            }
        }

        metricsSizeJob = viewModelScope.launch {
            getVaultItemsUseCase.getTotalSizeBytes(isDecoy).catch { emit(0L) }.collectLatest { size ->
                _uiState.update { it.copy(totalVaultSizeBytes = size ?: 0L) }
            }
        }
    }

    // ========================================================================
    // PIN & Keypad Management
    // ========================================================================

    fun onPinDigit(digit: Char) {
        if (!digit.isDigit()) return
        val current = _uiState.value.enteredPin
        if (current.length >= 6) return
        val newPin = current + digit
        _uiState.update { it.copy(enteredPin = newPin, errorMessage = null) }

        val state = _uiState.value
        if (state.isConfigured && !state.isSettingUpPin && !state.isChangingPin) {
            // Unlocking mode: check if PIN verifies (works for 4 or 6 digit saved PIN)
            if (vaultAuthUseCase.verifyPin(newPin)) {
                evaluatePinSubmission(newPin)
            } else if (newPin.length == 6) {
                // Fail only after 6 digits if it didn't match
                evaluatePinSubmission(newPin)
            }
        } else if (state.pinSetupStep == PinSetupStep.CONFIRM_NEW) {
            // Confirm step: evaluate when length matches firstEnteredPin
            if (newPin.length == state.firstEnteredPin.length) {
                evaluatePinSubmission(newPin)
            }
        } else {
            // Setup/Change PIN mode: evaluate at 4 or 6 digits
            if (newPin.length == 4 || newPin.length == 6) {
                evaluatePinSubmission(newPin)
            }
        }
    }

    fun onPinBackspace() {
        val current = _uiState.value.enteredPin
        if (current.isNotEmpty()) {
            _uiState.update { it.copy(enteredPin = current.dropLast(1), errorMessage = null) }
        }
    }

    fun onPinClear() {
        _uiState.update { it.copy(enteredPin = "", errorMessage = null) }
    }

    private fun evaluatePinSubmission(pin: String) {
        val state = _uiState.value
        if (!state.isConfigured || state.isSettingUpPin) {
            handlePinSetup(pin)
        } else if (state.isChangingPin) {
            handleChangePinStep(pin)
        } else {
            handleUnlockAttempt(pin)
        }
    }

    private fun handlePinSetup(pin: String) {
        val state = _uiState.value
        when (state.pinSetupStep) {
            PinSetupStep.ENTER_NEW -> {
                if (pin.length !in 4..6 || !pin.all { it.isDigit() }) {
                    _uiState.update {
                        it.copy(
                            enteredPin = "",
                            errorMessage = "PIN must be 4 to 6 numeric digits"
                        )
                    }
                    return
                }
                _uiState.update {
                    it.copy(
                        firstEnteredPin = pin,
                        enteredPin = "",
                        pinSetupStep = PinSetupStep.CONFIRM_NEW,
                        userMessage = "Confirm your Master PIN"
                    )
                }
            }
            PinSetupStep.CONFIRM_NEW -> {
                if (pin == state.firstEnteredPin) {
                    val success = vaultAuthUseCase.setupPin(pin)
                    if (success) {
                        _uiState.update {
                            it.copy(
                                isConfigured = true,
                                isUnlocked = true,
                                isSettingUpPin = false,
                                enteredPin = "",
                                firstEnteredPin = "",
                                userMessage = "Vault initialized and secured!"
                            )
                        }
                        scheduleAutoLock()
                    } else {
                        _uiState.update {
                            it.copy(
                                enteredPin = "",
                                errorMessage = "Failed to store secure PIN"
                            )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            enteredPin = "",
                            firstEnteredPin = "",
                            pinSetupStep = PinSetupStep.ENTER_NEW,
                            errorMessage = "PINs do not match. Try again."
                        )
                    }
                }
            }
            else -> {}
        }
    }

    private fun handleUnlockAttempt(pin: String) {
        val state = _uiState.value
        if (state.isLockedOut) return

        when (val result = vaultAuthUseCase.verifyPinWithResult(pin)) {
            is com.vvf.smartmanager.core.security.CryptoSecurityManager.VaultAuthResult.SUCCESS_REAL -> {
                _uiState.update {
                    it.copy(
                        isUnlocked = true,
                        sessionMode = VaultSessionMode.REAL,
                        enteredPin = "",
                        failedAttempts = 0,
                        errorMessage = null
                    )
                }
                observeVaultMetrics(isDecoy = false)
                scheduleAutoLock()
            }
            is com.vvf.smartmanager.core.security.CryptoSecurityManager.VaultAuthResult.SUCCESS_DECOY -> {
                _uiState.update {
                    it.copy(
                        isUnlocked = true,
                        sessionMode = VaultSessionMode.DECOY,
                        enteredPin = "",
                        failedAttempts = 0,
                        errorMessage = null
                    )
                }
                observeVaultMetrics(isDecoy = true)
                scheduleAutoLock()
            }
            is com.vvf.smartmanager.core.security.CryptoSecurityManager.VaultAuthResult.LOCKED_OUT -> {
                startLockoutTimer(result.remainingSeconds)
            }
            is com.vvf.smartmanager.core.security.CryptoSecurityManager.VaultAuthResult.INVALID_FORMAT -> {
                _uiState.update {
                    it.copy(
                        enteredPin = "",
                        errorMessage = "PIN must be 4 to 6 numeric digits"
                    )
                }
            }
            is com.vvf.smartmanager.core.security.CryptoSecurityManager.VaultAuthResult.INVALID_PIN -> {
                if (result.lockoutSeconds > 0) {
                    startLockoutTimer(result.lockoutSeconds)
                } else {
                    _uiState.update {
                        it.copy(
                            enteredPin = "",
                            failedAttempts = result.failedAttempts,
                            errorMessage = "Incorrect PIN (${result.failedAttempts} failed attempt${if (result.failedAttempts > 1) "s" else ""})"
                        )
                    }
                }
            }
        }
    }

    fun onBiometricAuthSuccess() {
        val state = _uiState.value
        if (state.isConfigured && !state.isLockedOut && state.isBiometricEnabled) {
            _uiState.update {
                it.copy(
                    isUnlocked = true,
                    sessionMode = VaultSessionMode.REAL,
                    enteredPin = "",
                    failedAttempts = 0,
                    errorMessage = null
                )
            }
            observeVaultMetrics(isDecoy = false)
            scheduleAutoLock()
        } else if (!state.isBiometricEnabled) {
            _uiState.update { it.copy(errorMessage = "Biometric authentication is disabled in settings") }
        }
    }

    fun onBiometricAuthError(message: String) {
        _uiState.update { it.copy(errorMessage = message) }
    }

    private fun startLockoutTimer(seconds: Int) {
        lockoutTimerJob?.cancel()
        _uiState.update {
            it.copy(
                isLockedOut = true,
                lockoutRemainingSeconds = seconds,
                enteredPin = "",
                errorMessage = "Too many attempts. Vault locked for $seconds seconds."
            )
        }
        lockoutTimerJob = viewModelScope.launch {
            var remaining = seconds
            while (remaining > 0) {
                delay(1000)
                remaining--
                _uiState.update { it.copy(lockoutRemainingSeconds = remaining) }
            }
            _uiState.update {
                it.copy(
                    isLockedOut = false,
                    failedAttempts = 0,
                    errorMessage = null
                )
            }
        }
    }

    // ========================================================================
    // Change PIN Flow
    // ========================================================================

    fun startChangePinFlow() {
        _uiState.update {
            it.copy(
                isChangingPin = true,
                showChangePinDialog = true,
                pinSetupStep = PinSetupStep.ENTER_OLD_FOR_CHANGE,
                enteredPin = "",
                firstEnteredPin = "",
                oldPinForChange = "",
                errorMessage = null
            )
        }
    }

    private fun handleChangePinStep(pin: String) {
        val state = _uiState.value
        when (state.pinSetupStep) {
            PinSetupStep.ENTER_OLD_FOR_CHANGE -> {
                if (vaultAuthUseCase.verifyPin(pin)) {
                    _uiState.update {
                        it.copy(
                            oldPinForChange = pin,
                            enteredPin = "",
                            pinSetupStep = PinSetupStep.ENTER_NEW,
                            errorMessage = null,
                            userMessage = "Enter new 4-6 digit PIN"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            enteredPin = "",
                            errorMessage = "Current PIN is incorrect"
                        )
                    }
                }
            }
            PinSetupStep.ENTER_NEW -> {
                _uiState.update {
                    it.copy(
                        firstEnteredPin = pin,
                        enteredPin = "",
                        pinSetupStep = PinSetupStep.CONFIRM_NEW,
                        errorMessage = null,
                        userMessage = "Confirm your new PIN"
                    )
                }
            }
            PinSetupStep.CONFIRM_NEW -> {
                if (pin == state.firstEnteredPin) {
                    val success = vaultAuthUseCase.changePin(state.oldPinForChange, pin)
                    if (success) {
                        _uiState.update {
                            it.copy(
                                isChangingPin = false,
                                showChangePinDialog = false,
                                enteredPin = "",
                                firstEnteredPin = "",
                                oldPinForChange = "",
                                userMessage = "Master PIN changed successfully!"
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                enteredPin = "",
                                errorMessage = "Failed to update PIN"
                            )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            enteredPin = "",
                            firstEnteredPin = "",
                            pinSetupStep = PinSetupStep.ENTER_NEW,
                            errorMessage = "PINs do not match. Try again."
                        )
                    }
                }
            }
        }
    }

    fun dismissChangePinDialog() {
        _uiState.update {
            it.copy(
                isChangingPin = false,
                showChangePinDialog = false,
                enteredPin = "",
                firstEnteredPin = "",
                oldPinForChange = "",
                errorMessage = null
            )
        }
    }

    // ========================================================================
    // Auto-Lock & Session Timeout
    // ========================================================================

    fun scheduleAutoLock() {
        autoLockJob?.cancel()
        val timeoutSeconds = _uiState.value.autoLockSeconds
        if (timeoutSeconds > 0) {
            autoLockJob = viewModelScope.launch {
                delay(timeoutSeconds * 1000L)
                lockVaultNow()
            }
        }
    }

    fun userActivityOccurred() {
        if (_uiState.value.isUnlocked) {
            scheduleAutoLock()
        }
    }

    fun lockVaultNow() {
        autoLockJob?.cancel()
        _uiState.update {
            it.copy(
                isUnlocked = false,
                sessionMode = VaultSessionMode.LOCKED,
                enteredPin = "",
                selectedItem = null,
                showSettingsDialog = false,
                showAddFileDialog = false,
                showItemDetailDialog = false,
                showChangePinDialog = false,
                showSetupDecoyDialog = false,
                showConfirmDeleteDialog = false,
                userMessage = "Vault locked"
            )
        }
    }

    fun onAppBackgrounded() {
        if (_uiState.value.autoLockSeconds <= 30) {
            lockVaultNow()
        }
    }

    // ========================================================================
    // Vault Operations (Encrypt, Restore, Export, Delete)
    // ========================================================================

    fun lockFile(
        sourceFile: File,
        category: String = "Other",
        notes: String = "",
        deleteOriginal: Boolean = false
    ) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingMessage = "Encrypting with AES-256-GCM..."
                )
            }
            val isDecoy = (_uiState.value.sessionMode == VaultSessionMode.DECOY)
            val result = withContext(Dispatchers.IO) {
                lockFileInVaultUseCase(sourceFile, category, notes, deleteOriginal, isDecoy)
            }
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    showAddFileDialog = false,
                    userMessage = if (result.isSuccess) {
                        if (isDecoy) "Decoy file locked in Fake Vault" else "File securely locked in Vault"
                    } else {
                        "Encryption failed: ${result.exceptionOrNull()?.message}"
                    }
                )
            }
        }
    }

    fun restoreItem(item: VaultItem, destinationDir: File? = null) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingMessage = "Decrypting & restoring file..."
                )
            }
            val result = withContext(Dispatchers.IO) {
                restoreVaultItemUseCase(item.id, destinationDir)
            }
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    showItemDetailDialog = false,
                    selectedItem = null,
                    userMessage = if (result.isSuccess) "Restored: ${result.getOrNull()?.name}" else "Restore failed: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun exportItem(item: VaultItem, destinationFile: File) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingMessage = "Exporting decrypted copy..."
                )
            }
            val result = withContext(Dispatchers.IO) {
                exportVaultItemUseCase(item.id, destinationFile)
            }
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    showItemDetailDialog = false,
                    userMessage = if (result.isSuccess) "Exported copy to: ${destinationFile.name}" else "Export failed: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    fun deleteItemPermanently(item: VaultItem) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingMessage = "Securely shredding encrypted file..."
                )
            }
            val result = withContext(Dispatchers.IO) {
                deleteVaultItemUseCase(item.id)
            }
            _uiState.update {
                it.copy(
                    isProcessing = false,
                    showItemDetailDialog = false,
                    showConfirmDeleteDialog = false,
                    selectedItem = null,
                    userMessage = if (result.isSuccess) "Permanently shredded" else "Deletion failed: ${result.exceptionOrNull()?.message}"
                )
            }
        }
    }

    // ========================================================================
    // Category & Search Filters
    // ========================================================================

    fun onCategorySelected(category: String) {
        _uiState.update { state ->
            val filtered = filterItems(state.rawVaultItems, category, state.searchQuery)
            state.copy(selectedCategory = category, filteredItems = filtered)
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { state ->
            val filtered = filterItems(state.rawVaultItems, state.selectedCategory, query)
            state.copy(searchQuery = query, filteredItems = filtered)
        }
    }

    fun toggleViewMode() {
        _uiState.update {
            it.copy(viewMode = if (it.viewMode == VaultViewMode.LIST) VaultViewMode.GRID else VaultViewMode.LIST)
        }
    }

    private fun filterItems(items: List<VaultItem>, category: String, query: String): List<VaultItem> {
        return items.filter { item ->
            val matchesCategory = if (category.equals("ALL", ignoreCase = true)) {
                true
            } else {
                item.category.equals(category, ignoreCase = true)
            }
            val matchesQuery = if (query.isBlank()) {
                true
            } else {
                item.originalName.contains(query, ignoreCase = true) ||
                        (item.notes?.contains(query, ignoreCase = true) == true)
            }
            matchesCategory && matchesQuery
        }
    }

    // ========================================================================
    // UI Dialog Controls
    // ========================================================================

    fun onItemSelected(item: VaultItem) {
        _uiState.update { it.copy(selectedItem = item, showItemDetailDialog = true) }
    }

    fun dismissItemDetailDialog() {
        _uiState.update { it.copy(showItemDetailDialog = false, selectedItem = null) }
    }

    fun showAddFileDialog() {
        _uiState.update { it.copy(showAddFileDialog = true) }
    }

    fun dismissAddFileDialog() {
        _uiState.update { it.copy(showAddFileDialog = false) }
    }

    fun showSettingsDialog() {
        _uiState.update { it.copy(showSettingsDialog = true) }
    }

    fun dismissSettingsDialog() {
        _uiState.update { it.copy(showSettingsDialog = false) }
    }

    fun showConfirmDeleteDialog() {
        _uiState.update { it.copy(showConfirmDeleteDialog = true) }
    }

    fun dismissConfirmDeleteDialog() {
        _uiState.update { it.copy(showConfirmDeleteDialog = false) }
    }

    fun clearUserMessage() {
        _uiState.update { it.copy(userMessage = null) }
    }

    fun setBiometricEnabled(enabled: Boolean) {
        vaultAuthUseCase.setBiometricEnabled(enabled)
        _uiState.update { it.copy(isBiometricEnabled = enabled) }
    }

    fun setAutoLockSeconds(seconds: Int) {
        vaultAuthUseCase.setAutoLockTimeoutSeconds(seconds)
        _uiState.update { it.copy(autoLockSeconds = seconds) }
        scheduleAutoLock()
    }

    // ========================================================================
    // Decoy (Fake Vault) PIN Management
    // ========================================================================

    fun showSetupDecoyDialog() {
        _uiState.update {
            it.copy(
                showSetupDecoyDialog = true,
                isSettingUpDecoyPin = true,
                decoyPinStep = PinSetupStep.ENTER_NEW,
                enteredDecoyPin = "",
                firstEnteredDecoyPin = "",
                errorMessage = null
            )
        }
    }

    fun dismissSetupDecoyDialog() {
        _uiState.update {
            it.copy(
                showSetupDecoyDialog = false,
                isSettingUpDecoyPin = false,
                enteredDecoyPin = "",
                firstEnteredDecoyPin = "",
                errorMessage = null
            )
        }
    }

    fun onDecoyPinDigit(digit: Char) {
        val current = _uiState.value.enteredDecoyPin
        if (current.length >= 6) return
        val newPin = current + digit
        _uiState.update { it.copy(enteredDecoyPin = newPin, errorMessage = null) }

        val state = _uiState.value
        if (state.decoyPinStep == PinSetupStep.CONFIRM_NEW) {
            if (newPin.length == state.firstEnteredDecoyPin.length) {
                evaluateDecoyPinSubmission(newPin)
            }
        } else {
            if (newPin.length == 4 || newPin.length == 6) {
                evaluateDecoyPinSubmission(newPin)
            }
        }
    }

    fun onDecoyPinBackspace() {
        val current = _uiState.value.enteredDecoyPin
        if (current.isNotEmpty()) {
            _uiState.update { it.copy(enteredDecoyPin = current.dropLast(1), errorMessage = null) }
        }
    }

    private fun evaluateDecoyPinSubmission(pin: String) {
        val state = _uiState.value
        when (state.decoyPinStep) {
            PinSetupStep.ENTER_NEW -> {
                if (vaultAuthUseCase.verifyPin(pin)) {
                    _uiState.update {
                        it.copy(
                            enteredDecoyPin = "",
                            errorMessage = "Decoy PIN cannot be equal to Real Master PIN"
                        )
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            firstEnteredDecoyPin = pin,
                            enteredDecoyPin = "",
                            decoyPinStep = PinSetupStep.CONFIRM_NEW,
                            userMessage = "Confirm your Fake/Decoy PIN"
                        )
                    }
                }
            }
            PinSetupStep.CONFIRM_NEW -> {
                if (pin == state.firstEnteredDecoyPin) {
                    val success = vaultAuthUseCase.setupDecoyPin(pin)
                    if (success) {
                        _uiState.update {
                            it.copy(
                                isDecoyConfigured = true,
                                showSetupDecoyDialog = false,
                                isSettingUpDecoyPin = false,
                                enteredDecoyPin = "",
                                firstEnteredDecoyPin = "",
                                userMessage = "Fake/Decoy Vault PIN configured successfully!"
                            )
                        }
                    } else {
                        _uiState.update {
                            it.copy(
                                enteredDecoyPin = "",
                                errorMessage = "Failed to setup Decoy PIN"
                            )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            enteredDecoyPin = "",
                            firstEnteredDecoyPin = "",
                            decoyPinStep = PinSetupStep.ENTER_NEW,
                            errorMessage = "Decoy PINs do not match. Try again."
                        )
                    }
                }
            }
            else -> {}
        }
    }

    fun removeDecoyPin() {
        vaultAuthUseCase.removeDecoyPin()
        _uiState.update {
            it.copy(
                isDecoyConfigured = false,
                userMessage = "Decoy Vault disabled"
            )
        }
    }

    fun populateSampleDecoyData(contextFilesDir: File) {
        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    isProcessing = true,
                    processingMessage = "Populating sample innocent files into Decoy Vault..."
                )
            }

            val samples = listOf(
                File(contextFilesDir, "vacation_itinerary_2026.pdf") to ("Documents" to "%PDF-1.4 Sample Innocent Travel Plan"),
                File(contextFilesDir, "family_recipes_grandma.txt") to ("Documents" to "Grandma's Favorite Apple Pie & Chai Recipe"),
                File(contextFilesDir, "sample_landscape.jpg") to ("Images" to "MOCK_LANDSCAPE_PHOTO_CONTENT")
            )

            withContext(Dispatchers.IO) {
                samples.forEach { (file, meta) ->
                    if (!file.exists()) file.writeText(meta.second)
                    lockFileInVaultUseCase(
                        sourceFile = file,
                        category = meta.first,
                        notes = "Sample decoy file",
                        deleteOriginal = false,
                        isDecoy = true
                    )
                }
            }

            _uiState.update {
                it.copy(
                    isProcessing = false,
                    userMessage = "Sample decoy files added to Fake Vault"
                )
            }
        }
    }

    companion object {
        fun provideFactory(
            getVaultItemsUseCase: GetVaultItemsUseCase,
            lockFileInVaultUseCase: LockFileInVaultUseCase,
            restoreVaultItemUseCase: RestoreVaultItemUseCase,
            exportVaultItemUseCase: ExportVaultItemUseCase,
            deleteVaultItemUseCase: DeleteVaultItemUseCase,
            vaultAuthUseCase: VaultAuthUseCase
        ): ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return VaultViewModel(
                    getVaultItemsUseCase = getVaultItemsUseCase,
                    lockFileInVaultUseCase = lockFileInVaultUseCase,
                    restoreVaultItemUseCase = restoreVaultItemUseCase,
                    exportVaultItemUseCase = exportVaultItemUseCase,
                    deleteVaultItemUseCase = deleteVaultItemUseCase,
                    vaultAuthUseCase = vaultAuthUseCase
                ) as T
            }
        }
    }
}
