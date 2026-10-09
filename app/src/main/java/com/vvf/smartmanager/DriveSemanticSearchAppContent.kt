package com.vvf.smartmanager

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.vvf.smartmanager.core.background.drive.DriveContentIndexWorker
import com.vvf.smartmanager.core.background.drive.DriveMetadataSyncWorker
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.database.model.DriveIndexFileEntity
import com.vvf.smartmanager.core.domain.DriveIndexBackupManager
import com.vvf.smartmanager.core.domain.DriveOfflinePinManager
import com.vvf.smartmanager.core.domain.DriveRankedResult
import com.vvf.smartmanager.core.domain.DriveSearchRepository
import com.vvf.smartmanager.core.domain.DriveSearchTypeFilter
import com.vvf.smartmanager.feature.vault.VaultScreen
import com.vvf.smartmanager.feature.vault.VaultViewModel
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

private enum class DriveTab(val title: String) {
    DASHBOARD("Dashboard"),
    SEARCH("Search"),
    DUPLICATES("Duplicates"),
    VAULT("Vault"),
    OFFLINE("Offline"),
    BACKUP("Backup")
}

/**
 * Native Drive-first app surface. All Drive mutations are explicit user actions; this screen never
 * calls Drive delete/trash endpoints. Neural embeddings remain blocked until the worker explicitly
 * enables and verifies native Android App Check.
 */
