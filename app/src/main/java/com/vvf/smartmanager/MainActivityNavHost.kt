package com.vvf.smartmanager

import androidx.biometric.BiometricManager
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.vvf.smartmanager.core.data.permission.StoragePermissionGate
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
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun VVFNavHost(
    navController: androidx.navigation.NavHostController,
    app: VVFApplication,
    onGoogleDriveSignInRequested: ((Result<String>) -> Unit) -> Unit,
    onFirebaseIdentityChange: suspend (Boolean) -> Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
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
                onStorageAccessGranted = {
                    if (app.isAutoIndexOcrEnabled()) {
                        app.backgroundSyncManager.triggerImmediateIndexingThenOcr()
                    } else {
                        app.backgroundSyncManager.triggerImmediateIndexing()
                    }
                }
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
                onRequestSignIn = onGoogleDriveSignInRequested
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
            val firebaseAvailable = com.google.firebase.FirebaseApp.getApps(context).isNotEmpty()
            val firebaseIdentityConnected = firebaseAvailable &&
                FirebaseAuth.getInstance().currentUser != null
            SettingsScreen(
                initialFirebaseIdentityAvailable = firebaseAvailable,
                initialFirebaseIdentityConnected = firebaseIdentityConnected,
                onFirebaseIdentityChange = onFirebaseIdentityChange,
                initialBiometricEnabled = app.vaultAuthUseCase.isBiometricEnabled(),
                onBiometricEnabledChange = { enabled, pin ->
                    if (!enabled) {
                        app.vaultAuthUseCase.setBiometricEnabled(false)
                        true
                    } else {
                        val strongBiometricAvailable =
                            BiometricManager.from(context).canAuthenticate(
                                BiometricManager.Authenticators.BIOMETRIC_STRONG
                            ) == BiometricManager.BIOMETRIC_SUCCESS
                        val vaultConfigured = app.vaultAuthUseCase.isVaultConfigured()
                        val enrollmentAuthorized = strongBiometricAvailable && vaultConfigured &&
                            pin != null && withContext(Dispatchers.IO) {
                                app.vaultAuthUseCase.verifyPin(pin) &&
                                    app.vaultAuthUseCase.createVaultBiometricCipher() != null
                            }
                        if (canEnableBiometricVault(strongBiometricAvailable, enrollmentAuthorized)) {
                            app.vaultAuthUseCase.setBiometricEnabled(true)
                            true
                        } else {
                            false
                        }
                    }
                },
                initialAutoIndexOcr = app.isAutoIndexOcrEnabled(),
                onAutoIndexOcrChange = { enabled ->
                    app.setAutoIndexOcrEnabled(enabled)
                    if (enabled) {
                        coroutineScope.launch {
                            app.backgroundSyncManager.triggerImmediateOcrBatch()
                        }
                    } else {
                        app.backgroundSyncManager.cancelOcrBatchWork()
                    }
                },
                initialOfflineOnlyMode = app.isOfflineOnlyModeEnabled(),
                onOfflineOnlyModeChange = { enabled -> app.setOfflineOnlyModeEnabled(enabled) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}

/** Strong biometric + vault PIN enrollment gate. */
internal fun canEnableBiometricVault(
    strongBiometricAvailable: Boolean,
    enrollmentAuthorized: Boolean
): Boolean = strongBiometricAvailable && enrollmentAuthorized
