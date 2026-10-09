package com.vvf.smartmanager

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.OfflinePin
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.firebase.auth.FirebaseAuth
import com.vvf.smartmanager.core.background.drive.DriveMetadataSyncWorker
import com.vvf.smartmanager.core.cloud.gdrive.DriveSessionPolicy
import com.vvf.smartmanager.core.database.dao.DriveDuplicateGroup
import com.vvf.smartmanager.core.database.model.DriveIndexFileEntity
import com.vvf.smartmanager.core.domain.DriveIndexBackupManager
import com.vvf.smartmanager.core.domain.DriveRankedResult
import com.vvf.smartmanager.core.domain.DriveSearchRepository
import com.vvf.smartmanager.core.domain.DriveSearchTypeFilter
import com.vvf.smartmanager.core.domain.OfflinePinManager
import com.vvf.smartmanager.core.model.FileItem
import android.provider.OpenableColumns
import com.vvf.smartmanager.feature.vault.VaultScreen
import com.vvf.smartmanager.feature.vault.VaultViewModel
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class DriveHomeTab(val label: String) {
    DASHBOARD("Dashboard"), SEARCH("Search"), DUPLICATES("Duplicates"),
    VAULT("Vault"), OFFLINE("Offline"), BACKUP("Backup")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DriveSemanticSearchHome(
    app: VVFApplication,
    onGoogleDriveSignInRequested: ((Result<String>) -> Unit) -> Unit,
    onGoogleDriveSignOutRequested: suspend () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val dao = remember(app) { app.database.driveIndexDao() }
    val searchRepository = remember(app) {
        DriveSearchRepository(dao, { app.isEmbeddingConsentGranted() }, { false })
    }
    val pinManager = remember(app) { OfflinePinManager(context, app.googleDriveService, dao) }
    val backupManager = remember(app) {
        DriveIndexBackupManager(
            dao,
            { runCatching { FirebaseAuth.getInstance().currentUser?.email }.getOrNull() },
            { app.isAutoIndexOcrEnabled() }
        )
    }
    val uriHandler = LocalUriHandler.current
    val pinnedFiles by dao.observePinnedFiles().collectAsState(initial = emptyList())

    var activeTab by rememberSaveable { mutableStateOf(DriveHomeTab.DASHBOARD) }
    var accountEmail by remember { mutableStateOf<String?>(null) }
    var accountLinked by remember { mutableStateOf(false) }
    var indexedCount by remember { mutableStateOf(0) }
    var contentIndexedCount by remember { mutableStateOf(0) }
    var pendingContentCount by remember { mutableStateOf(0) }
    var syncState by remember { mutableStateOf<com.vvf.smartmanager.core.database.model.DriveSyncStateEntity?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var searchFilter by rememberSaveable { mutableStateOf(DriveSearchTypeFilter.ALL) }
    var searchResults by remember { mutableStateOf<List<DriveRankedResult>>(emptyList()) }
    var duplicateGroups by remember { mutableStateOf<List<DriveDuplicateGroup>>(emptyList()) }
    var duplicateHashes by remember { mutableStateOf<Map<String, Map<String, String>>>(emptyMap()) }
    var comparingDuplicateKey by remember { mutableStateOf<String?>(null) }
    var movingFile by remember { mutableStateOf<DriveIndexFileEntity?>(null) }
    var moveFolders by remember { mutableStateOf<List<DriveIndexFileEntity>>(emptyList()) }
    var selectedFolderId by remember { mutableStateOf<String?>(null) }
    var showEmbeddingConsentDialog by remember { mutableStateOf(false) }
    var showOcrConsentDialog by remember { mutableStateOf(false) }
    var showCreateFolderDialog by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }

    suspend fun refreshDashboard() {
        val firebaseEmail = runCatching { FirebaseAuth.getInstance().currentUser?.email }.getOrNull()
        val googleEmail = runCatching { GoogleSignIn.getLastSignedInAccount(context)?.email }.getOrNull()
        accountEmail = firebaseEmail
        accountLinked = DriveSessionPolicy.accountsMatch(googleEmail, firebaseEmail)
        syncState = dao.getSyncState()
        indexedCount = dao.getIndexedFileCount()
        contentIndexedCount = dao.getContentIndexedCount()
        pendingContentCount = dao.getPendingContentCount()
    }

    LaunchedEffect(Unit) { refreshDashboard() }
    LaunchedEffect(activeTab) {
        if (activeTab == DriveHomeTab.DUPLICATES) {
            duplicateGroups = runCatching { dao.findNameAndSizeDuplicateGroups() }.getOrDefault(emptyList())
        }
        refreshDashboard()
    }
    LaunchedEffect(searchQuery, searchFilter, activeTab) {
        if (activeTab != DriveHomeTab.SEARCH || searchQuery.isBlank()) {
            searchResults = emptyList()
        } else {
            delay(250)
            searchResults = runCatching {
                searchRepository.search(
                    query = searchQuery,
                    typeFilter = searchFilter,
                    embeddingsEnabled = app.isEmbeddingConsentGranted(),
                    limit = 200
                )
            }.getOrDefault(emptyList())
        }
    }

    fun requestSignIn() {
        onGoogleDriveSignInRequested { result ->
            scope.launch {
                statusMessage = result.fold(
                    onSuccess = { "Google account linked. Drive sync is queued." },
                    onFailure = { "Sign-in did not complete. Check the Google/Firebase account link and try again." }
                )
                refreshDashboard()
            }
        }
    }

    fun openMoveDialog(file: DriveIndexFileEntity, suggestedFolderId: String? = null) {
        scope.launch {
            val folders = dao.getDriveFolders()
            moveFolders = folders
            selectedFolderId = suggestedFolderId?.takeIf { id -> folders.any { it.driveFileId == id } }
            movingFile = file
            if (folders.isEmpty()) statusMessage = "No existing Drive folders are indexed yet. Sync Drive first."
        }
    }

    fun suggestFolder(file: DriveIndexFileEntity) {
        scope.launch {
            val folders = dao.getDriveFolders()
            val suggestion = bestExistingFolder(file, folders)
            if (suggestion == null) {
                statusMessage = "No strong match among existing Drive folders. No move was made."
            } else {
                moveFolders = folders
                selectedFolderId = suggestion.driveFileId
                movingFile = file
                statusMessage = "Suggested existing folder: ${suggestion.name}. Review and confirm before moving."
            }
        }
    }

    fun starFile(file: DriveIndexFileEntity) {
        scope.launch {
            app.googleDriveService.starFile(file.driveFileId, !file.starred).onSuccess {
                dao.updateStarred(file.driveFileId, !file.starred)
                DriveMetadataSyncWorker.enqueue(context)
                statusMessage = if (!file.starred) "File starred in Google Drive." else "Star removed in Google Drive."
                refreshDashboard()
            }.onFailure {
                statusMessage = "Could not update the Drive star. Confirm the account has Drive write access."
            }
        }
    }

    fun pinFile(file: DriveIndexFileEntity) {
        scope.launch {
            val result = if (file.pinnedPath != null) pinManager.unpin(file.driveFileId).map { file }
                else pinManager.pin(file.driveFileId)
            result.onSuccess {
                statusMessage = if (file.pinnedPath != null) "Offline pin removed." else "File pinned in app-private storage."
                refreshDashboard()
            }.onFailure { statusMessage = it.message ?: "Could not update the offline pin." }
        }
    }

    fun moveSelectedFile() {
        val file = movingFile ?: return
        val folderId = selectedFolderId ?: return
        scope.launch {
            app.googleDriveService.moveFile(file.driveFileId, folderId).onSuccess {
                movingFile = null
                statusMessage = "Move completed. Drive metadata sync is queued."
                DriveMetadataSyncWorker.enqueue(context)
                refreshDashboard()
            }.onFailure { statusMessage = "Move failed. No local or Drive file was deleted." }
        }
    }

    fun compareDuplicateHashes(group: DriveDuplicateGroup) {
        val key = duplicateKey(group)
        scope.launch {
            comparingDuplicateKey = key
            statusMessage = "Downloading candidate bytes privately to compare SHA-256. No files will be moved or deleted."
            val files = dao.getDuplicateGroupFiles(group.name, group.sizeBytes)
            val hashes = linkedMapOf<String, String>()
            withContext(Dispatchers.IO) {
                for (file in files) {
                    if (file.contentSha256 != null) {
                        hashes[file.driveFileId] = file.contentSha256
                        continue
                    }
                    if (file.sizeBytes > 50L * 1024L * 1024L) continue
                    val temp = File(context.cacheDir, "duplicate-compare/${file.driveFileId}.bin")
                    temp.parentFile?.mkdirs()
                    try {
                        val downloaded = app.googleDriveService.downloadForIndexing(
                            file.driveFileId, file.mimeType, temp.absolutePath
                        )
                        if (downloaded.isSuccess && temp.isFile) {
                            val digest = sha256File(temp)
                            dao.updateContentSha256(file.driveFileId, digest)
                            hashes[file.driveFileId] = digest
                        }
                    } finally {
                        temp.delete()
                    }
                }
            }
            duplicateHashes = duplicateHashes + (key to hashes)
            comparingDuplicateKey = null
            statusMessage = if (hashes.isEmpty()) {
                "No candidate could be hashed. Large or unsupported files remain unverified."
            } else {
                val sameHashCount = hashes.values.groupingBy { it }.eachCount().values.maxOrNull() ?: 1
                if (sameHashCount > 1) "SHA-256 confirms byte-identical candidates. Review manually; nothing was deleted."
                else "No matching SHA-256 found among downloaded candidates."
            }
        }
    }

    val createBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val output = context.contentResolver.openOutputStream(uri)
                        ?: throw IllegalStateException("Could not open the selected backup destination.")
                    output.use { backupManager.exportTo(it).getOrThrow() }
                }
            }
            statusMessage = result.fold(
                onSuccess = { summary ->
                    if (summary.truncated) "Exported ${summary.fileCount} records. Text or file count was capped for safety."
                    else "Exported ${summary.fileCount} local index records. No tokens or file bytes were included."
                },
                onFailure = { it.message ?: "Backup export failed." }
            )
        }
    }

    val uploadFileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val displayName = context.contentResolver.query(
                        uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
                    )?.use { cursor ->
                        if (cursor.moveToFirst()) cursor.getString(0) else null
                    }?.takeIf { it.isNotBlank() } ?: "drive-upload-${System.currentTimeMillis()}"
                    val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"
                    val stagingDirectory = File(context.cacheDir, "drive-upload-staging").apply { mkdirs() }
                    val stagingFile = File(stagingDirectory, "${UUID.randomUUID()}.upload")
                    try {
                        val input = context.contentResolver.openInputStream(uri)
                            ?: throw IllegalStateException("Could not read the selected file.")
                        input.use { source ->
                            FileOutputStream(stagingFile).use { output ->
                                val buffer = ByteArray(8192)
                                var total = 0L
                                while (true) {
                                    val read = source.read(buffer)
                                    if (read < 0) break
                                    total += read
                                    require(total <= 200L * 1024L * 1024L) { "Upload exceeds the 200 MiB safety limit." }
                                    output.write(buffer, 0, read)
                                }
                                output.flush()
                                output.fd.sync()
                            }
                        }
                        val item = FileItem(
                            path = stagingFile.absolutePath,
                            name = displayName,
                            sizeBytes = stagingFile.length(),
                            lastModified = stagingFile.lastModified(),
                            isDirectory = false,
                            mimeType = mimeType
                        )
                        app.googleDriveService.uploadFile(item).getOrThrow()
                    } finally {
                        stagingFile.delete()
                    }
                }
            }
            result.onSuccess {
                statusMessage = "Upload completed. Drive metadata sync is queued."
                DriveMetadataSyncWorker.enqueue(context)
            }.onFailure { statusMessage = it.message ?: "Upload failed." }
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) {
                    val input = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("Could not open the selected backup.")
                    input.use { backupManager.importFrom(it).getOrThrow() }
                }
            }
            result.onSuccess { summary ->
                DriveMetadataSyncWorker.enqueue(context)
                statusMessage = "Imported ${summary.fileCount} records. Drive reconciliation has been queued."
                refreshDashboard()
            }.onFailure { statusMessage = it.message ?: "Backup import failed." }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Drive Semantic Search", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            if (accountLinked) accountEmail.orEmpty() else "Google Drive not linked",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                },
                actions = {
                    if (accountLinked) {
                        IconButton(onClick = { scope.launch { onGoogleDriveSignOutRequested(); refreshDashboard() } }) {
                            Icon(Icons.Default.Logout, contentDescription = "Sign out")
                        }
                    }
                }
            )
        },
        bottomBar = {
            NavigationBar {
                DriveHomeTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = activeTab == tab,
                        onClick = { activeTab = tab },
                        icon = {
                            Icon(
                                when (tab) {
                                    DriveHomeTab.DASHBOARD -> Icons.Default.Home
                                    DriveHomeTab.SEARCH -> Icons.Default.Search
                                    DriveHomeTab.DUPLICATES -> Icons.Default.ContentCopy
                                    DriveHomeTab.VAULT -> Icons.Default.Lock
                                    DriveHomeTab.OFFLINE -> Icons.Default.OfflinePin
                                    DriveHomeTab.BACKUP -> Icons.Default.Backup
                                },
                                contentDescription = tab.label
                            )
                        },
                        label = { Text(tab.label, maxLines = 1) }
                    )
                }
            }
        }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (!statusMessage.isNullOrBlank()) {
                Card(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp)) {
                    Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text(statusMessage.orEmpty(), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { statusMessage = null }) { Text("Dismiss") }
                    }
                }
            }
            when (activeTab) {
                DriveHomeTab.DASHBOARD -> DashboardTab(
                    accountLinked = accountLinked,
                    accountEmail = accountEmail,
                    indexedCount = indexedCount,
                    contentIndexedCount = contentIndexedCount,
                    pendingContentCount = pendingContentCount,
                    syncState = syncState,
                    embeddingConsent = app.isEmbeddingConsentGranted(),
                    fullContentConsent = app.isFullContentIndexConsentGranted(),
                    autoOcrEnabled = app.isAutoIndexOcrEnabled(),
                    onSignIn = ::requestSignIn,
                    onSync = {
                        DriveMetadataSyncWorker.enqueue(context)
                        statusMessage = "Drive metadata sync queued. It will resume in 50-file batches."
                    },
                    onEmbeddingToggle = { enabled ->
                        if (enabled) showEmbeddingConsentDialog = true
                        else {
                            app.setEmbeddingConsentGranted(false)
                            statusMessage = "Neural consent revoked; stored vectors were scheduled for removal."
                            scope.launch { refreshDashboard() }
                        }
                    },
                    onOcrConsentToggle = { enabled ->
                        if (enabled) showOcrConsentDialog = true
                        else {
                            app.setFullContentIndexConsentGranted(false)
                            statusMessage = "Full-content OCR consent revoked. Previously extracted OCR text is being removed."
                            scope.launch { refreshDashboard() }
                        }
                    },
                    onAutoOcrToggle = {
                        app.setAutoIndexOcrEnabled(it)
                        statusMessage = if (it) "Image OCR indexing enabled." else "Automatic image OCR paused."
                    }
                )
                DriveHomeTab.SEARCH -> SearchTab(
                    accountLinked = accountLinked,
                    onCreateFolder = { showCreateFolderDialog = true },
                    onUploadFile = { uploadFileLauncher.launch("*/*") },
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    typeFilter = searchFilter,
                    onTypeFilterChange = { searchFilter = it },
                    results = searchResults,
                    onPreview = { file ->
                        val link = file.webViewLink
                        if (link.isNullOrBlank()) statusMessage = "No Drive preview link is available."
                        else runCatching { uriHandler.openUri(link) }.onFailure { statusMessage = "Could not open Drive preview." }
                    },
                    onStar = ::starFile,
                    onMove = { openMoveDialog(it) },
                    onSuggestFolder = ::suggestFolder,
                    onPin = ::pinFile
                )
                DriveHomeTab.DUPLICATES -> DuplicatesTab(
                    groups = duplicateGroups,
                    hashResults = duplicateHashes,
                    comparingKey = comparingDuplicateKey,
                    onCompare = ::compareDuplicateHashes
                )
                DriveHomeTab.VAULT -> VaultTab(app)
                DriveHomeTab.OFFLINE -> OfflineTab(pinnedFiles = pinnedFiles, onUnpin = ::pinFile)
                DriveHomeTab.BACKUP -> BackupTab(
                    onExport = { createBackupLauncher.launch("drive-index-backup.json") },
                    onImport = { importBackupLauncher.launch(arrayOf("application/json", "text/json")) }
                )
            }
        }
    }

    if (showEmbeddingConsentDialog) {
        AlertDialog(
            onDismissRequest = { showEmbeddingConsentDialog = false },
            title = { Text("Enable neural embeddings?") },
            text = {
                Text(
                    "This consent would allow indexed text and search queries to be processed by the approved embedding service. " +
                        "No request will be sent until the Android authentication/proxy path is deployed and verified. " +
                        "You can revoke consent at any time; stored vectors will be cleared."
                )
            },
            confirmButton = {
                Button(onClick = {
                    app.setEmbeddingConsentGranted(true)
                    showEmbeddingConsentDialog = false
                    statusMessage = "Consent saved. Neural requests remain disabled until the trusted Android backend path is verified."
                }) { Text("Consent") }
            },
            dismissButton = { TextButton(onClick = { showEmbeddingConsentDialog = false }) { Text("Cancel") } }
        )
    }

    if (showOcrConsentDialog) {
        AlertDialog(
            onDismissRequest = { showOcrConsentDialog = false },
            title = { Text("Allow full-content image OCR?") },
            text = {
                Text(
                    "ML Kit will process selected image files locally on this device. Image bytes and OCR text are not sent to the embedding service by this OCR step. " +
                        "Automatic OCR remains a separate setting and can be turned off later."
                )
            },
            confirmButton = {
                Button(onClick = {
                    app.setFullContentIndexConsentGranted(true)
                    showOcrConsentDialog = false
                    statusMessage = "Full-content OCR consent saved. Enable automatic OCR separately to process images."
                }) { Text("Consent") }
            },
            dismissButton = { TextButton(onClick = { showOcrConsentDialog = false }) { Text("Cancel") } }
        )
    }

    if (showCreateFolderDialog) {
        AlertDialog(
            onDismissRequest = { showCreateFolderDialog = false },
            title = { Text("Create Drive folder") },
            text = {
                OutlinedTextField(
                    value = newFolderName,
                    onValueChange = { newFolderName = it },
                    label = { Text("Folder name") },
                    singleLine = true
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val name = newFolderName.trim()
                        if (name.isBlank()) {
                            statusMessage = "Enter a folder name."
                        } else {
                            scope.launch {
                                app.googleDriveService.createFolder(name).onSuccess {
                                    showCreateFolderDialog = false
                                    newFolderName = ""
                                    statusMessage = "Folder created in Google Drive. Metadata sync is queued."
                                    DriveMetadataSyncWorker.enqueue(context)
                                }.onFailure {
                                    statusMessage = "Could not create the folder. Check Drive write permission."
                                }
                            }
                        }
                    },
                    enabled = newFolderName.trim().isNotBlank()
                ) { Text("Create folder") }
            },
            dismissButton = { TextButton(onClick = { showCreateFolderDialog = false }) { Text("Cancel") } }
        )
    }

    if (movingFile != null) {
        AlertDialog(
            onDismissRequest = { movingFile = null },
            title = { Text("Review move") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Move “${movingFile?.name.orEmpty()}” to an existing Drive folder? Nothing moves until you confirm.")
                    if (moveFolders.isEmpty()) {
                        Text("No indexed Drive folders found. Sync Drive and try again.")
                    } else {
                        LazyColumn(Modifier.height(240.dp)) {
                            items(moveFolders, key = { it.driveFileId }) { folder ->
                                Row(
                                    Modifier.fillMaxWidth().clickable { selectedFolderId = folder.driveFileId }.padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    RadioButton(selected = selectedFolderId == folder.driveFileId, onClick = { selectedFolderId = folder.driveFileId })
                                    Icon(Icons.Default.Folder, contentDescription = null)
                                    Spacer(Modifier.width(8.dp))
                                    Text(folder.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(onClick = ::moveSelectedFile, enabled = selectedFolderId != null && moveFolders.isNotEmpty()) {
                    Text("Confirm move")
                }
            },
            dismissButton = { TextButton(onClick = { movingFile = null }) { Text("Cancel") } }
        )
    }
}

@Composable
private fun DashboardTab(
    accountLinked: Boolean,
    accountEmail: String?,
    indexedCount: Int,
    contentIndexedCount: Int,
    pendingContentCount: Int,
    syncState: com.vvf.smartmanager.core.database.model.DriveSyncStateEntity?,
    embeddingConsent: Boolean,
    fullContentConsent: Boolean,
    autoOcrEnabled: Boolean,
    onSignIn: () -> Unit,
    onSync: () -> Unit,
    onEmbeddingToggle: (Boolean) -> Unit,
    onOcrConsentToggle: (Boolean) -> Unit,
    onAutoOcrToggle: (Boolean) -> Unit
 ) {
    val lastSyncLabel = syncState?.lastSyncAtMs?.let { formatTime(it) }
    LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Google Drive connection", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (accountLinked) "Account linked: ${accountEmail.orEmpty()}" else "Choose a Google account to connect your own Drive.")
                    if (!accountLinked) {
                        Button(onClick = onSignIn, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Cloud, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Sign in with Google")
                        }
                    } else {
                        OutlinedButton(onClick = onSync, modifier = Modifier.fillMaxWidth()) {
                            Icon(Icons.Default.Refresh, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("Sync Drive now")
                        }
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Index status", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Drive metadata records: $indexedCount")
                    Text("Files with extracted text: $contentIndexedCount")
                    Text("Text extraction pending/retrying: $pendingContentCount")
                    Text(
                        when {
                            syncState?.indexingInProgress == true -> "Drive metadata sync is in progress."
                            lastSyncLabel != null -> "Last metadata sync: $lastSyncLabel"
                            else -> "No completed Drive sync yet."
                        }
                    )
                    if (syncState?.listingIncomplete == true) {
                        Text("The Drive listing may be incomplete (20,000-file safety cap or backup reconciliation).", color = MaterialTheme.colorScheme.error)
                    }
                    if (!syncState?.lastError.isNullOrBlank()) Text(syncState?.lastError.orEmpty(), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Neural embeddings", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (embeddingConsent) "Consent recorded." else "Off until you explicitly consent.")
                    Text("Neural requests remain disabled until the trusted Android worker/proxy authentication path is deployed and verified.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Embedding consent", modifier = Modifier.weight(1f))
                        Switch(checked = embeddingConsent, onCheckedChange = onEmbeddingToggle)
                    }
                }
            }
        }
        item {
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Full-content image OCR", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text(if (fullContentConsent) "Full-content OCR consent recorded." else "Images are not OCR-indexed by default.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Full-content consent", modifier = Modifier.weight(1f))
                        Switch(checked = fullContentConsent, onCheckedChange = onOcrConsentToggle)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Automatic image OCR", modifier = Modifier.weight(1f))
                        Switch(checked = autoOcrEnabled, enabled = fullContentConsent, onCheckedChange = onAutoOcrToggle)
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchTab(
    accountLinked: Boolean,
    onCreateFolder: () -> Unit,
    onUploadFile: () -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    typeFilter: DriveSearchTypeFilter,
    onTypeFilterChange: (DriveSearchTypeFilter) -> Unit,
    results: List<DriveRankedResult>,
    onPreview: (DriveIndexFileEntity) -> Unit,
    onStar: (DriveIndexFileEntity) -> Unit,
    onMove: (DriveIndexFileEntity) -> Unit,
    onSuggestFolder: (DriveIndexFileEntity) -> Unit,
    onPin: (DriveIndexFileEntity) -> Unit
) {
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = onCreateFolder, enabled = accountLinked, modifier = Modifier.weight(1f)) {
                Icon(Icons.Default.Folder, contentDescription = null)
                Spacer(Modifier.width(6.dp))
                Text("New folder")
            }
            Button(onClick = onUploadFile, enabled = accountLinked, modifier = Modifier.weight(1f)) {
                Text("Upload file")
            }
        }
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Search Drive by words or extracted text") },
            singleLine = true,
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
        )
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            DriveSearchTypeFilter.entries.forEach { filter ->
                FilterChip(selected = typeFilter == filter, onClick = { onTypeFilterChange(filter) }, label = { Text(filter.labelForUi()) })
            }
        }
        Text(
            if (query.isBlank()) "Search is local-first. Extracted text and metadata are used even when neural embeddings are off."
            else "${results.size} matching local result(s).",
            style = MaterialTheme.typography.bodySmall
        )
        if (results.isEmpty() && query.isNotBlank()) {
            Box(Modifier.fillMaxWidth().weight(1f), contentAlignment = Alignment.Center) {
                Text("No indexed matches yet. Sync Drive and let local text extraction finish.")
            }
        } else {
            LazyColumn(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(results, key = { it.file.driveFileId }) { result ->
                    SearchResultCard(
                        result = result,
                        onPreview = { onPreview(result.file) },
                        onStar = { onStar(result.file) },
                        onMove = { onMove(result.file) },
                        onSuggestFolder = { onSuggestFolder(result.file) },
                        onPin = { onPin(result.file) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchResultCard(
    result: DriveRankedResult,
    onPreview: () -> Unit,
    onStar: () -> Unit,
    onMove: () -> Unit,
    onSuggestFolder: () -> Unit,
    onPin: () -> Unit
) {
    val file = result.file
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(file.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            Text("${file.mimeType} • ${formatSize(file.sizeBytes)} • ${file.indexStatus}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (file.extractedText.isNotBlank()) {
                Text(file.extractedText.take(180), style = MaterialTheme.typography.bodySmall, maxLines = 3, overflow = TextOverflow.Ellipsis)
            } else {
                Text("Metadata match; local text extraction is not available yet.", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onPreview, enabled = !file.webViewLink.isNullOrBlank()) {
                    Icon(Icons.Default.OpenInNew, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp)); Text("Preview")
                }
                TextButton(onClick = onStar) {
                    Icon(if (file.starred) Icons.Default.Star else Icons.Default.StarBorder, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp)); Text(if (file.starred) "Unstar" else "Star")
                }
                TextButton(onClick = onPin) {
                    Icon(Icons.Default.PushPin, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(3.dp)); Text(if (file.pinnedPath != null) "Unpin" else "Pin offline")
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onMove) { Text("Move…") }
                TextButton(onClick = onSuggestFolder) { Text("Suggest folder") }
            }
        }
    }
}

@Composable
private fun DuplicatesTab(
    groups: List<DriveDuplicateGroup>,
    hashResults: Map<String, Map<String, String>>,
    comparingKey: String?,
    onCompare: (DriveDuplicateGroup) -> Unit
) {
    if (groups.isEmpty()) {
        Box(Modifier.fillMaxSize().padding(20.dp), contentAlignment = Alignment.Center) {
            Text("No same-name, same-size duplicate candidates found in the indexed Drive metadata.")
        }
    } else {
        LazyColumn(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Text("Candidates are grouped by name and size. SHA-256 is computed only after you choose to download candidate bytes or pin a file. Nothing is deleted automatically.", style = MaterialTheme.typography.bodySmall)
            }
            items(groups, key = { duplicateKey(it) }) { group ->
                val key = duplicateKey(group)
                val hashes = hashResults[key].orEmpty()
                val identicalCount = hashes.values.groupingBy { it }.eachCount().values.maxOrNull() ?: 1
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(group.name, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                        Text("${group.count} candidates • ${formatSize(group.sizeBytes)} each")
                        if (hashes.isNotEmpty()) {
                            Text(if (identicalCount > 1) "SHA-256 confirms identical bytes among $identicalCount candidates." else "Downloaded candidate hashes differ.")
                            hashes.values.distinct().take(3).forEach { hash -> Text(hash.take(20) + "…", style = MaterialTheme.typography.labelSmall) }
                        } else Text("Not confirmed duplicates yet.", style = MaterialTheme.typography.bodySmall)
                        Button(onClick = { onCompare(group) }, enabled = comparingKey != key, modifier = Modifier.fillMaxWidth()) {
                            if (comparingKey == key) CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            else Text("Compare SHA-256")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun OfflineTab(pinnedFiles: List<DriveIndexFileEntity>, onUnpin: (DriveIndexFileEntity) -> Unit) {
    val actualBytes = pinnedFiles.sumOf { record -> record.pinnedPath?.let { File(it).takeIf(File::isFile)?.length() } ?: 0L }
    Column(Modifier.fillMaxSize().padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${pinnedFiles.size}/${OfflinePinManager.MAX_PINNED_FILES} pinned files • ${formatSize(actualBytes)}/${formatSize(OfflinePinManager.MAX_PINNED_BYTES)}")
        Text("Oldest pins are evicted automatically to stay within the offline budget.", style = MaterialTheme.typography.bodySmall)
        if (pinnedFiles.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No files pinned for offline use.") }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(pinnedFiles, key = { it.driveFileId }) { file ->
                    Card(Modifier.fillMaxWidth()) {
                        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(file.name, fontWeight = FontWeight.SemiBold)
                                Text(formatSize(file.pinnedPath?.let { File(it).takeIf(File::isFile)?.length() } ?: file.sizeBytes), style = MaterialTheme.typography.labelSmall)
                                Text(
                                    when {
                                        file.indexStatus == "REMOTE_REMOVED" -> "Pinned locally; no longer present in this Drive account."
                                        file.pinnedModifiedTimeMs != file.modifiedTimeMs -> "Pinned copy is older than the current Drive version."
                                        else -> "Available in app-private offline storage."
                                    },
                                    style = MaterialTheme.typography.bodySmall
                                )
                            }
                            TextButton(onClick = { onUnpin(file) }) { Text("Unpin") }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupTab(onExport: () -> Unit, onImport: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Local index backup", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        Text("Export/import uses JSON containing indexed metadata and extracted text. It never exports OAuth tokens, Firebase ID tokens, API keys, vector blobs, pinned paths or file bytes.")
        Button(onClick = onExport, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Backup, contentDescription = null); Spacer(Modifier.width(8.dp)); Text("Export index JSON")
        }
        OutlinedButton(onClick = onImport, modifier = Modifier.fillMaxWidth()) { Text("Import index JSON") }
        Text("Imports are bound to the same Google account and trigger a full Drive reconciliation.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun VaultTab(app: VVFApplication) {
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
    VaultScreen(viewModel = vaultViewModel)
}

private fun bestExistingFolder(file: DriveIndexFileEntity, folders: List<DriveIndexFileEntity>): DriveIndexFileEntity? {
    val fileTokens = file.name.substringBeforeLast('.', file.name).lowercase()
        .split(Regex("[^\\p{L}\\p{Nd}]+")).filter { it.length >= 3 }.toSet()
    if (fileTokens.isEmpty()) return null
    return folders.mapNotNull { folder ->
        val folderTokens = folder.name.lowercase().split(Regex("[^\\p{L}\\p{Nd}]+")).filter { it.length >= 3 }.toSet()
        val score = fileTokens.intersect(folderTokens).size.toDouble() / fileTokens.size
        folder.takeIf { score >= 0.30 }?.let { it to score }
    }.maxByOrNull { it.second }?.first
}

private fun duplicateKey(group: DriveDuplicateGroup): String = "${group.name}|${group.sizeBytes}"

private fun DriveSearchTypeFilter.labelForUi(): String = when (this) {
    DriveSearchTypeFilter.ALL -> "All"
    DriveSearchTypeFilter.DOCUMENTS -> "Documents"
    DriveSearchTypeFilter.SPREADSHEETS -> "Spreadsheets"
    DriveSearchTypeFilter.PRESENTATIONS -> "Presentations"
    DriveSearchTypeFilter.IMAGES -> "Images"
}

private fun formatSize(bytes: Long): String = when {
    bytes < 1024L -> "$bytes B"
    bytes < 1024L * 1024L -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> String.format(Locale.US, "%.1f MB", bytes / (1024.0 * 1024.0))
}

private fun formatTime(timestamp: Long): String =
    SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(timestamp))

private fun sha256File(file: File): String {
    val digest = MessageDigest.getInstance("SHA-256")
    FileInputStream(file).use { input ->
        val buffer = ByteArray(8192)
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            digest.update(buffer, 0, read)
        }
    }
    return digest.digest().joinToString("") { byte -> "%02x".format(byte) }
}
