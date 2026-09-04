package com.vvf.smartmanager.feature.vault.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockReset
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.vvf.smartmanager.core.common.FormatUtils
import com.vvf.smartmanager.core.model.VaultItem
import com.vvf.smartmanager.feature.vault.PinSetupStep
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultAddFileDialog(
    onDismiss: () -> Unit,
    onEncryptFile: (File, String, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    var filePath by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Documents") }
    var notes by remember { mutableStateOf("") }
    var deleteOriginal by remember { mutableStateOf(true) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    val categories = listOf("Images", "Videos", "Documents", "Audio", "Archives", "Other")

    // Quick suggestion files from app's internal or storage cache
    val sampleFiles = remember {
        listOf(
            File(context.filesDir, "personal_confidential.txt").apply {
                if (!exists()) writeText("CONFIDENTIAL FINANCIAL & PERSONAL RECORDS\nStored in AES-256 Vault.")
            },
            File(context.filesDir, "medical_report_2026.pdf").apply {
                if (!exists()) writeText("%PDF-1.4 Mock Medical Report for VVF Vault Test")
            },
            File(context.filesDir, "passport_scan.jpg").apply {
                if (!exists()) writeText("MOCK_IMAGE_DATA_ENCRYPTED")
            }
        )
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = BhagwaOrange,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Encrypt & Lock File",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = filePath,
                    onValueChange = { filePath = it },
                    label = { Text("File Path to Encrypt") },
                    placeholder = { Text("/path/to/sensitive_file.pdf") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_add_file_path_input")
                )

                // Quick suggestions
                Text(
                    text = "Or pick a file to encrypt:",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    sampleFiles.forEach { file ->
                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = if (filePath == file.absolutePath) BhagwaOrange.copy(alpha = 0.2f) else MaterialTheme.colorScheme.surfaceContainer
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    filePath = file.absolutePath
                                    selectedCategory = when (file.extension.lowercase()) {
                                        "jpg", "png" -> "Images"
                                        "pdf", "txt" -> "Documents"
                                        else -> "Other"
                                    }
                                }
                        ) {
                            Text(
                                text = file.name,
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 6.dp)
                            )
                        }
                    }
                }

                // Category Dropdown
                ExposedDropdownMenuBox(
                    expanded = categoryDropdownExpanded,
                    onExpandedChange = { categoryDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = selectedCategory,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Vault Category") },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = categoryDropdownExpanded) },
                        modifier = Modifier
                            .menuAnchor()
                            .fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = categoryDropdownExpanded,
                        onDismissRequest = { categoryDropdownExpanded = false }
                    ) {
                        categories.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(cat) },
                                onClick = {
                                    selectedCategory = cat
                                    categoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                // Notes input
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text("Notes (Optional)") },
                    placeholder = { Text("e.g. Tax return or Private ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Shred original checkbox
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable { deleteOriginal = !deleteOriginal }
                ) {
                    Checkbox(
                        checked = deleteOriginal,
                        onCheckedChange = { deleteOriginal = it },
                        colors = CheckboxDefaults.colors(checkedColor = BhagwaOrange)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Securely shred original plaintext file",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val file = File(filePath)
                    if (file.exists()) {
                        onEncryptFile(file, selectedCategory, notes, deleteOriginal)
                    }
                },
                enabled = filePath.isNotBlank() && File(filePath).exists(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BhagwaOrange,
                    contentColor = Color.White
                ),
                modifier = Modifier.testTag("vault_confirm_encrypt_btn")
            ) {
                Text("Lock in Vault")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun VaultItemDetailDialog(
    item: VaultItem,
    onDismiss: () -> Unit,
    onRestore: (VaultItem) -> Unit,
    onExportCopy: (VaultItem) -> Unit,
    onDeletePermanently: (VaultItem) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = null,
                    tint = SoftGold,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = item.originalName,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                DetailRow(label = "Category", value = item.formattedCategory)
                DetailRow(label = "Encrypted Size", value = FormatUtils.formatBytes(item.sizeBytes))
                DetailRow(label = "Locked On", value = FormatUtils.formatDate(item.createdAt))
                DetailRow(label = "MIME Type", value = item.mimeType)
                DetailRow(label = "Original Path", value = item.originalPath)
                val itemNotes = item.notes
                if (!itemNotes.isNullOrBlank()) {
                    DetailRow(label = "Notes", value = itemNotes)
                }

                Spacer(modifier = Modifier.height(4.dp))

                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = CosmicBlue.copy(alpha = 0.08f)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Security,
                            contentDescription = null,
                            tint = EmeraldGreen,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Encrypted with AES-256-GCM. Hardware keystore verified.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(
                    onClick = { onDeletePermanently(item) },
                    colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    modifier = Modifier.testTag("vault_detail_delete_btn")
                ) {
                    Icon(imageVector = Icons.Default.DeleteForever, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Delete")
                }

                Spacer(modifier = Modifier.width(4.dp))

                OutlinedButton(
                    onClick = { onExportCopy(item) },
                    modifier = Modifier.testTag("vault_detail_export_btn")
                ) {
                    Icon(imageVector = Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Export")
                }

                Spacer(modifier = Modifier.width(4.dp))

                Button(
                    onClick = { onRestore(item) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = BhagwaOrange,
                        contentColor = Color.White
                    ),
                    modifier = Modifier.testTag("vault_detail_restore_btn")
                ) {
                    Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Restore")
                }
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VaultSettingsDialog(
    isBiometricEnabled: Boolean,
    onToggleBiometric: (Boolean) -> Unit,
    autoLockSeconds: Int,
    onAutoLockChange: (Int) -> Unit,
    onChangePinClick: () -> Unit,
    isDecoyConfigured: Boolean = false,
    onSetupDecoyClick: () -> Unit = {},
    onRemoveDecoyClick: () -> Unit = {},
    onPopulateDecoyDataClick: () -> Unit = {},
    onDismiss: () -> Unit
) {
    var timeoutDropdownExpanded by remember { mutableStateOf(false) }

    val timeoutOptions = listOf(
        0 to "Immediate (On minimize)",
        30 to "30 Seconds",
        60 to "1 Minute",
        300 to "5 Minutes",
        900 to "15 Minutes"
    )

    val currentTimeoutLabel = timeoutOptions.firstOrNull { it.first == autoLockSeconds }?.second ?: "$autoLockSeconds seconds"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Security,
                    contentDescription = null,
                    tint = BhagwaOrange,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Vault Security Settings",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Change Master PIN
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onChangePinClick)
                        .testTag("vault_settings_change_pin_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(imageVector = Icons.Default.LockReset, contentDescription = null, tint = BhagwaOrange)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Change Master PIN", fontWeight = FontWeight.SemiBold)
                            Text("Update your 4-6 digit decryption PIN", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }

                // Fake/Decoy Vault Configuration
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_settings_decoy_card"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainer
                    ),
                    shape = RoundedCornerShape(10.dp)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(imageVector = Icons.Default.Security, contentDescription = null, tint = EmeraldGreen)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Fake / Decoy Vault (Duress Safety)", fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = if (isDecoyConfigured) "Decoy PIN Active (Anti-Coercion Mode Enabled)" else "Set a secondary PIN to open an innocent Fake Vault",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(10.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            if (isDecoyConfigured) {
                                OutlinedButton(
                                    onClick = onPopulateDecoyDataClick,
                                    modifier = Modifier.padding(end = 8.dp)
                                ) {
                                    Text("Add Dummy Files", style = MaterialTheme.typography.labelMedium)
                                }
                                OutlinedButton(
                                    onClick = onRemoveDecoyClick,
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                                ) {
                                    Text("Disable Decoy", style = MaterialTheme.typography.labelMedium)
                                }
                            } else {
                                Button(
                                    onClick = onSetupDecoyClick,
                                    colors = ButtonDefaults.buttonColors(containerColor = EmeraldGreen)
                                ) {
                                    Text("Set Decoy PIN", style = MaterialTheme.typography.labelMedium)
                                }
                            }
                        }
                    }
                }

                // Biometric Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null, tint = CosmicBlue)
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Biometric Unlock", fontWeight = FontWeight.SemiBold)
                            Text("Unlock using Fingerprint or Face", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Switch(
                        checked = isBiometricEnabled,
                        onCheckedChange = onToggleBiometric,
                        colors = SwitchDefaults.colors(checkedThumbColor = BhagwaOrange),
                        modifier = Modifier.testTag("vault_biometric_switch")
                    )
                }

                // Auto-Lock Timeout
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Default.Timer, contentDescription = null, tint = SoftGold)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Auto-Lock Timeout", fontWeight = FontWeight.SemiBold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))

                    ExposedDropdownMenuBox(
                        expanded = timeoutDropdownExpanded,
                        onExpandedChange = { timeoutDropdownExpanded = it }
                    ) {
                        OutlinedTextField(
                            value = currentTimeoutLabel,
                            onValueChange = {},
                            readOnly = true,
                            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = timeoutDropdownExpanded) },
                            modifier = Modifier
                                .menuAnchor()
                                .fillMaxWidth()
                        )
                        ExposedDropdownMenu(
                            expanded = timeoutDropdownExpanded,
                            onDismissRequest = { timeoutDropdownExpanded = false }
                        ) {
                            timeoutOptions.forEach { (sec, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    onClick = {
                                        onAutoLockChange(sec)
                                        timeoutDropdownExpanded = false
                                    }
                                )
                            }
                        }
                    }
                }

                // Zero-Knowledge Architecture Info
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(
                            text = "🔐 Architecture & Encryption",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = CosmicBlue
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "• Algorithm: AES-256-GCM with authenticated streaming\n• Keystore: Hardware TEE / StrongBox\n• Shredder: US DoD 5220.22-M Zero-Overwrite\n• Decoy Vault: Plausible Deniability Dual-PIN Engine\n• Mode: 100% Zero-Knowledge Offline",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CosmicBlue)
            ) {
                Text("Done")
            }
        }
    )
}

