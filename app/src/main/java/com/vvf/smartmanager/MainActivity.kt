package com.vvf.smartmanager

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveAuth
import com.vvf.smartmanager.core.data.permission.StoragePermissionGate
import kotlinx.coroutines.launch
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import com.vvf.smartmanager.R
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.foundation.Image
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vvf.smartmanager.feature.cleaner.CleanerScreen
import com.vvf.smartmanager.feature.cleaner.CleanerViewModel
import com.vvf.smartmanager.feature.cloud.CloudRoute
import com.vvf.smartmanager.feature.cloud.CloudViewModel
import com.vvf.smartmanager.feature.explorer.ExplorerScreen
import com.vvf.smartmanager.feature.explorer.ExplorerViewModel
import com.vvf.smartmanager.feature.plugins.PluginsScreen
import com.vvf.smartmanager.feature.plugins.PluginsViewModel
import com.vvf.smartmanager.feature.search.SearchScreen
import com.vvf.smartmanager.feature.search.SearchViewModel
import com.vvf.smartmanager.feature.settings.SettingsScreen
import com.vvf.smartmanager.feature.vault.VaultScreen
import com.vvf.smartmanager.feature.vault.VaultViewModel
import com.vvf.smartmanager.ui.navigation.TopLevelDestination
import com.vvf.smartmanager.ui.theme.BhagwaOrange
import com.vvf.smartmanager.ui.theme.CosmicBlue
import com.vvf.smartmanager.ui.theme.VVFSmartManagerTheme

/** Single-Activity Entry Point for VVF Smart Manager. */
class MainActivity : FragmentActivity() {
    private lateinit var googleDriveAuth: GoogleDriveAuth

    /**
     * Activity-scoped OAuth completion handler (not Application-held), so config changes
     * do not leave a stale Application lambda. On success we always apply the token to
     * [VVFApplication.googleDriveService] even if the UI callback was cleared.
     */
    private var pendingGoogleDriveSignInCallback: ((Result<String>) -> Unit)? = null

