package com.vvf.smartmanager.feature.vault.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.vvf.smartmanager.core.common.FormatUtils
import com.vvf.smartmanager.core.model.VaultItem
import com.vvf.smartmanager.feature.vault.PinSetupStep
import java.io.File

@Composable
fun VaultAddFileDialog(
    onDismiss: () -> Unit,
    onEncryptFile: (File, String, String, Boolean) -> Unit
) {
    val context = LocalContext.current
    var selectedFile by remember { mutableStateOf<File?>(null) }
    var category by remember { mutableStateOf("Other") }
    var notes by remember { mutableStateOf("") }
    var deleteOriginal by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            selectedFile = runCatching {
                val name = "vault-import-${System.currentTimeMillis()}"
                File.createTempFile(name, ".bin", context.cacheDir).also { target ->
                    context.contentResolver.openInputStream(uri)?.use { input -> target.outputStream().use(input::copyTo) }
                }
            }.getOrNull()
        }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lock a file in the vault") },
        text = {
            Column {
                Button(onClick = { picker.launch(arrayOf("*/*")) }, modifier = Modifier.fillMaxWidth()) {
                    Text(selectedFile?.name ?: "Choose a file")
                }
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(category, { category = it }, label = { Text("Category") }, singleLine = true)
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, singleLine = true)
                Row {
                    Checkbox(deleteOriginal, { deleteOriginal = it })
                    Text("Delete original after encryption", modifier = Modifier.padding(top = 12.dp))
                }
            }
        },
        confirmButton = {
            TextButton(enabled = selectedFile != null, onClick = { selectedFile?.let { onEncryptFile(it, category.ifBlank { "Other" }, notes, deleteOriginal) } }) { Text("Encrypt") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun VaultItemDetailDialog(item: VaultItem, onDismiss: () -> Unit, onRestore: (VaultItem) -> Unit, onExportCopy: (VaultItem) -> Unit, onDeletePermanently: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(item.originalName) }, text = {
        Column {
            Text("Category: ${item.formattedCategory}")
            Text("Size: ${FormatUtils.formatBytes(item.sizeBytes)}")
            if (!item.notes.isNullOrBlank()) Text("Notes: ${item.notes}")
            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Text("This file is encrypted in the secure vault.")
        }
    }, confirmButton = { TextButton(onClick = { onRestore(item) }) { Text("Restore") } }, dismissButton = {
        Row { TextButton(onClick = { onExportCopy(item) }) { Text("Export copy") }; TextButton(onClick = onDeletePermanently) { Text("Delete") } }
    })
}

@Composable
fun VaultConfirmDeleteDialog(item: VaultItem, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Delete permanently?") }, text = { Text("Securely shred ${item.originalName}? This cannot be undone.") }, confirmButton = { TextButton(onClick = onConfirm) { Text("Delete permanently") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun VaultSettingsDialog(isBiometricEnabled: Boolean, onToggleBiometric: (Boolean) -> Unit, autoLockSeconds: Int, onAutoLockChange: (Int) -> Unit, onChangePinClick: () -> Unit, isDecoyConfigured: Boolean, onSetupDecoyClick: () -> Unit, onRemoveDecoyClick: () -> Unit, onPopulateDecoyDataClick: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Vault settings") }, text = {
        Column {
            Row(Modifier.fillMaxWidth()) { Text("Biometric unlock", Modifier.weight(1f).padding(top = 12.dp)); Switch(isBiometricEnabled, onToggleBiometric) }
            Text("Auto-lock: ${autoLockSeconds}s")
            Slider(value = autoLockSeconds.toFloat(), onValueChange = { onAutoLockChange(it.toInt()) }, valueRange = 15f..300f)
            TextButton(onClick = onChangePinClick) { Text("Change master PIN") }
            TextButton(onClick = if (isDecoyConfigured) onRemoveDecoyClick else onSetupDecoyClick) { Text(if (isDecoyConfigured) "Remove fake vault" else "Set up fake vault") }
            if (isDecoyConfigured) TextButton(onClick = onPopulateDecoyDataClick) { Text("Add sample fake-vault files") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
fun VaultChangePinDialog(step: PinSetupStep, enteredPin: String, errorMessage: String?, isPinVerifying: Boolean, onDigitClick: (Char) -> Unit, onBackspaceClick: () -> Unit, onSubmitClick: () -> Unit, onDismiss: () -> Unit) {
    PinEntryDialog(title = "Change master PIN", step = step, pin = enteredPin, error = errorMessage, verifying = isPinVerifying, onDigitClick = onDigitClick, onBackspace = onBackspaceClick, onSubmit = onSubmitClick, onDismiss = onDismiss)
}

@Composable
fun VaultSetupDecoyDialog(decoyStep: PinSetupStep, enteredPin: String, errorMessage: String?, userMessage: String?, onDigitClick: (Char) -> Unit, onBackspaceClick: () -> Unit, onSubmitClick: () -> Unit, isPinVerifying: Boolean, onDismiss: () -> Unit) {
    PinEntryDialog(title = "Set up fake vault", step = decoyStep, pin = enteredPin, error = errorMessage ?: userMessage, verifying = isPinVerifying, onDigitClick = onDigitClick, onBackspace = onBackspaceClick, onSubmit = onSubmitClick, onDismiss = onDismiss)
}

@Composable
private fun PinEntryDialog(title: String, step: PinSetupStep, pin: String, error: String?, verifying: Boolean, onDigitClick: (Char) -> Unit, onBackspace: () -> Unit, onSubmit: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column {
            Text(when (step) { PinSetupStep.ENTER_OLD_FOR_CHANGE -> "Enter current PIN"; PinSetupStep.CONFIRM_NEW -> "Confirm new PIN"; else -> "Enter PIN" })
            Text("•".repeat(pin.length).ifEmpty { "Enter 4–6 digits" }, style = MaterialTheme.typography.headlineSmall)
            VaultKeypad(onDigitClick = onDigitClick, onBackspaceClick = onBackspace, onBiometricClick = {}, isBiometricEnabled = false, isLockedOut = verifying)
            if (!error.isNullOrBlank()) Text(error, color = MaterialTheme.colorScheme.error)
        }
    }, confirmButton = { TextButton(enabled = !verifying, onClick = onSubmit) { Text("Continue") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun VaultProcessingDialog(message: String) {
    AlertDialog(onDismissRequest = {}, title = { Text("Please wait") }, text = { Text(message) }, confirmButton = {})
}
