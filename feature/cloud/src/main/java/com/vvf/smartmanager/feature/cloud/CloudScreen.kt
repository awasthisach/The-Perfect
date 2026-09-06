package com.vvf.smartmanager.feature.cloud

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vvf.smartmanager.core.common.FormatUtils
import com.vvf.smartmanager.core.common.R as CommonR
import com.vvf.smartmanager.core.model.CloudAccount
import com.vvf.smartmanager.core.model.CloudProviderType

private val BhagwaOrange = Color(0xFFF47B20)
private val CosmicBlue = Color(0xFF102B52)
private val EmeraldGreen = Color(0xFF3FA34D)
private val SoftGold = Color(0xFFD4A95A)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CloudScreen(
    viewModel: CloudViewModel,
    onGoogleDriveSignInRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.statusMessage) {
        uiState.statusMessage?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearStatusMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = CommonR.drawable.vvf_foundation_logo),
                            contentDescription = "VVF",
                            modifier = Modifier.size(34.dp).padding(end = 8.dp)
                        )
                        Column {
                            Text("Cloud Manager", fontWeight = FontWeight.Bold, color = BhagwaOrange)
                            Text(
                                "॥ विजया ददाति विजयम् ॥",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = SoftGold
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.loadAllAccounts() }, modifier = Modifier.testTag("refresh_cloud_button")) {
                        Icon(Icons.Default.Refresh, contentDescription = "Refresh", tint = CosmicBlue)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface)
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize().testTag("cloud_screen_root")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(innerPadding).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            item {
                Text(
                    "Storage Providers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CosmicBlue
                )
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(CloudProviderType.values().toList()) { provider ->
                        val account = uiState.accounts[provider]
                        val isConnected = account?.isConnected == true
                        val isSelected = uiState.selectedProvider == provider
                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectProvider(provider) },
                            label = {
                                Text(
                                    if (provider.isCore) "${provider.displayName} (Core)" else provider.displayName,
                                    fontSize = 12.sp
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    if (isConnected) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                                    contentDescription = null,
                                    tint = if (isConnected) EmeraldGreen else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = BhagwaOrange.copy(alpha = 0.2f),
                                selectedLabelColor = BhagwaOrange
                            ),
                            modifier = Modifier.testTag("provider_chip_${provider.name.lowercase()}")
                        )
                    }
                }
            }

            item {
                val currentProvider = uiState.selectedProvider
                val currentAccount = uiState.accounts[currentProvider] ?: CloudAccount(currentProvider)
                Card(
                    modifier = Modifier.fillMaxWidth().testTag("provider_quota_card"),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = CosmicBlue.copy(alpha = 0.05f))
                ) {
                    Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    currentAccount.displayName.ifBlank { currentProvider.displayName },
                                    fontWeight = FontWeight.Bold,
                                    color = CosmicBlue
                                )
                                Text(
                                    if (currentAccount.isConnected) {
                                        currentAccount.accountEmail.ifBlank { "Connected" }
                                    } else {
                                        "Not Authenticated"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (!currentAccount.isConnected) {
                                Button(
                                    onClick = {
                                        if (currentProvider == CloudProviderType.GOOGLE_DRIVE) {
                                            viewModel.beginGoogleDriveSignIn()
                                            onGoogleDriveSignInRequested()
                                        } else {
                                            viewModel.connectProvider(currentProvider)
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = BhagwaOrange),
                                    modifier = Modifier.testTag("connect_provider_button")
                                ) {
                                    Text("Connect", fontSize = 12.sp)
                                }
                            } else {
                                Surface(shape = RoundedCornerShape(6.dp), color = EmeraldGreen.copy(alpha = 0.15f)) {
                                    Text(
                                        "Active",
                                        color = EmeraldGreen,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                    )
                                }
                            }
                        }
                        if (currentAccount.isConnected && currentAccount.totalBytes > 0) {
                            Spacer(modifier = Modifier.height(12.dp))
                            Text(
                                "${FormatUtils.formatBytes(currentAccount.usedBytes)} / ${FormatUtils.formatBytes(currentAccount.totalBytes)}",
                                style = MaterialTheme.typography.labelMedium
                            )
                            LinearProgressIndicator(
                                progress = { currentAccount.usedPercentage },
                                modifier = Modifier.fillMaxWidth().padding(top = 6.dp)
                            )
                        }
                    }
                }
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.Backup, contentDescription = null, tint = BhagwaOrange)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Backup options", fontWeight = FontWeight.Bold, color = CosmicBlue)
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Include Vault in backup")
                            Switch(
                                checked = uiState.includeVaultInBackup,
                                onCheckedChange = { viewModel.toggleVaultBackup(it) }
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Button(
                            onClick = { viewModel.triggerCloudBackup() },
                            enabled = !uiState.isBackingUp,
                            colors = ButtonDefaults.buttonColors(containerColor = BhagwaOrange),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(if (uiState.isBackingUp) "Backing up…" else "Start cloud backup")
                        }
                    }
                }
            }

            if (uiState.backupHistory.isNotEmpty()) {
                item {
                    Text(
                        "Cloud Snapshots",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CosmicBlue
                    )
                }
                items(uiState.backupHistory) { backup ->
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(backup.backupName, fontWeight = FontWeight.SemiBold)
                                Text(
                                    FormatUtils.formatBytes(backup.backupSizeBytes) +
                                        if (backup.includesVault) " (Vault)" else "",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            OutlinedButton(onClick = { viewModel.restoreBackup(backup) }) {
                                Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Restore", fontSize = 11.sp)
                            }
                        }
                    }
                }
            } else {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "No cloud snapshots yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}
