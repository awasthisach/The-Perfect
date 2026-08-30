package com.vvf.smartmanager.feature.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vvf.smartmanager.core.common.FormatUtils
import com.vvf.smartmanager.feature.vault.components.BhagwaOrange
import com.vvf.smartmanager.feature.vault.components.CosmicBlue
import com.vvf.smartmanager.feature.vault.components.SoftGold
import com.vvf.smartmanager.feature.vault.components.VaultAddFileDialog
import com.vvf.smartmanager.feature.vault.components.VaultAuthScreen
import com.vvf.smartmanager.feature.vault.components.VaultCategoryFilterChips
import com.vvf.smartmanager.feature.vault.components.VaultConfirmDeleteDialog
import com.vvf.smartmanager.feature.vault.components.VaultEmptyState
import com.vvf.smartmanager.feature.vault.components.EmeraldGreen
import com.vvf.smartmanager.feature.vault.components.VaultItemCard
import com.vvf.smartmanager.feature.vault.components.VaultItemDetailDialog
import com.vvf.smartmanager.feature.vault.components.VaultItemGridCard
import com.vvf.smartmanager.feature.vault.components.VaultProcessingDialog
import com.vvf.smartmanager.feature.vault.components.VaultSettingsDialog
import com.vvf.smartmanager.feature.vault.components.VaultSetupDecoyDialog
import java.io.File

