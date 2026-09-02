package com.vvf.smartmanager

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
import com.vvf.smartmanager.feature.cloud.CloudScreen
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

/**
 * Single-Activity Entry Point for VVF Smart Manager.
 */
class MainActivity : FragmentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VVFSmartManagerTheme {
                VVFAppContent()
            }
        }
    }
}

@Composable
fun VVFAppContent() {
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
                            modifier = Modifier
                                .padding(vertical = 12.dp)
                                .size(52.dp)
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
                                    try {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    } catch (_: Exception) {}
                                    navController.navigate(destination.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
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

                VVFNavHost(
                    navController = navController,
                    app = app,
                    modifier = Modifier.fillMaxSize()
                )
            }
        } else {
            Scaffold(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("vvf_main_scaffold"),
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
                                        try {
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        } catch (_: Exception) {}
                                        navController.navigate(destination.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                )
            }
        }
    }
}

@Composable
private fun VVFNavHost(
    navController: androidx.navigation.NavHostController,
    app: VVFApplication,
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
                    getDirectoryFilesUseCase = app.getDirectoryFilesUseCase,
                    getCategorizedFilesUseCase = app.getCategorizedFilesUseCase,
                    getStorageOverviewUseCase = app.getStorageOverviewUseCase,
                    fileOperationsUseCase = app.fileOperationsUseCase,
                    recycleBinUseCase = app.recycleBinUseCase,
                    cloudSyncUseCase = app.cloudSyncUseCase
                )
            )
            ExplorerScreen(
                viewModel = explorerViewModel,
                onNavigateToSettings = { navController.navigate(TopLevelDestination.SETTINGS.route) },
                onNavigateToPlugins = { navController.navigate(TopLevelDestination.PLUGINS.route) }
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
                    aiIntelligenceUseCase = app.aiIntelligenceUseCase
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
                    semanticSearchUseCase = app.semanticSearchUseCase,
                    aiIntelligenceUseCase = app.aiIntelligenceUseCase
                )
            )
            SearchScreen(viewModel = searchViewModel)
        }
        composable(TopLevelDestination.CLOUD.route) {
            val cloudViewModel: CloudViewModel = viewModel(
                factory = CloudViewModel.provideFactory(
                    cloudSyncUseCase = app.cloudSyncUseCase
                )
            )
            CloudScreen(viewModel = cloudViewModel)
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
            PluginsScreen(
                viewModel = pluginsViewModel,
                onNavigateBack = { navController.popBackStack() }
            )
        }
        composable(TopLevelDestination.SETTINGS.route) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
