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
    onEncryptFile: (File, String, String, Boolean) -> Unit,
    onActivity: (() -> Unit)? = null
) {
    var filePath by remember { mutableStateOf("") }
    var selectedCategory by remember { mutableStateOf("Documents") }
    var notes by remember { mutableStateOf("") }
    var deleteOriginal by remember { mutableStateOf(true) }
    var categoryDropdownExpanded by remember { mutableStateOf(false) }

    val categories = listOf("Images", "Videos", "Documents", "Audio", "Archives", "Other")

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
                Text(
                    text = "Enter the absolute path of a file on this device. " +
                        "A system document picker will replace path entry in a follow-up.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = filePath,
                    onValueChange = {
                        onActivity?.invoke()
                        filePath = it
                    },
                    label = { Text("File Path to Encrypt") },
                    placeholder = { Text("/storage/emulated/0/Download/document.pdf") },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("vault_add_file_path_input")
                )

                ExposedDropdownMenuBox(
                    expanded = categoryDropdownExpanded,
                    onExpandedChange = {
                        onActivity?.invoke()
                        categoryDropdownExpanded = it
                    }
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
                                    onActivity?.invoke()
                                    selectedCategory = cat
                                    categoryDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                OutlinedTextField(
                    value = notes,
                    onValueChange = {
                        onActivity?.invoke()
                        notes = it
                    },
                    label = { Text("Notes (Optional)") },
                    placeholder = { Text("e.g. Tax return or Private ID") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.clickable {
                        onActivity?.invoke()
                        deleteOriginal = !deleteOriginal
                    }
                ) {
                    Checkbox(
                        checked = deleteOriginal,
                        onCheckedChange = {
                            onActivity?.invoke()
                            deleteOriginal = it
                        },
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
                    onActivity?.invoke()
                    val file = File(filePath)
                    if (file.exists() && file.isFile) {
                        onEncryptFile(file, selectedCategory, notes, deleteOriginal)
                    }
                },
                enabled = filePath.isNotBlank() && File(filePath).isFile,
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

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(imageVector = Icons.Default.Fingerprint, contentDescription = null, tint = BhagwaOrange)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Biometric unlock", fontWeight = FontWeight.SemiBold)
                        Text("Use fingerprint / face after PIN setup", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(
                        checked = isBiometricEnabled,
                        onCheckedChange = onToggleBiometric,
                        colors = SwitchDefaults.colors(checkedTrackColor = BhagwaOrange)
                    )
                }

                ExposedDropdownMenuBox(
                    expanded = timeoutDropdownExpanded,
                    onExpandedChange = { timeoutDropdownExpanded = it }
                ) {
                    OutlinedTextField(
                        value = currentTimeoutLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text("Auto-lock timeout") },
                        leadingIcon = { Icon(Icons.Default.Timer, contentDescription = null) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = timeoutDropdownExpanded) },
                        modifier = Modifier.menuAnchor().fillMaxWidth()
                    )
                    ExposedDropdownMenu(
                        expanded = timeoutDropdownExpanded,
                        onDismissRequest = { timeoutDropdownExpanded = false }
                    ) {
                        timeoutOptions.forEach { (seconds, label) ->
                            DropdownMenuItem(
                                text = { Text(label) },
                                onClick = {
                                    onAutoLockChange(seconds)
                                    timeoutDropdownExpanded = false
                                }
                            )
                        }
                    }
                }

                if (!isDecoyConfigured) {
                    TextButton(onClick = onSetupDecoyClick) { Text("Set up decoy PIN") }
                } else {
                    TextButton(onClick = onRemoveDecoyClick) { Text("Remove decoy PIN") }
                    TextButton(onClick = onPopulateDecoyDataClick) { Text("Populate sample decoy data") }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        }
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyMedium)
    }
}

// Shared vault palette tokens used across dialogs
private val BhagwaOrange = Color(0xFFF47B20)
private val CosmicBlue = Color(0xFF102B52)
private val EmeraldGreen = Color(0xFF2E7D32)
private val SoftGold = Color(0xFFD4A017)
