package com.vvf.smartmanager.feature.vault.components

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
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

private fun copySelectedFile(context: Context, uri: Uri): File? {
    val name = context.contentResolver.query(uri, arrayOf("_display_name"), null, null, null)?.use {
        if (it.moveToFirst()) it.getString(0) else null
    } ?: "selected_file"
    return try {
        val file = File(context.cacheDir, "vault_import_${System.currentTimeMillis()}_$name")
        context.contentResolver.openInputStream(uri)?.use { input ->
            file.outputStream().use { output -> input.copyTo(output) }
        }
        file
    } catch (_: Exception) { null }
}

@Composable
fun VaultAddFileDialog(onDismiss: () -> Unit, onEncryptFile: (File, String, String, Boolean) -> Unit) {
    val context = LocalContext.current
    var category by remember { mutableStateOf("Other") }
    var notes by remember { mutableStateOf("") }
    var deleteOriginal by remember { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let { copySelectedFile(context, it)?.let { file -> onEncryptFile(file, category, notes, deleteOriginal) } }
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Lock & Encrypt File") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Select a file to encrypt with AES-256-GCM.")
                OutlinedTextField(notes, { notes = it }, label = { Text("Notes (optional)") }, modifier = Modifier.fillMaxWidth())
                Row { Checkbox(deleteOriginal, { deleteOriginal = it }); Text("Delete original after encryption", modifier = Modifier.padding(top = 12.dp)) }
            }
        },
        confirmButton = { Button(onClick = { picker.launch(arrayOf("*/*")) }) { Text("Choose file") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun VaultItemDetailDialog(item: VaultItem, onDismiss: () -> Unit, onRestore: (VaultItem) -> Unit, onExportCopy: (VaultItem) -> Unit, onDeletePermanently: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(item.originalName) }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text("Category: ${item.formattedCategory}")
            Text("Size: ${FormatUtils.formatBytes(item.sizeBytes)}")
            if (!item.notes.isNullOrBlank()) Text("Notes: ${item.notes}")
        }
    }, confirmButton = { TextButton(onClick = { onRestore(item) }) { Text("Restore") } },
        dismissButton = { Row { TextButton(onClick = { onExportCopy(item) }) { Text("Export copy") }; TextButton(onClick = onDeletePermanently) { Text("Delete") } } })
}

@Composable
fun VaultConfirmDeleteDialog(item: VaultItem, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Delete permanently?") }, text = { Text("${item.originalName} will be permanently removed. This cannot be undone.") }, confirmButton = { Button(onClick = onConfirm) { Text("Delete permanently") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}

@Composable
fun VaultProcessingDialog(message: String) {
    AlertDialog(onDismissRequest = {}, title = { Text("Securing file") }, text = { Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) { CircularProgressIndicator(); Text(message) } }, confirmButton = {})
}

@Composable
fun VaultSettingsDialog(isBiometricEnabled: Boolean, onToggleBiometric: (Boolean) -> Unit, autoLockSeconds: Int, onAutoLockChange: (Int) -> Unit, onChangePinClick: () -> Unit, isDecoyConfigured: Boolean, onSetupDecoyClick: () -> Unit, onRemoveDecoyClick: () -> Unit, onPopulateDecoyDataClick: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Vault Settings") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row { Text("Biometric unlock", modifier = Modifier.weight(1f)); Switch(isBiometricEnabled, onToggleBiometric) }
            Text("Auto-lock: ${autoLockSeconds}s")
            Slider(value = autoLockSeconds.toFloat(), onValueChange = { onAutoLockChange(it.toInt()) }, valueRange = 15f..300f)
            TextButton(onClick = onChangePinClick) { Text("Change master PIN") }
            TextButton(onClick = if (isDecoyConfigured) onRemoveDecoyClick else onSetupDecoyClick) { Text(if (isDecoyConfigured) "Remove decoy PIN" else "Set up decoy PIN") }
            if (isDecoyConfigured) TextButton(onClick = onPopulateDecoyDataClick) { Text("Populate decoy data") }
        }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } })
}

@Composable
fun VaultChangePinDialog(step: PinSetupStep, enteredPin: String, errorMessage: String?, isPinVerifying: Boolean, onDigitClick: (Char) -> Unit, onBackspaceClick: () -> Unit, onSubmitClick: () -> Unit, onDismiss: () -> Unit) {
    PinEntryDialog(title = "Change PIN", pin = enteredPin, message = errorMessage, verifying = isPinVerifying, onDigit = onDigitClick, onBackspace = onBackspaceClick, onSubmit = onSubmitClick, onDismiss = onDismiss)
}

@Composable
fun VaultSetupDecoyDialog(decoyStep: PinSetupStep, enteredDecoyPin: String, errorMessage: String?, userMessage: String?, onDigitClick: (Char) -> Unit, onBackspaceClick: () -> Unit, onSubmitClick: () -> Unit, isPinVerifying: Boolean, onDismiss: () -> Unit) {
    PinEntryDialog(title = "Set up decoy PIN", pin = enteredDecoyPin, message = errorMessage ?: userMessage, verifying = isPinVerifying, onDigit = onDigitClick, onBackspace = onBackspaceClick, onSubmit = onSubmitClick, onDismiss = onDismiss)
}

@Composable
private fun PinEntryDialog(title: String, pin: String, message: String?, verifying: Boolean, onDigit: (Char) -> Unit, onBackspace: () -> Unit, onSubmit: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(onDismissRequest = onDismiss, title = { Text(title) }, text = {
        Column { OutlinedTextField(value = pin, onValueChange = { value -> value.takeLast(6).filter(Char::isDigit).forEach(onDigit) }, label = { Text("4–6 digit PIN") }, singleLine = true); if (message != null) Text(message, color = MaterialTheme.colorScheme.error) }
    }, confirmButton = { Button(onClick = onSubmit, enabled = !verifying) { Text("Continue") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } })
}
