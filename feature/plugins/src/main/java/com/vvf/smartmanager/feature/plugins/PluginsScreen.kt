package com.vvf.smartmanager.feature.plugins

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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Memory
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vvf.smartmanager.core.model.FileCategory
import com.vvf.smartmanager.core.model.FileItem
import com.vvf.smartmanager.feature.plugins.components.OcrResultDialog
import com.vvf.smartmanager.feature.plugins.components.OcrScanProgressDialog

private val BhagwaOrange = Color(0xFFF47B20)
private val CosmicBlue = Color(0xFF102B52)
private val EmeraldGreen = Color(0xFF3FA34D)
private val SkyCyan = Color(0xFF5BC0EB)
private val SoftGold = Color(0xFFD4A95A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PluginsScreen(
    viewModel: PluginsViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.snackbarMessage) {
        uiState.snackbarMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.dismissSnackbar()
        }
    }

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("plugins_screen_root"),
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(CosmicBlue)
                    .padding(horizontal = 20.dp, vertical = 16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.vvf.smartmanager.core.common.R.drawable.vvf_foundation_logo),
                        contentDescription = "Vishva Vijayaa Foundation Logo",
                        modifier = Modifier.size(34.dp)
                    )
                    Column {
                        Text(
                            text = "Plugin Manager",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "॥ विजया ददाति विजयम् ॥",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = SoftGold
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = "On-demand downloadable AI, OCR, and Cloud storage drivers",
                    style = MaterialTheme.typography.bodySmall,
                    color = SoftGold
                )
            }
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Architecture Isolation Card
            item {
                PluginArchitectureNoticeCard()
            }

            // Phase 8: OCR Engine Plugin Card
            item {
                OcrPluginCard(
                    isEnabled = uiState.ocrPluginEnabled,
                    isModelDownloaded = uiState.ocrModelDownloaded,
                    isDownloading = uiState.isDownloadingOcrModel,
                    downloadProgress = uiState.ocrDownloadProgress,
                    onToggleEnabled = { viewModel.toggleOcrPlugin(it) },
                    onDownloadModel = { viewModel.downloadOcrModel() },
                    onTestScan = {
                        // Create a test sample image file item
                        val sampleFile = FileItem(
                            path = "/storage/emulated/0/Documents/sample_invoice.pdf",
                            name = "sample_invoice.pdf",
                            sizeBytes = 245000L,
                            mimeType = "application/pdf",
                            isDirectory = false,
                            lastModified = System.currentTimeMillis()
                        )
                        viewModel.startScan(sampleFile)
                    }
                )
            }

            // Phase 9: AI Semantic Search Plugin (Upcoming Preview)
            item {
                SemanticSearchPluginCard()
            }

            // Phase 11: Multi-Cloud Drivers Plugin (Upcoming Preview)
            item {
                CloudDriversPluginCard()
            }
        }
    }

    // Active OCR Scanning Progress Modal
    if (uiState.isScanning && uiState.activeScanningFile != null) {
        OcrScanProgressDialog(
            fileItem = uiState.activeScanningFile!!,
            progress = uiState.scanProgress,
            onCancel = { viewModel.cancelScan() }
        )
    }

    // Extracted OCR Result Modal
    if (uiState.ocrResult != null && uiState.activeScanningFile != null) {
        OcrResultDialog(
            fileItem = uiState.activeScanningFile!!,
            ocrResult = uiState.ocrResult!!,
            isTxtSaved = uiState.isTxtSaved,
            isIndexed = uiState.isIndexed,
            onSaveTxt = { viewModel.saveAsTxt() },
            onIndexText = { viewModel.indexExtractedText() },
            onDismiss = { viewModel.dismissResultDialog() }
        )
    }
}

@Composable
private fun PluginArchitectureNoticeCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = CosmicBlue.copy(alpha = 0.05f)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = BhagwaOrange.copy(alpha = 0.15f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = null,
                        tint = BhagwaOrange,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column {
                Text(
                    text = "Strict Core vs. Plugin Separation",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = CosmicBlue
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "Heavy AI models and cloud drivers remain isolated modules. Core APK stays lightweight, fast, and battery friendly.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun OcrPluginCard(
    isEnabled: Boolean,
    isModelDownloaded: Boolean,
    isDownloading: Boolean,
    downloadProgress: Float,
    onToggleEnabled: (Boolean) -> Unit,
    onDownloadModel: () -> Unit,
    onTestScan: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ocr_plugin_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BhagwaOrange.copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.DocumentScanner,
                                contentDescription = null,
                                tint = BhagwaOrange,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "ML Kit OCR Text Scanner",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = CosmicBlue
                        )
                        Text(
                            text = "v1.0.0 • On-Demand Module",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Switch(
                    checked = isEnabled,
                    onCheckedChange = onToggleEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = BhagwaOrange
                    ),
                    modifier = Modifier.testTag("ocr_plugin_toggle")
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = "Extracts text from camera photos, screenshots, and multi-page PDFs using high-speed, 100% on-device machine learning with zero cloud dependencies.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Badges
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                PluginTagBadge(label = "PDF + Images", color = CosmicBlue)
                PluginTagBadge(label = "100% Offline", color = EmeraldGreen)
                PluginTagBadge(label = "12 MB Footprint", color = SoftGold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action area
            if (isDownloading) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "Downloading ML Model: ${(downloadProgress * 100).toInt()}%",
                        style = MaterialTheme.typography.labelMedium,
                        color = BhagwaOrange
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { downloadProgress },
                        color = BhagwaOrange,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            } else if (!isModelDownloaded) {
                FilledTonalButton(
                    onClick = onDownloadModel,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Download On-Demand Model (12 MB)")
                }
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = onTestScan,
                        enabled = isEnabled,
                        colors = ButtonDefaults.buttonColors(containerColor = BhagwaOrange),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("ocr_test_scan_button")
                    ) {
                        Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Test OCR Scan")
                    }
                }
            }
        }
    }
}

@Composable
private fun SemanticSearchPluginCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SkyCyan.copy(alpha = 0.2f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Psychology,
                                contentDescription = null,
                                tint = CosmicBlue,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "AI Semantic Search (TFLite)",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = CosmicBlue
                        )
                        Text(
                            text = "v1.0.0 • Phase 9 Plugin",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                PluginTagBadge(label = "Phase 9", color = SkyCyan)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "On-device lightweight embedding vectors for concept & meaning-based document discovery.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun CloudDriversPluginCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = SoftGold.copy(alpha = 0.2f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CloudSync,
                                contentDescription = null,
                                tint = BhagwaOrange,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column {
                        Text(
                            text = "Multi-Cloud Storage Drivers",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = CosmicBlue
                        )
                        Text(
                            text = "v1.0.0 • Phase 11 Plugin",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                PluginTagBadge(label = "Phase 11", color = SoftGold)
            }

            Spacer(modifier = Modifier.height(10.dp))

            Text(
                text = "Google Drive (Core) + OneDrive, Dropbox, NextCloud, S3, and local NAS network drives as modular plugins.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun PluginTagBadge(label: String, color: Color) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = color.copy(alpha = 0.12f),
        modifier = Modifier.padding(vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = color,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
