package com.vvf.smartmanager.feature.vault

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.PointerEventType
import com.vvf.smartmanager.core.common.FormatUtils
import com.vvf.smartmanager.feature.vault.components.EmeraldGreen
import com.vvf.smartmanager.feature.vault.components.VaultAddFileDialog
import com.vvf.smartmanager.feature.vault.components.VaultCategoryFilterChips
import com.vvf.smartmanager.feature.vault.components.VaultChangePinDialog
import com.vvf.smartmanager.feature.vault.components.VaultConfirmDeleteDialog
import com.vvf.smartmanager.feature.vault.components.VaultEmptyState
import com.vvf.smartmanager.feature.vault.components.VaultItemCard
import com.vvf.smartmanager.feature.vault.components.VaultItemDetailDialog
import com.vvf.smartmanager.feature.vault.components.VaultItemGridCard
import com.vvf.smartmanager.feature.vault.components.VaultProcessingDialog
import com.vvf.smartmanager.feature.vault.components.VaultSettingsDialog
import com.vvf.smartmanager.feature.vault.components.VaultBiometricEnrollmentDialog
import com.vvf.smartmanager.feature.vault.components.VaultSetupDecoyDialog
import android.os.SystemClock
import java.io.File
import kotlinx.coroutines.launch

/**
 * Throttled activity reporter so continuous scroll / rapid typing does not spam the ViewModel.
 * Resets the vault auto-lock timer at most once every [throttleMs] milliseconds.
 */