@Composable
fun VaultConfirmDeleteDialog(
    item: VaultItem,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.DeleteForever,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Permanently Shred File?", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Text(
                text = "Are you sure you want to permanently shred and delete \"${item.originalName}\"?\n\nThis will zero-overwrite the encrypted data. This action is irreversible.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error),
                modifier = Modifier.testTag("vault_confirm_shred_btn")
            ) {
                Text("Shred Permanently")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun VaultProcessingDialog(
    message: String
) {
    Dialog(onDismissRequest = {}) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.padding(24.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                CircularProgressIndicator(
                    color = BhagwaOrange,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
fun VaultSetupDecoyDialog(
    decoyStep: PinSetupStep,
    enteredPin: String,
    errorMessage: String?,
    userMessage: String?,
    onDigitClick: (Char) -> Unit,
    onBackspaceClick: () -> Unit,
    onSubmitClick: () -> Unit,
    isPinVerifying: Boolean,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = EmeraldGreen,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Set Fake/Decoy PIN",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CosmicBlue
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = when (decoyStep) {
                        PinSetupStep.ENTER_NEW -> "Enter a secondary 4-6 digit PIN for your Decoy Vault"
                        PinSetupStep.CONFIRM_NEW -> "Confirm your secondary Decoy PIN"
                        else -> "Setup Decoy PIN"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(16.dp))

                if (!errorMessage.isNullOrEmpty()) {
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                } else if (!userMessage.isNullOrEmpty()) {
                    Text(
                        text = userMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = BhagwaOrange
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                PinDotsIndicator(
                    pinLength = enteredPin.length,
                    maxDots = 6
                )

                Spacer(modifier = Modifier.height(20.dp))

                VaultKeypad(
                    onDigitClick = onDigitClick,
                    onBackspaceClick = onBackspaceClick,
                    onBiometricClick = {},
                    isBiometricEnabled = false,
                    isLockedOut = isPinVerifying
                )

                Spacer(modifier = Modifier.height(12.dp))

                Button(
                    onClick = onSubmitClick,
                    enabled = enteredPin.length in 4..6 && !isPinVerifying,
                    modifier = Modifier.testTag("vault_decoy_submit_pin_btn")
                ) {
                    Text(if (isPinVerifying) "Verifying…" else "Continue")
                }

                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
fun VaultChangePinDialog(
    step: PinSetupStep,
    enteredPin: String,
    errorMessage: String?,
    isPinVerifying: Boolean,
    onDigitClick: (Char) -> Unit,
    onBackspaceClick: () -> Unit,
    onSubmitClick: () -> Unit,
    onDismiss: () -> Unit
) {
    val instruction = when (step) {
        PinSetupStep.ENTER_OLD_FOR_CHANGE -> "Enter your current Master PIN"
        PinSetupStep.ENTER_NEW -> "Enter a new 4-6 digit Master PIN"
        PinSetupStep.CONFIRM_NEW -> "Confirm your new Master PIN"
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            modifier = Modifier.padding(16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.LockReset,
                        contentDescription = null,
                        tint = BhagwaOrange,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Change Master PIN",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CosmicBlue
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = instruction,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (!errorMessage.isNullOrEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = errorMessage,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                PinDotsIndicator(pinLength = enteredPin.length, maxDots = 6)
                Spacer(modifier = Modifier.height(20.dp))
                VaultKeypad(
                    onDigitClick = onDigitClick,
                    onBackspaceClick = onBackspaceClick,
                    onBiometricClick = {},
                    isBiometricEnabled = false,
                    isLockedOut = isPinVerifying
                )
                Spacer(modifier = Modifier.height(12.dp))
                Button(
                    onClick = onSubmitClick,
                    enabled = enteredPin.length in 4..6 && !isPinVerifying,
                    modifier = Modifier.testTag("vault_change_pin_submit_btn")
                ) {
                    Text(if (isPinVerifying) "Verifying…" else "Continue")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    }
}
