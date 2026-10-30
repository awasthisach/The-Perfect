package com.vvf.smartmanager

import android.app.Activity
import android.content.Intent
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
import androidx.navigation.NavHostController
import com.vvf.smartmanager.feature.cleaner.presentation.CleanerScreen
import com.vvf.smartmanager.feature.cleaner.presentation.CleanerViewModel
import com.vvf.smartmanager.feature.cloud.presentation.CloudRoute
import com.vvf.smartmanager.feature.cloud.presentation.CloudViewModel
import com.vvf.smartmanager.feature.explorer.presentation.ExplorerScreen
import com.vvf.smartmanager.feature.explorer.presentation.ExplorerViewModel
import com.vvf.smartmanager.feature.home.presentation.HomeScreen
import com.vvf.smartmanager.feature.home.presentation.HomeViewModel
import com.vvf.smartmanager.feature.plugins.presentation.PluginsScreen
import com.vvf.smartmanager.feature.plugins.presentation.PluginsViewModel
import com.vvf.smartmanager.feature.search.presentation.SearchScreen
import com.vvf.smartmanager.feature.search.presentation.SearchViewModel
import com.vvf.smartmanager.feature.settings.presentation.SettingsScreen
import com.vvf.smartmanager.feature.vault.presentation.VaultScreen
import com.vvf.smartmanager.feature.vault.presentation.VaultViewModel
import com.vvf.smartmanager.ui.navigation.TopLevelDestination
import com.vvf.smartmanager.ui.theme.VVFSmartManagerTheme

class MainActivity : FragmentActivity() {
    private lateinit var googleDriveAuth: GoogleDriveAuth

    private var pendingGoogleDriveSignInCallback: ((Result<String>) -> Unit)? = null

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
            result.onSuccess { token ->
                runCatching { app.googleDriveService.setAccessToken(token) }
            }
            callback?.invoke(result)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val appCtx = application as VVFApplication
        googleDriveAuth = GoogleDriveAuth(this, BuildConfig.GOOGLE_WEB_CLIENT_ID, appCtx.googleDriveService)
        enableEdgeToEdge()
        setContent {
            VVFSmartManagerTheme {
                val app = LocalContext.current.applicationContext as VVFApplication
                VVFRoot(
                    app = app,
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
                    }
                )
            }
        }
    }
}

@Composable
private fun VVFRoot(
    app: VVFApplication,
    onGoogleDriveSignInRequested: ((Result<String>) -> Unit) -> Unit
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize().testTag("vvf_root_scaffold"),
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { dest ->
                    val selected = currentRoute == dest.route
                    NavigationBarItem(
                        selected = selected,
                        onClick = {
                            navController.navigate(dest.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) }
                    )
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = TopLevelDestination.HOME.route,
            modifier = Modifier.fillMaxSize().padding(padding)
        ) {
            composable(TopLevelDestination.HOME.route) {
                val homeViewModel: HomeViewModel = viewModel(
                    factory = HomeViewModel.provideFactory(
                        getStorageOverviewUseCase = app.getStorageOverviewUseCase,
                        getCategorizedFilesUseCase = app.getCategorizedFilesUseCase
                    )
                )
                HomeScreen(viewModel = homeViewModel)
            }
            composable(TopLevelDestination.EXPLORER.route) {
                val explorerViewModel: ExplorerViewModel = viewModel(
                    factory = ExplorerViewModel.provideFactory(
                        getDirectoryFilesUseCase = app.getDirectoryFilesUseCase,
                        fileOperationsUseCase = app.fileOperationsUseCase,
                        recycleBinUseCase = app.recycleBinUseCase,
                        storagePermissionGate = StoragePermissionGate(app)
                    )
                )
                ExplorerScreen(viewModel = explorerViewModel)
            }
            composable(TopLevelDestination.VAULT.route) {
                val vaultViewModel: VaultViewModel = viewModel(
                    factory = VaultViewModel.provideFactory(
                        vaultAuthUseCase = app.vaultAuthUseCase,
                        getVaultItemsUseCase = app.getVaultItemsUseCase,
                        lockFileInVaultUseCase = app.lockFileInVaultUseCase,
                        restoreVaultItemUseCase = app.restoreVaultItemUseCase,
                        exportVaultItemUseCase = app.exportVaultItemUseCase,
                        deleteVaultItemUseCase = app.deleteVaultItemUseCase
                    )
                )
                VaultScreen(viewModel = vaultViewModel)
            }
            composable(TopLevelDestination.CLEANER.route) {
                val cleanerViewModel: CleanerViewModel = viewModel(
                    factory = CleanerViewModel.provideFactory(
                        duplicateCleanerUseCase = app.duplicateCleanerUseCase,
                        junkCleanerUseCase = app.junkCleanerUseCase
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
                SearchScreen(viewModel = searchViewModel)
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
                    }
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
                    initialOfflineOnlyMode = app.isOfflineOnlyModeEnabled(),
                    onOfflineOnlyModeChange = { enabled -> app.setOfflineOnlyModeEnabled(enabled) },
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
