package com.vvf.smartmanager

import android.content.Intent
import android.net.Uri
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.vvf.smartmanager.core.common.FormatUtils
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OfflineFilesScreen(app: VVFApplication) {
    var pinnedFiles by remember { mutableStateOf<List<FileMetadataEntity>>(emptyList()) }
    var usedBytes by remember { mutableStateOf(0L) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    val refresh: suspend () -> Unit = {
        pinnedFiles = app.database.fileDao().getOfflinePinnedDriveFiles()
        usedBytes = app.database.fileDao().getOfflinePinnedBytes()
    }
    LaunchedEffect(Unit) { refresh() }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Offline", fontWeight = FontWeight.Bold) })
        },
        modifier = Modifier.fillMaxSize()
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text("Pinned Drive files", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "${FormatUtils.formatBytes(usedBytes)} of 200 MiB used · ${pinnedFiles.size} of 80 files",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            "Copies stay in app-private storage. When a quota is reached, the oldest pinned copies are evicted first.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            if (!statusMessage.isNullOrBlank()) {
                item { Text(statusMessage.orEmpty(), color = MaterialTheme.colorScheme.error) }
            }
            if (pinnedFiles.isEmpty()) {
                item {
                    Text(
                        "No files are pinned. Open Search and use the download icon on a Drive result to keep a private offline copy.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(pinnedFiles, key = { it.path }) { entry ->
                Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(entry.name, fontWeight = FontWeight.SemiBold)
                        Text(
                            "${FormatUtils.formatBytes(entry.offlineBytes)} · pinned ${entry.offlinePinnedAt ?: 0L}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(
                                onClick = {
                                    val local = entry.offlineLocalPath?.let { File(it) }
                                    if (local == null || !local.isFile) {
                                        statusMessage = "Offline copy missing for ${entry.name}; remove and pin it again."
                                    } else {
                                        runCatching {
                                            val uri = FileProvider.getUriForFile(
                                                app,
                                                "${BuildConfig.APPLICATION_ID}.fileprovider",
                                                local
                                            )
                                            val mime = MimeTypeMap.getSingleton()
                                                .getMimeTypeFromExtension(local.extension.lowercase())
                                                ?: entry.mimeType
                                            app.startActivity(
                                                Intent(Intent.ACTION_VIEW)
                                                    .setDataAndType(uri, mime)
                                                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
                                            )
                                        }.onFailure {
                                            statusMessage = "No compatible viewer is available for ${entry.name}."
                                        }
                                    }
                                }
                            ) { Text("Open") }
                            OutlinedButton(
                                onClick = {
                                    scope.launch {
                                        DriveOfflineManager(app).toggle(entry.path).fold(
                                            onSuccess = {
                                                statusMessage = null
                                                refresh()
                                                Toast.makeText(app, "Offline pin removed", Toast.LENGTH_SHORT).show()
                                            },
                                            onFailure = { error ->
                                                statusMessage = error.message ?: "Could not remove offline pin"
                                            }
                                        )
                                    }
                                }
                            ) {
                                Text("Unpin")
                            }
                        }
                    }
                }
            }
        }
    }
}
