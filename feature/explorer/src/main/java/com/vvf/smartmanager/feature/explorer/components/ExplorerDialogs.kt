package com.vvf.smartmanager.feature.explorer.components

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.vvf.smartmanager.core.common.FormatUtils
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.core.model.FileOperationProgress

@Composable
fun CreateFolderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var folderName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.CreateNewFolder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = { Text(text = "New Folder", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Enter a name for the new folder:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_folder_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(folderName) },
                enabled = folderName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("create_folder_confirm")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("create_folder_cancel")
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.testTag("create_folder_dialog")
    )
}

@Composable
fun CreateFileDialog(
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var fileName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.NoteAdd,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = { Text(text = "New File", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Enter file name (e.g. notes.txt):",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = fileName,
                    onValueChange = { fileName = it },
                    label = { Text("File Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("create_file_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(fileName) },
                enabled = fileName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("create_file_confirm")
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("create_file_cancel")
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.testTag("create_file_dialog")
    )
}

@Composable
fun RenameDialog(
    file: FileItem,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var newName by remember { mutableStateOf(file.name) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.DriveFileRenameOutline,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = { Text(text = "Rename", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text(
                    text = "Enter a new name for '${file.name}':",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("New Name") },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("rename_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(newName) },
                enabled = newName.isNotBlank() && newName != file.name,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("rename_confirm")
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("rename_cancel")
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.testTag("rename_dialog")
    )
}

@Composable
fun DeleteConfirmDialog(
    files: List<FileItem>,
    onDismiss: () -> Unit,
    onConfirm: (Boolean) -> Unit
) {
    val itemCount = files.size
    val itemNames = files.map { it.name }
    var permanentDelete by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(28.dp)
            )
        },
        title = {
            Text(
                text = if (itemCount == 1) "Delete Item" else "Delete $itemCount Items",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                val previewNames = itemNames.take(3).joinToString(", ") + if (itemNames.size > 3) " and ${itemNames.size - 3} more" else ""
                Text(
                    text = "Are you sure you want to delete:\n$previewNames",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Checkbox(
                        checked = permanentDelete,
                        onCheckedChange = { permanentDelete = it },
                        modifier = Modifier.testTag("permanent_delete_checkbox")
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Permanent Delete (bypass Recycle Bin)",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (permanentDelete) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(permanentDelete) },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("delete_confirm_btn")
            ) {
                Text(if (permanentDelete) "Delete Permanently" else "Move to Trash")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("delete_cancel_btn")
            ) {
                Text("Cancel")
            }
        },
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.testTag("delete_confirm_dialog")
    )
}

@Composable
fun FileDetailsDialog(
    file: FileItem,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
        },
        title = { Text(text = "File Details", fontWeight = FontWeight.Bold) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                DetailRow(label = "Name", value = file.name)
                DetailRow(label = "Type", value = if (file.isDirectory) "Folder" else (file.mimeType ?: file.extension.uppercase()))
                DetailRow(label = "Size", value = if (file.isDirectory) "${file.itemCount} items" else "${FormatUtils.formatBytes(file.sizeBytes)} (${file.sizeBytes} bytes)")
                DetailRow(label = "Location", value = file.path)
                DetailRow(label = "Modified", value = FormatUtils.formatDate(file.lastModified))
                val hash = file.md5Hash
                if (hash != null) {
                    DetailRow(label = "SHA-256", value = hash)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.testTag("file_details_close")
            ) {
                Text("Close")
            }
        },
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.testTag("file_details_dialog")
    )
}

@Composable
fun OperationProgressDialog(
    progress: FileOperationProgress
) {
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = "${progress.operation.name} in progress...",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Processing: ${progress.currentFileName}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                LinearProgressIndicator(
                    progress = { progress.progressPercentage },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp)),
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${progress.processedCount} of ${progress.totalCount} completed (${(progress.progressPercentage * 100).toInt()}%)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {},
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.testTag("operation_progress_dialog")
    )
}

@Composable
private fun DetailRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}
