package com.vvf.smartmanager.feature.settings

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val BhagwaOrange = Color(0xFFF47B20)
private val CosmicBlue = Color(0xFF102B52)
private val EmeraldGreen = Color(0xFF3FA34D)
private val SkyCyan = Color(0xFF5BC0EB)
private val SoftGold = Color(0xFFD4A95A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: (() -> Unit)? = null,
    initialBiometricEnabled: Boolean = false,
    onBiometricEnabledChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var biometricEnabled by remember(initialBiometricEnabled) { mutableStateOf(initialBiometricEnabled) }
    var autoIndexOcr by remember { mutableStateOf(true) }
    var offlineOnlyMode by remember { mutableStateOf(true) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showArchitectureDialog by remember { mutableStateOf(false) }

    Scaffold(
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
                        Spacer(modifier = Modifier.width(12.dp))
                        Text("Settings & Compliance", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(
                            onClick = onNavigateBack,
                            modifier = Modifier.testTag("settings_back_btn")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Back"
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
                modifier = Modifier.testTag("settings_top_app_bar")
            )
        },
        modifier = modifier.testTag("settings_screen_root")
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            item {
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = CosmicBlue),
                    modifier = Modifier.fillMaxWidth().testTag("branding_card")
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text(
                            text = "VVF Smart Manager",
                            color = BhagwaOrange,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Text(
                            text = "Vishva Vijayaa Foundation",
                            color = Color.White.copy(alpha = 0.9f),
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 2.dp)
                        )
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = BhagwaOrange,
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = "v1.0.0-PROD",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }

            item {
                Text(
                    text = "Security & Cryptography",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingToggleRow(
                            icon = Icons.Default.Lock,
                            title = "Biometric Vault Authentication",
                            subtitle = "Use Fingerprint / Face Unlock for hardware Keystore authentication",
                            isChecked = biometricEnabled,
                            onCheckedChange = { enabled ->
                                biometricEnabled = enabled
                                onBiometricEnabledChange?.invoke(enabled)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        SettingToggleRow(
                            icon = Icons.Default.Security,
                            title = "Zero-Knowledge Hardware Keystore",
                            subtitle = "PIN/Password is never stored in plaintext on disk",
                            isChecked = true,
                            enabled = false,
                            onCheckedChange = {}
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Search & OCR Engine",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingToggleRow(
                            icon = Icons.Default.Storage,
                            title = "Auto-Index OCR Text in Core Search",
                            subtitle = "Automatically index keywords into SQLite FTS4",
                            isChecked = autoIndexOcr,
                            onCheckedChange = { autoIndexOcr = it }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        SettingToggleRow(
                            icon = Icons.Default.CheckCircle,
                            title = "Strict Offline-First Core",
                            subtitle = "All core file management and search executes locally",
                            isChecked = offlineOnlyMode,
                            onCheckedChange = { offlineOnlyMode = it }
                        )
                    }
                }
            }

            item {
                Text(
                    text = "Legal & Architecture",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    )
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        TextButton(
                            onClick = { showLicensesDialog = true },
                            modifier = Modifier.testTag("view_licenses_button")
                        ) {
                            Text("View Open Source Licenses", fontWeight = FontWeight.Bold, color = BhagwaOrange)
                        }
                        TextButton(
                            onClick = { showArchitectureDialog = true },
                            modifier = Modifier.testTag("view_architecture_button")
                        ) {
                            Text("View Architecture Notes", fontWeight = FontWeight.Bold, color = BhagwaOrange)
                        }
                    }
                }
            }
        }
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text("Open Source Licenses") },
            text = {
                Text("Core libraries use Apache 2.0 / MIT / BSD licenses. See APPLIED_FINDINGS and project docs for the full SBOM.")
            },
            confirmButton = {
                TextButton(onClick = { showLicensesDialog = false }) { Text("Close") }
            }
        )
    }
    if (showArchitectureDialog) {
        AlertDialog(
            onDismissRequest = { showArchitectureDialog = false },
            title = { Text("Architecture") },
            text = {
                Text("Manual composition root in VVFApplication; WorkManager same-process bridges; FailClosedRestorePipeline for cloud restore. Hilt deferred (AGP 9).")
            },
            confirmButton = {
                TextButton(onClick = { showArchitectureDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun SettingToggleRow(
    icon: ImageVector,
    title: String,
    subtitle: String,
    isChecked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(text = title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = BhagwaOrange)
        )
    }
}