    private val exportDriveIndexLauncher = registerForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) lifecycleScope.launch {
            DriveIndexBackupManager(this@MainActivity).export(uri).fold(
                onSuccess = { count ->
                    Toast.makeText(this@MainActivity, "Exported $count Drive index entries", Toast.LENGTH_LONG).show()
                },
                onFailure = { error ->
                    Toast.makeText(this@MainActivity, "Index export failed: ${error.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private val importDriveIndexLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) lifecycleScope.launch {
            DriveIndexBackupManager(this@MainActivity).import(uri).fold(
                onSuccess = { count ->
                    Toast.makeText(this@MainActivity, "Imported $count local index entries. Run Drive sync to reconcile.", Toast.LENGTH_LONG).show()
                },
                onFailure = { error ->
                    Toast.makeText(this@MainActivity, "Index import failed: ${error.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
                }
            )
        }
    }

    private val googleDriveSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        val app = application as VVFApplication
        val callback = pendingGoogleDriveSignInCallback
        pendingGoogleDriveSignInCallback = null
        app.pendingGoogleDriveSignInCallback = null
        lifecycleScope.launch {
            val result = googleDriveAuth.handleSignInActivityResult(
                resultCode = activityResult.resultCode,
                data = activityResult.data
            )
            val token = result.getOrNull()
            if (token != null) {
                app.googleDriveService.setAccessToken(token)
                val accountEmail = googleDriveAuth.getMatchingAccountEmail().getOrNull()
                if (!accountEmail.isNullOrBlank()) {
                    try {
                        app.prepareDriveIndexForAccount(accountEmail)
                        app.enqueueDriveIndexing()
                    } catch (_: Exception) {
                        // Authentication remains valid; a later manual sync can retry index setup.
                    }
                }
            }
            callback?.invoke(result)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        googleDriveAuth = GoogleDriveAuth(
            this,
            BuildConfig.GOOGLE_WEB_CLIENT_ID,
            (application as VVFApplication).googleDriveService
        )
        enableEdgeToEdge()
        setContent {
            VVFSmartManagerTheme {
                VVFAppContent(
                    onGoogleDriveSignInRequested = { callback ->
                        if (
                            BuildConfig.GOOGLE_WEB_CLIENT_ID.isBlank() ||
                            BuildConfig.GOOGLE_WEB_CLIENT_ID == "__UNCONFIGURED__"
                        ) {
                            callback(
                                Result.failure(
                                    IllegalStateException(
                                        "Google Drive is not configured. Add GOOGLE_WEB_CLIENT_ID through the app secret configuration."
                                    )
                                )
                            )
                        } else {
                            pendingGoogleDriveSignInCallback = callback
                            googleDriveSignInLauncher.launch(googleDriveAuth.buildDriveSignInIntent())
                        }
                    },
                    onGoogleDriveSignOutRequested = {
                        val app = application as VVFApplication
                        lifecycleScope.launch {
                            googleDriveAuth.signOut()
                            runCatching { app.clearDriveIndexOnSignOut() }
                            Toast.makeText(this@MainActivity, "Google Drive disconnected and local Drive index cleared", Toast.LENGTH_SHORT).show()
                        }
                    },
                    onExportDriveIndexRequested = {
                        exportDriveIndexLauncher.launch("drive-semantic-search-index.json")
                    },
                    onImportDriveIndexRequested = {
                        importDriveIndexLauncher.launch(arrayOf("application/json", "text/json"))
                    }
                )
            }
        }
    }
}

@Composable
fun VVFAppContent(
    onGoogleDriveSignInRequested: ((Result<String>) -> Unit) -> Unit,
    onGoogleDriveSignOutRequested: () -> Unit = {},
    onExportDriveIndexRequested: () -> Unit = {},
    onImportDriveIndexRequested: () -> Unit = {}
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val app = context.applicationContext as VVFApplication

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: TopLevelDestination.EXPLORER.route

    val destinations = listOf(
        TopLevelDestination.EXPLORER,
        TopLevelDestination.VAULT,
        TopLevelDestination.CLEANER,
        TopLevelDestination.SEARCH,
        TopLevelDestination.CLOUD
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize().testTag("vvf_main_container")) {
        val isWideScreen = maxWidth >= 600.dp

        if (isWideScreen) {
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                    header = {
                        Image(
                            painter = painterResource(id = R.drawable.ic_vvf_foundation_logo),
                            contentDescription = "Vishva Vijayaa Foundation",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier.padding(vertical = 12.dp).size(52.dp)
                        )
                    },
                    modifier = Modifier.fillMaxHeight().testTag("tablet_nav_rail")
                ) {
                    destinations.forEach { destination ->
                        val isSelected = currentRoute == destination.route
                        NavigationRailItem(
                            selected = isSelected,
                            alwaysShowLabel = true,
                            onClick = {
                                if (currentRoute != destination.route) {
                                    try { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Exception) {}
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                }
                            },
                            icon = {
                                Icon(
                                    imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                    contentDescription = destination.title,
                                    tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                )
                            },
                            label = {
                                Text(
                                    text = destination.title,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                )
                            },
                            colors = NavigationRailItemDefaults.colors(
                                indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                selectedIconColor = MaterialTheme.colorScheme.primary,
                                selectedTextColor = MaterialTheme.colorScheme.primary,
                                unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            ),
                            modifier = Modifier.testTag(destination.testTag)
                        )
                    }
                }
                VVFNavHost(navController = navController, app = app, onGoogleDriveSignInRequested = onGoogleDriveSignInRequested, onGoogleDriveSignOutRequested = onGoogleDriveSignOutRequested, onExportDriveIndexRequested = onExportDriveIndexRequested, onImportDriveIndexRequested = onImportDriveIndexRequested, modifier = Modifier.fillMaxSize())
            }
        } else {
            Scaffold(
                modifier = Modifier.fillMaxSize().testTag("vvf_main_scaffold"),
                bottomBar = {
                    NavigationBar(
                        containerColor = MaterialTheme.colorScheme.surface,
                        contentColor = MaterialTheme.colorScheme.onSurface,
                        tonalElevation = 6.dp,
                        modifier = Modifier.testTag("bottom_nav_bar")
                    ) {
                        destinations.forEach { destination ->
                            val isSelected = currentRoute == destination.route
                            NavigationBarItem(
                                selected = isSelected,
                                alwaysShowLabel = true,
                                onClick = {
                                    if (currentRoute != destination.route) {
                                        try { haptic.performHapticFeedback(HapticFeedbackType.LongPress) } catch (_: Exception) {}
                                        navController.navigate(destination.route) {
                                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (isSelected) destination.selectedIcon else destination.unselectedIcon,
                                        contentDescription = destination.title,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                },
                                label = {
                                    Text(
                                        text = destination.title,
                                        maxLines = 1,
                                        softWrap = false,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                                    selectedIconColor = MaterialTheme.colorScheme.primary,
                                    selectedTextColor = MaterialTheme.colorScheme.primary,
                                    unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                                    unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                ),
                                modifier = Modifier.testTag(destination.testTag)
                            )
                        }
                    }
                }
            ) { innerPadding ->
                VVFNavHost(
                    navController = navController,
                    app = app,
                    onGoogleDriveSignInRequested = onGoogleDriveSignInRequested,
                    onGoogleDriveSignOutRequested = onGoogleDriveSignOutRequested,
                    onExportDriveIndexRequested = onExportDriveIndexRequested,
                    onImportDriveIndexRequested = onImportDriveIndexRequested,
                    modifier = Modifier.fillMaxSize().padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun VVFNavHost(
    navController: androidx.navigation.NavHostController,
    app: VVFApplication,
    onGoogleDriveSignInRequested: ((Result<String>) -> Unit) -> Unit,
    onGoogleDriveSignOutRequested: () -> Unit = {},
    onExportDriveIndexRequested: () -> Unit = {},
    onImportDriveIndexRequested: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.EXPLORER.route,
        enterTransition = { fadeIn(animationSpec = tween(250)) + slideInHorizontally(animationSpec = tween(250), initialOffsetX = { it / 4 }) },
        exitTransition = { fadeOut(animationSpec = tween(200)) + slideOutHorizontally(animationSpec = tween(200), targetOffsetX = { -it / 4 }) },
        popEnterTransition = { fadeIn(animationSpec = tween(250)) + slideInHorizontally(animationSpec = tween(250), initialOffsetX = { -it / 4 }) },
        popExitTransition = { fadeOut(animationSpec = tween(200)) + slideOutHorizontally(animationSpec = tween(200), targetOffsetX = { it / 4 }) },
        modifier = modifier
    ) {
        composable(TopLevelDestination.EXPLORER.route) {
            val explorerViewModel: ExplorerViewModel = viewModel(
                factory = ExplorerViewModel.provideFactory(
                    appContext = app,
                    getDirectoryFilesUseCase = app.getDirectoryFilesUseCase,
                    getCategorizedFilesUseCase = app.getCategorizedFilesUseCase,
                    getStorageOverviewUseCase = app.getStorageOverviewUseCase,
                    fileOperationsUseCase = app.fileOperationsUseCase,
                    recycleBinUseCase = app.recycleBinUseCase,
                    cloudSyncUseCase = app.cloudSyncUseCase,
                    ocrEngine = app.ocrPlugin
                )
            )
            ExplorerScreen(
                viewModel = explorerViewModel,
                onNavigateToSettings = { navController.navigate(TopLevelDestination.SETTINGS.route) },
                onNavigateToPlugins = { navController.navigate(TopLevelDestination.PLUGINS.route) },
                onStorageAccessGranted = { app.backgroundSyncManager.triggerImmediateIndexing() }
            )
        }
        composable(TopLevelDestination.VAULT.route) {
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
        composable(TopLevelDestination.CLEANER.route) {
            val cleanerViewModel: CleanerViewModel = viewModel(
                factory = CleanerViewModel.provideFactory(
                    duplicateCleanerUseCase = app.duplicateCleanerUseCase,
                    junkCleanerUseCase = app.junkCleanerUseCase,
                    aiIntelligenceUseCase = app.aiIntelligenceUseCase,
                    canScanPrimaryStorage = { StoragePermissionGate(app).evaluate().canBrowsePrimaryTree }
                )
            )
            CleanerScreen(viewModel = cleanerViewModel)
        }
        composable(TopLevelDestination.SEARCH.route) {
            val searchViewModel: SearchViewModel = viewModel(
                factory = SearchViewModel.provideFactory(
                    searchFilesUseCase = app.searchFilesUseCase,
                    searchHistoryUseCase = app.searchHistoryUseCase,
                    tagManagementUseCase = app.tagManagementUseCase,
                    fileOperationsUseCase = app.fileOperationsUseCase,
                    searchIndexManagementUseCase = app.searchIndexManagementUseCase,
                    semanticSearchUseCase = app.semanticSearchUseCase,
                    aiIntelligenceUseCase = app.aiIntelligenceUseCase
                )
            )
            SearchScreen(
                viewModel = searchViewModel,
                onOpenFile = { item ->
                    val driveUrl = item.canonicalUri?.takeIf { raw ->
                        runCatching {
                            val parsed = Uri.parse(raw)
                            parsed.scheme == "https" && parsed.host in setOf("drive.google.com", "docs.google.com")
                        }.getOrDefault(false)
                    } ?: item.localFileId?.let { id ->
                        Uri.parse("https://drive.google.com/open").buildUpon()
                            .appendQueryParameter("id", id)
                            .build()
                            .toString()
                    }
                    if (item.path.startsWith("gdrive://") && !driveUrl.isNullOrBlank()) {
                        runCatching {
                            app.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(driveUrl)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                        }.onFailure {
                            Toast.makeText(app, "No app available to open this Drive file", Toast.LENGTH_SHORT).show()
                        }
                    } else {
                        Toast.makeText(app, "This result is not a Drive link", Toast.LENGTH_SHORT).show()
                    }
                }
            )
        }
        composable(TopLevelDestination.CLOUD.route) {
            val cloudViewModel: CloudViewModel = viewModel(
                factory = CloudViewModel.provideFactory(
                    cloudSyncUseCase = app.cloudSyncUseCase,
                    googleDriveService = app.googleDriveService
                )
            )
            CloudRoute(
                viewModel = cloudViewModel,
                onGoogleDriveSignInRequested = {
                    onGoogleDriveSignInRequested { accessTokenResult ->
                        cloudViewModel.completeGoogleDriveSignIn(accessTokenResult)
                    }
                },
                onDriveIndexRequested = {
                    app.enqueueDriveIndexing()
                    Toast.makeText(app, "Drive search indexing scheduled", Toast.LENGTH_SHORT).show()
                },
                onGoogleDriveSignOutRequested = onGoogleDriveSignOutRequested,
                onExportDriveIndexRequested = onExportDriveIndexRequested,
                onImportDriveIndexRequested = onImportDriveIndexRequested
            )
        }
        composable(TopLevelDestination.PLUGINS.route) {
            val pluginsViewModel: PluginsViewModel = viewModel(
                factory = PluginsViewModel.provideFactory(
                    ocrPlugin = app.ocrPlugin,
                    extractTextUseCase = app.extractTextUseCase,
                    indexOcrTextUseCase = app.indexOcrTextUseCase,
                    saveOcrTextUseCase = app.saveOcrTextUseCase
                )
            )
            PluginsScreen(viewModel = pluginsViewModel, onNavigateBack = { navController.popBackStack() })
        }
        composable(TopLevelDestination.SETTINGS.route) {
            SettingsScreen(
                initialAutoIndexOcr = app.isAutoIndexOcrEnabled(),
                onAutoIndexOcrChange = { enabled -> app.setAutoIndexOcrEnabled(enabled) },
                initialDriveFullContentConsent = app.isDriveFullContentConsentEnabled(),
                onDriveFullContentConsentChange = { enabled -> app.setDriveFullContentConsentEnabled(enabled) },
                initialOfflineOnlyMode = app.isOfflineOnlyModeEnabled(),
                onOfflineOnlyModeChange = { enabled -> app.setOfflineOnlyModeEnabled(enabled) },
                initialEmbeddingConsent = app.isEmbeddingConsentEnabled(),
                onEmbeddingConsentChange = { enabled -> app.setEmbeddingConsentEnabled(enabled) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
