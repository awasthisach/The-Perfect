package com.vvf.smartmanager.feature.cloud

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import com.vvf.smartmanager.core.common.R as CommonR
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vvf.smartmanager.core.common.FormatUtils
import com.vvf.smartmanager.core.model.CloudAccount
import com.vvf.smartmanager.core.model.CloudBackupInfo
import com.vvf.smartmanager.core.model.CloudProviderType
import com.vvf.smartmanager.core.model.CloudSyncStatus
import com.vvf.smartmanager.core.model.FileItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val BhagwaOrange = Color(0xFFF47B20)
private val CosmicBlue = Color(0xFF102B52)
private val EmeraldGreen = Color(0xFF3FA34D)
private val SkyCyan = Color(0xFF5BC0EB)
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
                        androidx.compose.foundation.Image(
                            painter = androidx.compose.ui.res.painterResource(id = com.vvf.smartmanager.core.common.R.drawable.vvf_foundation_logo),
                            contentDescription = "Vishva Vijayaa Foundation Logo",
                            modifier = Modifier
                                .size(34.dp)
                                .padding(end = 8.dp)
                        )
                        Column {
                            Text(
                                text = "Cloud Manager",
                                fontWeight = FontWeight.Bold,
                                color = BhagwaOrange
                            )
                            Text(
                                text = "॥ विजया ददाति विजयम् ॥",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = SoftGold
                            )
                        }
                    }
                },
                actions = {
                    IconButton(
                        onClick = { viewModel.loadAllAccounts() },
                        modifier = Modifier.testTag("refresh_cloud_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Refresh Cloud",
                            tint = CosmicBlue
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier.fillMaxSize().testTag("cloud_screen_root")
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 24.dp)
        ) {
            // Hero Cloud Backup Art Banner
            item {
                Image(
                    painter = painterResource(id = CommonR.drawable.ic_vvf_cloud_backup_hero),
                    contentDescription = "Cloud Backup & Vault Sync Hero",
                    contentScale = ContentScale.FillWidth,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .padding(top = 4.dp)
                )
            }

            // 1. Cloud Provider Horizontal Selector
            item {
                Text(
                    text = "Storage Providers",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CosmicBlue,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp)
                )
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(CloudProviderType.values()) { provider ->
                        val account = uiState.accounts[provider]
                        val isConnected = account?.isConnected == true
                        val isSelected = uiState.selectedProvider == provider

                        FilterChip(
                            selected = isSelected,
                            onClick = { viewModel.selectProvider(provider) },
                            label = {
                                Text(
                                    text = if (provider.isCore) "${provider.displayName} (Core)" else provider.displayName,
                                    fontSize = 12.sp,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = if (isConnected) Icons.Default.CloudDone else Icons.Default.CloudQueue,
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

            // 2. Active Account Card & Quota Visualizer
            item {
                val currentProvider = uiState.selectedProvider
                val currentAccount = uiState.accounts[currentProvider] ?: CloudAccount(currentProvider)
                ProviderQuotaCard(
                    account = currentAccount,
                    isBackingUp = uiState.isBackingUp,
                    onConnectClick = {
                        if (currentProvider == CloudProviderType.GOOGLE_DRIVE) {
                            viewModel.beginGoogleDriveSignIn()
                            onGoogleDriveSignInRequested()
                        } else {
                            viewModel.connectProvider(currentProvider)
                        }
                    },
                    onBackupClick = { viewModel.triggerCloudBackup() }
                )
            }

            // 3. Cloud Backup Controls & Options Card
            item {
                BackupOptionsCard(
                    includeVault = uiState.includeVaultInBackup,
                    isBackingUp = uiState.isBackingUp,
                    onToggleVault = { viewModel.toggleVaultBackup(it) },
                    onStartBackup = { viewModel.triggerCloudBackup() }
                )
            }

            // 4. Remote Cloud Files Listing
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Remote Cloud Files",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CosmicBlue
                    )
                    Text(
                        text = "${uiState.remoteFiles.size} items",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            if (uiState.remoteFiles.isEmpty()) {
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth().padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "No remote files synced on ${uiState.selectedProvider.displayName}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } else {
                items(uiState.remoteFiles) { file ->
                    RemoteFileRow(file = file)
                }
            }

            // 5. Cloud Backup Snapshots History
            if (uiState.backupHistory.isNotEmpty()) {
                item {
                    Text(
                        text = "Cloud Snapshots History",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = CosmicBlue,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                }
                items(uiState.backupHistory) { backup ->
                    BackupHistoryRow(
                        backup = backup,
                        onRestore = { viewModel.restoreBackup(backup) }
                    )
                }
            }
        }
    }
}

@Composable
fun ProviderQuotaCard(
    account: CloudAccount,
    isBackingUp: Boolean,
    onConnectClick: () -> Unit,
    onBackupClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("provider_quota_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CosmicBlue.copy(alpha = 0.05f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        shape = CircleShape,
                        color = if (account.isConnected) EmeraldGreen.copy(alpha = 0.15f) else BhagwaOrange.copy(alpha = 0.15f),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = if (account.isConnected) Icons.Default.CloudDone else Icons.Default.CloudQueue,
                                contentDescription = null,
                                tint = if (account.isConnected) EmeraldGreen else BhagwaOrange,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = account.displayName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = CosmicBlue
                        )
                        Text(
                            text = if (account.isConnected) account.accountEmail.ifBlank { "Connected" } else "Not Authenticated",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (!account.isConnected) {
                    Button(
                        onClick = onConnectClick,
                        colors = ButtonDefaults.buttonColors(containerColor = BhagwaOrange),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("connect_provider_button")
                    ) {
                        Text("Connect", fontSize = 12.sp)
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = EmeraldGreen.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Active",
                            color = EmeraldGreen,
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            if (account.isConnected) {
                Spacer(modifier = Modifier.height(16.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Cloud Storage Used",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${FormatUtils.formatBytes(account.usedBytes)} / ${FormatUtils.formatBytes(account.totalBytes)} (${(account.usedPercentage * 100).toInt()}%)",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = CosmicBlue
                    )
                }
                Spacer(modifier = Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { account.usedPercentage },
                    color = if (account.usedPercentage > 0.9f) Color.Red else BhagwaOrange,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                )
            }
        }
    }
}

@Composable
fun BackupOptionsCard(
    includeVault: Boolean,
    isBackingUp: Boolean,
    onToggleVault: (Boolean) -> Unit,
    onStartBackup: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("backup_options_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Backup,
                    contentDescription = null,
                    tint = BhagwaOrange,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Cloud Snapshot & Backup",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = CosmicBlue
                )
            }

            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Include Encrypted Vault",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "AES-GCM zero-knowledge encrypted vault database backup",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = includeVault,
                    onCheckedChange = onToggleVault,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = BhagwaOrange,
                        checkedTrackColor = BhagwaOrange.copy(alpha = 0.5f)
                    ),
                    modifier = Modifier.testTag("switch_include_vault")
                )
            }

            Spacer(modifier = Modifier.height(14.dp))
            Button(
                onClick = onStartBackup,
                enabled = !isBackingUp,
                colors = ButtonDefaults.buttonColors(containerColor = CosmicBlue),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("create_cloud_backup_button")
            ) {
                if (isBackingUp) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Creating Snapshot...")
                } else {
                    Icon(imageVector = Icons.Default.CloudSync, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Create Full Cloud Backup")
                }
            }
        }
    }
}

@Composable
fun RemoteFileRow(file: FileItem) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = SkyCyan.copy(alpha = 0.2f),
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = if (file.isDirectory) Icons.Default.Folder else Icons.Default.InsertDriveFile,
                        contentDescription = null,
                        tint = SkyCyan,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = file.name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1
                )
                Text(
                    text = "${FormatUtils.formatBytes(file.sizeBytes)} • ${SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(Date(file.lastModified))}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.Default.CloudDone,
                contentDescription = "Synced",
                tint = EmeraldGreen,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
fun BackupHistoryRow(
    backup: CloudBackupInfo,
    onRestore: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 3.dp),
        shape = RoundedCornerShape(10.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = EmeraldGreen,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = backup.backupName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = "${FormatUtils.formatBytes(backup.backupSizeBytes)} • ${SimpleDateFormat("dd MMM HH:mm", Locale.getDefault()).format(Date(backup.timestamp))}" +
                                if (backup.includesVault) " (Vault Included)" else "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            OutlinedButton(
                onClick = onRestore,
                shape = RoundedCornerShape(8.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                modifier = Modifier.testTag("restore_backup_button_${backup.backupId}")
            ) {
                Icon(imageVector = Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(14.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("Restore", fontSize = 11.sp)
            }
        }
    }
}