@Composable
fun DriveSemanticSearchAppContent(
    onGoogleDriveSignInRequested: ((Result<String>) -> Unit) -> Unit,
    onGoogleDriveSignOutRequested: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as VVFApplication
    val dao = remember(app) { app.database.driveIndexDao() }
    val scope = rememberCoroutineScope()
    var tab by remember { mutableStateOf(DriveTab.DASHBOARD) }
    var statusMessage by remember { mutableStateOf("") }
    var syncState by remember { mutableStateOf<com.vvf.smartmanager.core.database.model.DriveSyncStateEntity?>(null) }
    var allFiles by remember { mutableStateOf<List<DriveIndexFileEntity>>(emptyList()) }
    var indexedCount by remember { mutableStateOf(0) }
    var pendingTextCount by remember { mutableStateOf(0) }
    var failedTextCount by remember { mutableStateOf(0) }
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf(DriveSearchTypeFilter.ALL) }
    var results by remember { mutableStateOf<List<DriveRankedResult>>(emptyList()) }
    var duplicateGroups by remember { mutableStateOf<List<com.vvf.smartmanager.core.database.dao.DriveDuplicateGroup>>(emptyList()) }
    var selectedDuplicate by remember { mutableStateOf<com.vvf.smartmanager.core.database.dao.DriveDuplicateGroup?>(null) }
    var selectedDuplicateFiles by remember { mutableStateOf<List<DriveIndexFileEntity>>(emptyList()) }
    var moveDialogFile by remember { mutableStateOf<DriveIndexFileEntity?>(null) }
    var moveDialogOpen by remember { mutableStateOf(false) }
    var moveConfirmationOpen by remember { mutableStateOf(false) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var selectedFolderName by remember { mutableStateOf<String?>(null) }
    var suggestionDialogFile by remember { mutableStateOf<DriveIndexFileEntity?>(null) }
    var folderSuggestions by remember { mutableStateOf<List<DriveIndexFileEntity>>(emptyList()) }
    var showEmbeddingConsent by remember { mutableStateOf(false) }
    var embeddingConsent by remember { mutableStateOf(app.isEmbeddingConsentGranted()) }
    val pinnedFiles by dao.observePinnedFiles().collectAsState(initial = emptyList())
    val backupManager = remember(app) { DriveIndexBackupManager(app.database) }
    val pinManager = remember(app) { DriveOfflinePinManager(app.filesDir, app.googleDriveService, dao) }

    val signedInEmail = GoogleSignIn.getLastSignedInAccount(context)?.email
    val firebaseEmail = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser?.email
    val connected = !signedInEmail.isNullOrBlank() &&
        !firebaseEmail.isNullOrBlank() &&
        signedInEmail.equals(firebaseEmail, ignoreCase = true)

    suspend fun refreshLocalState() {
        syncState = dao.getSyncState()
        allFiles = dao.getRecentFiles(20_000)
        indexedCount = allFiles.count { it.indexStatus == "TEXT_INDEXED" || it.indexStatus == "OCR_INDEXED" }
        pendingTextCount = allFiles.count { it.indexStatus == "METADATA_ONLY" }
        failedTextCount = allFiles.count {
            it.indexStatus in setOf("DOWNLOAD_FAILED", "EXTRACTION_FAILED", "TOO_LARGE")
        }
        duplicateGroups = dao.findNameAndSizeDuplicateGroups()
    }

    LaunchedEffect(Unit) { refreshLocalState() }
    LaunchedEffect(query, filter) {
        if (query.isBlank()) {
            results = emptyList()
        } else {
            results = runCatching { DriveSearchRepository(dao).search(query, filter, limit = 200) }
                .getOrDefault(emptyList())
        }
    }

    val vaultViewModel: VaultViewModel = viewModel(
        factory = VaultViewModel.provideFactory(
            getVaultItemsUseCase = app.getVaultItemsUseCase,
            lockFileInVaultUseCase = app.lockFileInVaultUseCase,
            restoreVaultItemUseCase = app.restoreVaultItemUseCase,
            exportVaultItemUseCase = app.exportVaultItemUseCase,
            deleteVaultItemUseCase = app.deleteVaultItemUseCase,
            vaultAuthUseCase = app.vaultAuthUseCase
        )
    )

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri: Uri? ->
        if (uri != null) scope.launch {
            val output = context.contentResolver.openOutputStream(uri)
            if (output == null) {
                statusMessage = "Could not open the selected backup destination."
            } else {
                output.use {
                    backupManager.exportTo(it)
                        .onSuccess { count -> statusMessage = "Exported $count local index records. No credentials or pin bytes were exported." }
                        .onFailure { statusMessage = it.message ?: "Local index export failed." }
                }
            }
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) scope.launch {
            val input = context.contentResolver.openInputStream(uri)
            if (input == null) {
                statusMessage = "Could not read the selected backup file."
            } else {
                input.use {
                    backupManager.importFrom(it)
                        .onSuccess { count ->
                            statusMessage = "Imported $count local index records. Drive sync will rebuild its cursor."
                            refreshLocalState()
                        }
                        .onFailure { statusMessage = it.message ?: "Local index import failed." }
                }
            }
        }
    }

    Scaffold(
        bottomBar = {
            NavigationBar {
                DriveTab.entries.forEach { item ->
                    NavigationBarItem(
                        selected = tab == item,
                        onClick = { tab = item },
                        icon = { Text(item.title.take(1)) },
                        label = { Text(item.title, maxLines = 1) }
                    )
                }
            }
        }
    ) { insets ->
        Column(
            modifier = Modifier.fillMaxSize().padding(insets).padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("Drive Semantic Search", style = MaterialTheme.typography.headlineSmall)
            if (statusMessage.isNotBlank()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp)) {
                        Text(statusMessage, style = MaterialTheme.typography.bodyMedium)
                        TextButton(onClick = { statusMessage = "" }) { Text("Dismiss") }
                    }
                }
            }
            when (tab) {
                DriveTab.DASHBOARD -> {
                    Text("Google Drive: ${if (connected) "Connected" else "Not connected"}")
                    Text("Account: ${firebaseEmail ?: "Not signed in"}")
                    Text("Indexed text: $indexedCount · Awaiting extraction: $pendingTextCount · Failed: $failedTextCount")
                    Text("Drive metadata: ${if (syncState?.listingIncomplete == true) "Listing may be incomplete (20,000-file cap)" else "Last sync recorded"}")
                    syncState?.lastSyncAtMs?.let { Text("Last sync: ${DateFormat.getDateTimeInstance().format(Date(it))}") }
                    syncState?.lastError?.let { Text("Sync note: $it") }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (!connected) {
                            Button(onClick = {
                                onGoogleDriveSignInRequested { result ->
                                    result.onSuccess {
                                        statusMessage = "Google and Firebase sessions linked. Drive sync queued."
                                        scope.launch { refreshLocalState() }
                                    }.onFailure { statusMessage = it.message ?: "Google sign-in failed." }
                                }
                            }) { Text("Sign in with Google") }
                        } else {
                            Button(onClick = {
                                DriveMetadataSyncWorker.enqueue(context)
                                statusMessage = "Drive metadata sync queued."
                            }) { Text("Sync Drive") }
                            OutlinedButton(onClick = {
                                onGoogleDriveSignOutRequested()
                                statusMessage = "Signed out of Google Drive and Firebase."
                            }) { Text("Sign out") }
                        }
                        OutlinedButton(onClick = { scope.launch { refreshLocalState() } }) { Text("Refresh") }
                    }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Optional neural embeddings", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (embeddingConsent) "Consent recorded." else "Consent is off.",
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                "Neural requests stay disabled until the existing worker explicitly enables verified Android App Check. Keyword and metadata search remain available.",
                                style = MaterialTheme.typography.bodySmall
                            )
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Button(onClick = { showEmbeddingConsent = true }) {
                                    Text(if (embeddingConsent) "Review consent" else "Review consent")
                                }
                                if (embeddingConsent) {
                                    OutlinedButton(onClick = {
                                        app.setEmbeddingConsentGranted(false)
                                        embeddingConsent = false
                                        statusMessage = "Embedding consent withdrawn; stored vectors are being cleared."
                                    }) { Text("Withdraw") }
                                }
                            }
                        }
                    }
                    if (showEmbeddingConsent) {
                        AlertDialog(
                            onDismissRequest = { showEmbeddingConsent = false },
                            title = { Text("Optional neural-search consent") },
                            text = {
                                Text(
                                    "If enabled in a future verified backend release, your query and selected indexed text may be sent to the embedding service. The app will not send requests until Android App Check is explicitly enabled by the worker. No Gemini API key is stored in this app."
                                )
                            },
                            confirmButton = {
                                TextButton(onClick = {
                                    app.setEmbeddingConsentGranted(true)
                                    embeddingConsent = true
                                    showEmbeddingConsent = false
                                    statusMessage = "Consent recorded. Neural requests remain blocked until the worker enables Android App Check."
                                }) { Text("I consent") }
                            },
                            dismissButton = {
                                TextButton(onClick = { showEmbeddingConsent = false }) { Text("Not now") }
                            }
                        )
                    }
                }

                DriveTab.SEARCH -> {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Search indexed Drive content") },
                        singleLine = true
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(
                            DriveSearchTypeFilter.ALL to "All",
                            DriveSearchTypeFilter.DOCUMENTS to "Documents",
                            DriveSearchTypeFilter.SPREADSHEETS to "Spreadsheets",
                            DriveSearchTypeFilter.PRESENTATIONS to "Presentations",
                            DriveSearchTypeFilter.IMAGES to "Images"
                        ).forEach { (value, label) ->
                            FilterChip(selected = filter == value, onClick = { filter = value }, label = { Text(label) })
                        }
                    }
                    Text("Local text/metadata search remains available without embeddings.")
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(results, key = { it.file.driveFileId }) { result ->
                            val file = result.file
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                                    Text(file.name, style = MaterialTheme.typography.titleMedium)
                                    Text("${file.mimeType} · ${file.sizeBytes} bytes · score ${"%.3f".format(result.score)}")
                                    if (file.extractedText.isNotBlank()) Text(file.extractedText.take(240))
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(onClick = {
                                            moveDialogFile = file
                                            selectedFolderId = null
                                            selectedFolderName = null
                                            moveDialogOpen = true
                                        }) { Text("Move") }
                                        TextButton(onClick = {
                                            scope.launch {
                                                app.googleDriveService.starFile(file.driveFileId, !file.starred)
                                                    .onSuccess {
                                                        statusMessage = if (it) "Star state updated." else "Drive did not confirm the star change."
                                                        DriveMetadataSyncWorker.enqueue(context)
                                                        refreshLocalState()
                                                    }
                                                    .onFailure { statusMessage = it.message ?: "Star update failed." }
                                            }
                                        }) { Text(if (file.starred) "Unstar" else "Star") }
                                        TextButton(onClick = {
                                            scope.launch {
                                                pinManager.pin(file.driveFileId)
                                                    .onSuccess { statusMessage = "Pinned offline and verified SHA-256." }
                                                    .onFailure { statusMessage = it.message ?: "Offline pin failed." }
                                                refreshLocalState()
                                            }
                                        }) { Text("Pin offline") }
                                        TextButton(onClick = {
                                            suggestionDialogFile = file
                                            selectedFolderId = null
                                            selectedFolderName = null
                                            val terms = file.name.lowercase().split(Regex("[^\\p{L}\\p{Nd}]+")).filter { it.length >= 2 }.toSet()
                                            folderSuggestions = allFiles.filter {
                                                it.mimeType == "application/vnd.google-apps.folder" &&
                                                    it.driveFileId != file.driveFileId
                                            }.mapNotNull { folder ->
                                                val folderTerms = folder.name.lowercase().split(Regex("[^\\p{L}\\p{Nd}]+")).filter { it.length >= 2 }.toSet()
                                                val overlap = terms.intersect(folderTerms).size
                                                if (overlap > 0) folder to overlap else null
                                            }.sortedWith(compareByDescending<Pair<DriveIndexFileEntity, Int>> { it.second }.thenBy { it.first.name })
                                                .map { it.first }
                                            val suggested = folderSuggestions.firstOrNull()
                                            selectedFolderId = suggested?.driveFileId
                                            selectedFolderName = suggested?.name
                                        }) { Text("Suggest folder") }
                                    }
                                }
                            }
                        }
                    }
                }

                DriveTab.DUPLICATES -> {
                    Text("Candidates match the same name and size. No Drive file is deleted or trashed.")
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(duplicateGroups, key = { "${it.name}:${it.sizeBytes}" }) { group ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(group.name, style = MaterialTheme.typography.titleMedium)
                                        Text("${group.count} candidates · ${group.sizeBytes} bytes each")
                                    }
                                    TextButton(onClick = {
                                        scope.launch {
                                            selectedDuplicate = group
                                            selectedDuplicateFiles = dao.getDuplicateGroupFiles(group.name, group.sizeBytes)
                                        }
                                    }) { Text("Review") }
                                }
                            }
                        }
                    }
                }

                DriveTab.VAULT -> {
                    Text("Vault protection is intended for casual device access; it does not protect a compromised app or device.")
                    VaultScreen(viewModel = vaultViewModel)
                }

                DriveTab.OFFLINE -> {
                    Text("Pinned files · ${pinnedFiles.size}/${DriveOfflinePinManager.MAX_PINNED_FILES} files · 200 MB cap")
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(pinnedFiles, key = { it.driveFileId }) { file ->
                            Card(modifier = Modifier.fillMaxWidth()) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(Modifier.weight(1f)) {
                                        Text(file.name, style = MaterialTheme.typography.titleMedium)
                                        Text(file.pinnedPath.orEmpty())
                                    }
                                    TextButton(onClick = {
                                        scope.launch {
                                            pinManager.unpin(file.driveFileId)
                                                .onSuccess { statusMessage = "Offline pin removed. Drive file unchanged." }
                                                .onFailure { statusMessage = it.message ?: "Could not remove offline pin." }
                                            refreshLocalState()
                                        }
                                    }) { Text("Unpin") }
                                }
                            }
                        }
                    }
                }

                DriveTab.BACKUP -> {
                    Text("Export/import the local index only. OAuth tokens, Firebase ID tokens, API keys, vault records and pinned file bytes are excluded.")
                    Button(onClick = { exportLauncher.launch("drive-semantic-index.json") }) { Text("Export index JSON") }
                    OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json")) }) { Text("Import index JSON") }
                }
            }
        }
    }

    if (moveDialogOpen && moveDialogFile != null) {
        AlertDialog(
            onDismissRequest = { moveDialogOpen = false },
            title = { Text("Choose destination folder") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Select an existing Drive folder. Moving happens only after the next confirmation.")
                    LazyColumn {
                        items(allFiles.filter { it.mimeType == "application/vnd.google-apps.folder" && it.driveFileId != moveDialogFile?.driveFileId }.take(100)) { folder ->
                            Row {
                                RadioButton(
                                    selected = selectedFolderId == folder.driveFileId,
                                    onClick = {
                                        selectedFolderId = folder.driveFileId
                                        selectedFolderName = folder.name
                                    }
                                )
                                TextButton(onClick = {
                                    selectedFolderId = folder.driveFileId
                                    selectedFolderName = folder.name
                                }) { Text(folder.name) }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedFolderId != null,
                    onClick = {
                        moveDialogOpen = false
                        moveConfirmationOpen = true
                    }
                ) { Text("Review move") }
            },
            dismissButton = { TextButton(onClick = { moveDialogOpen = false }) { Text("Cancel") } }
        )
    }

    if (moveConfirmationOpen && moveDialogFile != null && selectedFolderId != null) {
        AlertDialog(
            onDismissRequest = { moveConfirmationOpen = false },
            title = { Text("Confirm Drive move") },
            text = { Text("Move '${moveDialogFile?.name}' to '${selectedFolderName}'? The file will not be deleted or trashed.") },
            confirmButton = {
                TextButton(onClick = {
                    val file = moveDialogFile
                    val folderId = selectedFolderId
                    moveConfirmationOpen = false
                    if (file != null && folderId != null) scope.launch {
                        app.googleDriveService.moveFile(file.driveFileId, folderId)
                            .onSuccess { moved ->
                                statusMessage = if (moved) "Drive move completed." else "Drive did not confirm the move."
                                DriveMetadataSyncWorker.enqueue(context)
                            }
                            .onFailure { statusMessage = it.message ?: "Drive move failed." }
                        refreshLocalState()
                    }
                }) { Text("Confirm move") }
            },
            dismissButton = { TextButton(onClick = { moveConfirmationOpen = false }) { Text("Cancel") } }
        )
    }

    if (suggestionDialogFile != null) {
        AlertDialog(
            onDismissRequest = { suggestionDialogFile = null },
            title = { Text("Folder suggestions") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Suggestions are based only on folder names already present in the local Drive index. Nothing moves automatically.")
                    if (folderSuggestions.isEmpty()) Text("No suitable existing folder name found.")
                    folderSuggestions.take(8).forEach { folder ->
                        TextButton(onClick = {
                            selectedFolderId = folder.driveFileId
                            selectedFolderName = folder.name
                        }) {
                            Text((if (selectedFolderId == folder.driveFileId) "✓ " else "") + folder.name)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    enabled = selectedFolderId != null,
                    onClick = {
                        moveDialogFile = suggestionDialogFile
                        suggestionDialogFile = null
                        moveDialogOpen = false
                        moveConfirmationOpen = true
                    }
                ) { Text("Review move") }
            },
            dismissButton = { TextButton(onClick = { suggestionDialogFile = null }) { Text("Cancel") } }
        )
    }

    if (selectedDuplicate != null) {
        AlertDialog(
            onDismissRequest = { selectedDuplicate = null },
            title = { Text("Duplicate candidates") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    selectedDuplicateFiles.forEach { file ->
                        Text("${file.name} · ${file.sizeBytes} bytes")
                        Text("SHA-256: ${file.contentSha256 ?: "not calculated; pin/download the bytes first"}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("No candidate is automatically chosen as the original. This screen does not delete or trash Drive files.")
                }
            },
            confirmButton = { TextButton(onClick = { selectedDuplicate = null }) { Text("Done") } }
        )
    }
}