@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val activity = remember(context) {
        context as? androidx.fragment.app.FragmentActivity
            ?: (context as? android.content.ContextWrapper)?.baseContext as? androidx.fragment.app.FragmentActivity
    }

    val triggerBiometricPrompt = {
        if (!uiState.isBiometricEnabled) {
            viewModel.onBiometricAuthError("Biometric authentication is disabled in settings")
        } else if (activity != null) {
            val executor = androidx.core.content.ContextCompat.getMainExecutor(activity)
            val biometricManager = androidx.biometric.BiometricManager.from(activity)

            val canAuth = biometricManager.canAuthenticate(
                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
            )

            if (canAuth == androidx.biometric.BiometricManager.BIOMETRIC_SUCCESS) {
                val promptInfo = androidx.biometric.BiometricPrompt.PromptInfo.Builder()
                    .setTitle("Unlock VVF Secure Vault")
                    .setSubtitle("Authenticate using biometric credential")
                    .setNegativeButtonText("Use PIN")
                    .setAllowedAuthenticators(
                        androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG or
                                androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_WEAK
                    )
                    .build()

                val prompt = androidx.biometric.BiometricPrompt(
                    activity,
                    executor,
                    object : androidx.biometric.BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: androidx.biometric.BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            viewModel.onBiometricAuthSuccess()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            if (errorCode != androidx.biometric.BiometricPrompt.ERROR_USER_CANCELED &&
                                errorCode != androidx.biometric.BiometricPrompt.ERROR_NEGATIVE_BUTTON
                            ) {
                                viewModel.onBiometricAuthError("Biometric Error: $errString")
                            }
                        }

                        override fun onAuthenticationFailed() {
                            super.onAuthenticationFailed()
                            viewModel.onBiometricAuthError("Biometric recognition failed")
                        }
                    }
                )

                prompt.authenticate(promptInfo)
            } else {
                val errorMsg = when (canAuth) {
                    androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "No biometric hardware on device"
                    androidx.biometric.BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Biometric hardware unavailable"
                    androidx.biometric.BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED -> "No fingerprint/face enrolled in device settings"
                    else -> "Biometric auth unavailable"
                }
                viewModel.onBiometricAuthError(errorMsg)
            }
        } else {
            viewModel.onBiometricAuthError("Activity context unavailable for biometric prompt")
        }
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                viewModel.onAppBackgrounded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    if (!uiState.isUnlocked) {
        VaultAuthScreen(
            state = uiState,
            onDigitClick = { viewModel.onPinDigit(it) },
            onBackspaceClick = { viewModel.onPinBackspace() },
            onClearClick = { viewModel.onPinClear() },
            onBiometricClick = { triggerBiometricPrompt() },
            modifier = modifier
        )
    } else {
        VaultUnlockedContent(
            uiState = uiState,
            viewModel = viewModel,
            snackbarHostState = snackbarHostState,
            modifier = modifier
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultUnlockedContent(
    uiState: VaultUiState,
    viewModel: VaultViewModel,
    snackbarHostState: SnackbarHostState,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("vault_unlocked_root"),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.vvf.smartmanager.core.common.R.drawable.vvf_foundation_logo),
                            contentDescription = "VVF Logo",
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Secure Vault",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                if (uiState.sessionMode == VaultSessionMode.DECOY) {
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Surface(
                                        color = EmeraldGreen.copy(alpha = 0.2f),
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "DECOY",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldGreen,
                                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }
                            Text(
                                text = "${FormatUtils.formatBytes(uiState.totalVaultSizeBytes)} • ${uiState.totalVaultItemCount} Files",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.toggleViewMode() },
                        modifier = Modifier.testTag("vault_toggle_view_mode_btn")
                    ) {
                        Icon(
                            imageVector = if (uiState.viewMode == VaultViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                            contentDescription = "Toggle Grid/List View",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = { viewModel.showSettingsDialog() },
                        modifier = Modifier.testTag("vault_settings_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = "Vault Settings",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    IconButton(
                        onClick = { viewModel.lockVaultNow() },
                        modifier = Modifier.testTag("vault_lock_now_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Vault Now",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { viewModel.showAddFileDialog() },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.testTag("vault_fab_add_file")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Lock & Encrypt File"
                )
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Search Bar
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = { viewModel.onSearchQueryChanged(it) },
                placeholder = { Text("Search encrypted files...") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                },
                trailingIcon = {
                    if (uiState.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Icon(imageVector = Icons.Default.Clear, contentDescription = "Clear search")
                        }
                    }
                },
                shape = RoundedCornerShape(16.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                    focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("vault_search_input")
            )

            // Category Filter Chips
            VaultCategoryFilterChips(
                selectedCategory = uiState.selectedCategory,
                onCategorySelected = { viewModel.onCategorySelected(it) }
            )

            // Content: Empty State or Grid/List
            if (uiState.filteredItems.isEmpty()) {
                VaultEmptyState(
                    onAddFileClick = { viewModel.showAddFileDialog() }
                )
            } else {
                if (uiState.viewMode == VaultViewMode.LIST) {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("vault_items_list"),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.filteredItems, key = { it.id }) { item ->
                            VaultItemCard(
                                item = item,
                                onClick = { viewModel.onItemSelected(item) }
                            )
                        }
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        modifier = Modifier
                            .fillMaxSize()
                            .testTag("vault_items_grid"),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.filteredItems, key = { it.id }) { item ->
                            VaultItemGridCard(
                                item = item,
                                onClick = { viewModel.onItemSelected(item) }
                            )
                        }
                    }
                }
            }
        }

        // ====================================================================
        // Dialogs & Modals
        // ====================================================================

        if (uiState.showAddFileDialog) {
            VaultAddFileDialog(
                onDismiss = { viewModel.dismissAddFileDialog() },
                onEncryptFile = { file, category, notes, deleteOriginal ->
                    viewModel.lockFile(file, category, notes, deleteOriginal)
                }
            )
        }

        if (uiState.showItemDetailDialog && uiState.selectedItem != null) {
            VaultItemDetailDialog(
                item = uiState.selectedItem!!,
                onDismiss = { viewModel.dismissItemDetailDialog() },
                onRestore = { viewModel.restoreItem(it) },
                onExportCopy = { item ->
                    val exportDir = File(context.filesDir, "exported")
                    val destFile = File(exportDir, item.originalName)
                    viewModel.exportItem(item, destFile)
                },
                onDeletePermanently = { viewModel.showConfirmDeleteDialog() }
            )
        }

        if (uiState.showConfirmDeleteDialog && uiState.selectedItem != null) {
            VaultConfirmDeleteDialog(
                item = uiState.selectedItem!!,
                onConfirm = { viewModel.deleteItemPermanently(uiState.selectedItem!!) },
                onDismiss = { viewModel.dismissConfirmDeleteDialog() }
            )
        }

        if (uiState.showSettingsDialog) {
            VaultSettingsDialog(
                isBiometricEnabled = uiState.isBiometricEnabled,
                onToggleBiometric = { viewModel.setBiometricEnabled(it) },
                autoLockSeconds = uiState.autoLockSeconds,
                onAutoLockChange = { viewModel.setAutoLockSeconds(it) },
                onChangePinClick = {
                    viewModel.dismissSettingsDialog()
                    viewModel.startChangePinFlow()
                },
                isDecoyConfigured = uiState.isDecoyConfigured,
                onSetupDecoyClick = {
                    viewModel.dismissSettingsDialog()
                    viewModel.showSetupDecoyDialog()
                },
                onRemoveDecoyClick = { viewModel.removeDecoyPin() },
                onPopulateDecoyDataClick = { viewModel.populateSampleDecoyData(context.filesDir) },
                onDismiss = { viewModel.dismissSettingsDialog() }
            )
        }

        if (uiState.showSetupDecoyDialog) {
            VaultSetupDecoyDialog(
                decoyStep = uiState.decoyPinStep,
                enteredPin = uiState.enteredDecoyPin,
                errorMessage = uiState.errorMessage,
                userMessage = uiState.userMessage,
                onDigitClick = { viewModel.onDecoyPinDigit(it) },
                onBackspaceClick = { viewModel.onDecoyPinBackspace() },
                onDismiss = { viewModel.dismissSetupDecoyDialog() }
            )
        }

        if (uiState.isProcessing) {
            VaultProcessingDialog(message = uiState.processingMessage)
        }
    }
}
