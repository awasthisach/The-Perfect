package com.vvf.smartmanager.feature.settings

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
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private val BhagwaOrange = Color(0xFFF47B20)
private val CosmicBlue = Color(0xFF102B52)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onNavigateBack: (() -> Unit)? = null,
    initialBiometricEnabled: Boolean = false,
    onBiometricEnabledChange: (suspend (Boolean, String?) -> Boolean)? = null,
    initialAutoIndexOcr: Boolean = false,
    onAutoIndexOcrChange: ((Boolean) -> Unit)? = null,
    initialOfflineOnlyMode: Boolean = true,
    onOfflineOnlyModeChange: ((Boolean) -> Unit)? = null,
    initialFirebaseIdentityAvailable: Boolean = false,
    initialFirebaseIdentityConnected: Boolean = false,
    onFirebaseIdentityChange: (suspend (Boolean) -> Boolean)? = null,
    modifier: Modifier = Modifier
) {
    var biometricEnabled by remember(initialBiometricEnabled) { mutableStateOf(initialBiometricEnabled) }
    var autoIndexOcr by remember(initialAutoIndexOcr) { mutableStateOf(initialAutoIndexOcr) }
    var offlineOnlyMode by remember(initialOfflineOnlyMode) { mutableStateOf(initialOfflineOnlyMode) }
    var firebaseIdentityConnected by remember(initialFirebaseIdentityConnected) { mutableStateOf(initialFirebaseIdentityConnected) }
    var firebaseIdentityBusy by remember { mutableStateOf(false) }
    var firebaseIdentityError by remember { mutableStateOf(false) }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showArchitectureDialog by remember { mutableStateOf(false) }
    var showBiometricAuthDialog by remember { mutableStateOf(false) }
    var biometricPin by remember { mutableStateOf("") }
    var biometricAuthError by remember { mutableStateOf(false) }
    var biometricEnrollmentInProgress by remember { mutableStateOf(false) }
    val coroutineScope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings & Compliance", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    if (onNavigateBack != null) {
                        IconButton(onClick = onNavigateBack, modifier = Modifier.testTag("settings_back_btn")) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                modifier = Modifier.testTag("settings_top_app_bar")
            )
        },
        modifier = modifier.testTag("settings_screen_root")
    ) { innerPadding ->
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize().padding(innerPadding)
        ) {
            item {
                ElevatedCard(
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = CosmicBlue),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("VVF Smart Manager", color = BhagwaOrange, fontSize = 20.sp, fontWeight = FontWeight.ExtraBold)
                        Text("Vishva Vijayaa Foundation", color = Color.White.copy(alpha = 0.9f), fontSize = 12.sp)
                    }
                }
            }
            item {
                Text("Security & Cryptography", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingToggleRow(
                            icon = Icons.Default.Lock,
                            title = "Biometric Vault Authentication",
                            subtitle = "Vault PIN and enrolled strong biometrics required to enable",
                            isChecked = biometricEnabled,
                            enabled = !biometricEnrollmentInProgress,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    biometricPin = ""
                                    biometricAuthError = false
                                    showBiometricAuthDialog = true
                                } else {
                                    coroutineScope.launch {
                                        biometricEnrollmentInProgress = true
                                        val disabled = onBiometricEnabledChange?.invoke(false, null) != false
                                        if (disabled) biometricEnabled = false
                                        biometricEnrollmentInProgress = false
                                    }
                                }
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        SettingToggleRow(
                            icon = Icons.Default.Security,
                            title = "Zero-Knowledge Hardware Keystore",
                            subtitle = "PIN is never stored in plaintext",
                            isChecked = true,
                            enabled = false,
                            onCheckedChange = {}
                        )
                    }
                }
            }
            item {
                Text("Search & OCR Engine", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Card(shape = RoundedCornerShape(14.dp)) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        SettingToggleRow(
                            icon = Icons.Default.Storage,
                            title = "Auto-Index OCR Text in Core Search",
                            subtitle = "Index OCR keywords into SQLite FTS4",
                            isChecked = autoIndexOcr,
                            onCheckedChange = {
                                autoIndexOcr = it
                                onAutoIndexOcrChange?.invoke(it)
                            }
                        )
                        HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                        SettingToggleRow(
                            icon = Icons.Default.CheckCircle,
                            title = "Strict Offline-First Core",
                            subtitle = "Core file management runs locally",
                            isChecked = offlineOnlyMode,
                            onCheckedChange = {
                                offlineOnlyMode = it
                                onOfflineOnlyModeChange?.invoke(it)
                            }
                        )
                    }
                }
            }
            if (initialFirebaseIdentityAvailable) {
                item {
                    Text("Protected Cloud Identity", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Card(shape = RoundedCornerShape(14.dp)) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                if (firebaseIdentityConnected) "Firebase identity connected" else "No protected-service identity connected",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                if (offlineOnlyMode) "Identity connection is blocked while Strict Offline-First is enabled. Turn it off to connect. This does not send file text; separate explicit AI-data consent is still required." else "This establishes identity only. File text is not sent to the embedding service by this setting; separate explicit AI-data consent is still required.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 6.dp)
                            )
                            if (firebaseIdentityError) {
                                Text(
                                    "Authentication could not be completed. Check Firebase Google sign-in configuration and try again.",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(top = 6.dp)
                                )
                            }
                            TextButton(
                                enabled = !firebaseIdentityBusy && onFirebaseIdentityChange != null && (!offlineOnlyMode || firebaseIdentityConnected),
                                onClick = {
                                    val connect = !firebaseIdentityConnected
                                    coroutineScope.launch {
                                        firebaseIdentityBusy = true
                                        firebaseIdentityError = false
                                        val succeeded = runCatching {
                                            onFirebaseIdentityChange?.invoke(connect) == true
                                        }.getOrDefault(false)
                                        if (succeeded) firebaseIdentityConnected = connect
                                        else firebaseIdentityError = true
                                        firebaseIdentityBusy = false
                                    }
                                },
                                modifier = Modifier.testTag("firebase_identity_toggle")
                            ) {
                                Text(
                                    when {
                                        firebaseIdentityBusy -> "Working…"
                                        firebaseIdentityConnected -> "Disconnect Firebase identity"
                                        else -> "Connect Google account to Firebase"
                                    }
                                )
                            }
                        }
                    }
                }
            }
            item {
                Text("Legal & Architecture", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                TextButton(onClick = { showLicensesDialog = true }) {
                    Text("View Open Source Licenses", fontWeight = FontWeight.Bold, color = BhagwaOrange)
                }
                TextButton(onClick = { showArchitectureDialog = true }) {
                    Text("View Architecture Notes", fontWeight = FontWeight.Bold, color = BhagwaOrange)
                }
            }
        }
    }


    if (showBiometricAuthDialog) {
        AlertDialog(
            onDismissRequest = {
                if (!biometricEnrollmentInProgress) {
                    showBiometricAuthDialog = false
                    biometricPin = ""
                    biometricAuthError = false
                }
            },
            title = { Text("Confirm vault PIN") },
            text = {
                Column {
                    Text("Enter your vault PIN to enable biometric unlock.")
                    OutlinedTextField(
                        value = biometricPin,
                        onValueChange = { biometricPin = it.filter(Char::isDigit).take(6) },
                        label = { Text("Vault PIN") },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        isError = biometricAuthError,
                        modifier = Modifier.testTag("biometric_pin_input")
                    )
                    if (biometricAuthError) {
                        Text("Biometric unlock could not be enabled. Check your PIN and enrolled strong biometric.", color = MaterialTheme.colorScheme.error)
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !biometricEnrollmentInProgress,
                    onClick = {
                        showBiometricAuthDialog = false
                        biometricPin = ""
                        biometricAuthError = false
                    }
                ) { Text("Cancel") }
            },
            confirmButton = {
                TextButton(
                    modifier = Modifier.testTag("biometric_pin_confirm"),
                    enabled = !biometricEnrollmentInProgress,
                    onClick = {
                        val pin = biometricPin
                        coroutineScope.launch {
                            biometricEnrollmentInProgress = true
                            val allowed = onBiometricEnabledChange?.invoke(true, pin) == true
                            if (allowed) {
                                biometricEnabled = true
                                showBiometricAuthDialog = false
                                biometricPin = ""
                                biometricAuthError = false
                            } else {
                                biometricAuthError = true
                            }
                            biometricEnrollmentInProgress = false
                        }
                    }
                ) { Text(if (biometricEnrollmentInProgress) "Checking…" else "Confirm") }
            }
        )
    }

    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text("Open Source Licenses") },
            text = { Text("Core libraries use Apache 2.0 / MIT / BSD. See project docs for SBOM.") },
            confirmButton = { TextButton(onClick = { showLicensesDialog = false }) { Text("Close") } }
        )
    }
    if (showArchitectureDialog) {
        AlertDialog(
            onDismissRequest = { showArchitectureDialog = false },
            title = { Text("Architecture") },
            text = { Text("Manual composition root; WorkManager same-process; FailClosedRestorePipeline.") },
            confirmButton = { TextButton(onClick = { showArchitectureDialog = false }) { Text("Close") } }
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
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Icon(imageVector = icon, contentDescription = null, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = isChecked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            colors = SwitchDefaults.colors(checkedTrackColor = BhagwaOrange)
        )
    }
}
