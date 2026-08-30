package com.vvf.smartmanager.feature.cloud

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vvf.smartmanager.core.domain.CloudSyncUseCase
import com.vvf.smartmanager.core.model.CloudAccount
import com.vvf.smartmanager.core.model.CloudBackupInfo
import com.vvf.smartmanager.core.model.CloudProviderType
import com.vvf.smartmanager.core.model.CloudSyncStatus
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class CloudViewModel(
    private val cloudSyncUseCase: CloudSyncUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(CloudUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadAllAccounts()
        observeSyncState()
    }

    private fun observeSyncState() {
        viewModelScope.launch {
            cloudSyncUseCase.syncState.collectLatest { status ->
                _uiState.update { it.copy(syncStatus = status) }
            }
        }
        viewModelScope.launch {
            cloudSyncUseCase.syncQueue.collectLatest { queue ->
                _uiState.update { it.copy(syncQueue = queue) }
            }
        }
    }

    fun selectProvider(providerType: CloudProviderType) {
        _uiState.update { it.copy(selectedProvider = providerType) }
        loadRemoteFiles(providerType)
    }

    fun loadAllAccounts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val accountMap = mutableMapOf<CloudProviderType, CloudAccount>()
            CloudProviderType.values().forEach { provider ->
                accountMap[provider] = cloudSyncUseCase.getAccount(provider)
            }
            _uiState.update {
                it.copy(
                    accounts = accountMap,
                    isLoading = false
                )
            }
            loadRemoteFiles(_uiState.value.selectedProvider)
        }
    }

    fun toggleVaultBackup(enabled: Boolean) {
        _uiState.update { it.copy(includeVaultInBackup = enabled) }
    }

    fun connectProvider(providerType: CloudProviderType) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Authenticating with ${providerType.displayName}...") }
            val result = cloudSyncUseCase.authenticateProvider(providerType)
            if (result.isSuccess) {
                val updated = cloudSyncUseCase.getAccount(providerType)
                _uiState.update { state ->
                    val updatedMap = state.accounts.toMutableMap()
                    updatedMap[providerType] = updated
                    state.copy(
                        accounts = updatedMap,
                        isLoading = false,
                        statusMessage = "Successfully connected to ${providerType.displayName}"
                    )
                }
                loadRemoteFiles(providerType)
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Failed to connect: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    fun loadRemoteFiles(providerType: CloudProviderType) {
        viewModelScope.launch {
            val result = cloudSyncUseCase.listRemoteFiles(providerType)
            if (result.isSuccess) {
                _uiState.update { it.copy(remoteFiles = result.getOrDefault(emptyList())) }
            } else {
                _uiState.update { it.copy(remoteFiles = emptyList()) }
            }
        }
    }

    fun triggerCloudBackup() {
        viewModelScope.launch {
            val provider = _uiState.value.selectedProvider
            _uiState.update { it.copy(isBackingUp = true, statusMessage = "Creating encrypted backup snapshot...") }
            val result = cloudSyncUseCase.createCloudBackup(
                providerType = provider,
                includeVault = _uiState.value.includeVaultInBackup,
                includeDatabase = true
            )
            if (result.isSuccess) {
                val backup = result.getOrThrow()
                _uiState.update { state ->
                    state.copy(
                        isBackingUp = false,
                        backupHistory = listOf(backup) + state.backupHistory,
                        statusMessage = "Backup created successfully on ${provider.displayName}"
                    )
                }
                loadAllAccounts()
            } else {
                _uiState.update {
                    it.copy(
                        isBackingUp = false,
                        statusMessage = "Backup failed: ${result.exceptionOrNull()?.message}"
                    )
                }
            }
        }
    }

    fun restoreBackup(backupInfo: CloudBackupInfo) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, statusMessage = "Restoring ${backupInfo.backupName}...") }
            val result = cloudSyncUseCase.restoreCloudBackup(backupInfo)
            if (result.isSuccess) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Restored ${backupInfo.backupName} successfully."
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        statusMessage = "Restore failed."
                    )
                }
            }
        }
    }

    fun clearStatusMessage() {
        _uiState.update { it.copy(statusMessage = null) }
    }

    companion object {
        fun provideFactory(cloudSyncUseCase: CloudSyncUseCase): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CloudViewModel(cloudSyncUseCase) as T
                }
            }
    }
}