@Composable
private fun rememberThrottledActivityReporter(
    viewModel: VaultViewModel,
    throttleMs: Long = 1500L
): () -> Unit {
    var lastReportMs by remember { mutableLongStateOf(0L) }
    return remember(viewModel) {
        {
            val now = SystemClock.elapsedRealtime()
            if (now - lastReportMs >= throttleMs) {
                lastReportMs = now
                viewModel.userActivityOccurred()
            }
        }
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
    val reportActivity = rememberThrottledActivityReporter(viewModel)
    val coroutineScope = rememberCoroutineScope()
    var showBiometricEnrollmentDialog by remember { mutableStateOf(false) }
    var biometricEnrollmentVerifying by remember { mutableStateOf(false) }
    var biometricEnrollmentError by remember { mutableStateOf<String?>(null) }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("vault_unlocked_root")
            // Capture any pointer activity (tap, scroll, drag, long-press) on the unlocked vault UI
            // and reset the auto-lock timer. Uses Initial pass so we observe events without consuming them.
            .pointerInput(reportActivity) {
                awaitPointerEventScope {
                    while (true) {
                        val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                        if (event.type == PointerEventType.Scroll || event.changes.any { it.pressed }) {
                            reportActivity()
                        }
                    }
                }
            },
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(
                                id = com.vvf.smartmanager.core.common.R.drawable.vvf_foundation_logo
                            ),
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
                        onClick = {
                            reportActivity()
                            viewModel.toggleViewMode()
                        },
                        modifier = Modifier.testTag("vault_toggle_view_mode_btn")
                    ) {
                        Icon(
                            imageVector = if (uiState.viewMode == VaultViewMode.LIST) Icons.Default.GridView else Icons.Default.ViewList,
                            contentDescription = "Toggle Grid/List View",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                    IconButton(
                        onClick = {
                            reportActivity()
                            viewModel.showSettingsDialog()
                        },
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
                onClick = {
                    reportActivity()
                    viewModel.showAddFileDialog()
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier.testTag("vault_fab_add_file")
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Lock & Encrypt File")
            }
        },
        snackbarHost = { SnackbarHost(hostState = snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            OutlinedTextField(
                value = uiState.searchQuery,
                onValueChange = {
                    reportActivity()
                    viewModel.onSearchQueryChanged(it)
                },
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
                        IconButton(onClick = {
                            reportActivity()
                            viewModel.onSearchQueryChanged("")
                        }) {
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
            VaultCategoryFilterChips(
                selectedCategory = uiState.selectedCategory,
                onCategorySelected = {
                    reportActivity()
                    viewModel.onCategorySelected(it)
                }
            )
            if (uiState.filteredItems.isEmpty()) {
                VaultEmptyState(onAddFileClick = {
                    reportActivity()
                    viewModel.showAddFileDialog()
                })
            } else if (uiState.viewMode == VaultViewMode.LIST) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().testTag("vault_items_list"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.filteredItems, key = { it.id }) { item ->
                        VaultItemCard(item = item, onClick = {
                            reportActivity()
                            viewModel.onItemSelected(item)
                        })
                    }
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxSize().testTag("vault_items_grid"),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 80.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(uiState.filteredItems, key = { it.id }) { item ->
                        VaultItemGridCard(item = item, onClick = {
                            reportActivity()
                            viewModel.onItemSelected(item)
                        })
                    }
                }
            }
        }

        if (uiState.showAddFileDialog) {
            VaultAddFileDialog(
                onActivity = reportActivity,
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
                onRestore = {
                    reportActivity()
                    viewModel.restoreItem(it)
                },
                onExportCopy = { item ->
                    reportActivity()
                    val exportDir = File(context.filesDir, "exported")
                    val destFile = File(exportDir, item.originalName)
                    viewModel.exportItem(item, destFile)
                },
                onDeletePermanently = {
                    reportActivity()
                    viewModel.showConfirmDeleteDialog()
                }
            )
        }

        if (uiState.showConfirmDeleteDialog && uiState.selectedItem != null) {
            VaultConfirmDeleteDialog(
                item = uiState.selectedItem!!,
                onConfirm = {
                    reportActivity()
                    viewModel.deleteItemPermanently(uiState.selectedItem!!)
                },
                onDismiss = { viewModel.dismissConfirmDeleteDialog() }
            )
        }

        if (uiState.showSettingsDialog) {
            VaultSettingsDialog(
                isBiometricEnabled = uiState.isBiometricEnabled,
                onToggleBiometric = { enabled ->
                    reportActivity()
                    if (enabled) {
                        biometricEnrollmentError = null
                        showBiometricEnrollmentDialog = true
                    } else {
                        viewModel.setBiometricEnabled(false)
                    }
                },
                autoLockSeconds = uiState.autoLockSeconds,
                onAutoLockChange = {
                    reportActivity()
                    viewModel.setAutoLockSeconds(it)
                },
                onChangePinClick = {
                    reportActivity()
                    viewModel.dismissSettingsDialog()
                    viewModel.startChangePinFlow()
                },
                isDecoyConfigured = uiState.isDecoyConfigured,
                onSetupDecoyClick = {
                    reportActivity()
                    viewModel.dismissSettingsDialog()
                    viewModel.showSetupDecoyDialog()
                },
                onRemoveDecoyClick = {
                    reportActivity()
                    viewModel.removeDecoyPin()
                },
                onPopulateDecoyDataClick = {
                    reportActivity()
                    viewModel.populateSampleDecoyData(context.filesDir)
                },
                onDismiss = { viewModel.dismissSettingsDialog() }
            )
        }

        if (showBiometricEnrollmentDialog) {
            VaultBiometricEnrollmentDialog(
                isVerifying = biometricEnrollmentVerifying,
                errorMessage = biometricEnrollmentError,
                onConfirm = { pin ->
                    coroutineScope.launch {
                        biometricEnrollmentVerifying = true
                        val enabled = viewModel.enableBiometricAfterPinVerification(pin)
                        biometricEnrollmentVerifying = false
                        if (enabled) {
                            showBiometricEnrollmentDialog = false
                            biometricEnrollmentError = null
                        } else {
                            biometricEnrollmentError = "Master PIN verification failed. Biometric unlock remains disabled."
                        }
                    }
                },
                onDismiss = {
                    if (!biometricEnrollmentVerifying) {
                        showBiometricEnrollmentDialog = false
                        biometricEnrollmentError = null
                    }
                }
            )
        }

        if (uiState.showChangePinDialog) {
            VaultChangePinDialog(
                step = uiState.pinSetupStep,
                enteredPin = uiState.enteredPin,
                errorMessage = uiState.errorMessage,
                isPinVerifying = uiState.isPinVerifying,
                onDigitClick = {
                    reportActivity()
                    viewModel.onPinDigit(it)
                },
                onBackspaceClick = {
                    reportActivity()
                    viewModel.onPinBackspace()
                },
                onSubmitClick = {
                    reportActivity()
                    viewModel.submitPin()
                },
                onDismiss = { viewModel.dismissChangePinDialog() }
            )
        }

        if (uiState.showSetupDecoyDialog) {
            VaultSetupDecoyDialog(
                decoyStep = uiState.decoyPinStep,
                enteredPin = uiState.enteredDecoyPin,
                errorMessage = uiState.errorMessage,
                userMessage = uiState.userMessage,
                onDigitClick = {
                    reportActivity()
                    viewModel.onDecoyPinDigit(it)
                },
                onBackspaceClick = {
                    reportActivity()
                    viewModel.onDecoyPinBackspace()
                },
                onSubmitClick = {
                    reportActivity()
                    viewModel.submitDecoyPin()
                },
                isPinVerifying = uiState.isPinVerifying,
                onDismiss = { viewModel.dismissSetupDecoyDialog() }
            )
        }

        if (uiState.isProcessing) {
            VaultProcessingDialog(message = uiState.processingMessage)
        }
    }
}
