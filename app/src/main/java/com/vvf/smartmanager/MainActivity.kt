package com.vvf.smartmanager

import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveAuth
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
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
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
import com.vvf.smartmanager.feature.cloud.CloudRoute
import com.vvf.smartmanager.feature.cloud.CloudViewModel
import com.vvf.smartmanager.feature.search.SearchScreen
import com.vvf.smartmanager.feature.search.SearchViewModel
import com.vvf.smartmanager.feature.settings.SettingsScreen
import com.vvf.smartmanager.ui.navigation.TopLevelDestination
import com.vvf.smartmanager.ui.theme.VVFSmartManagerTheme

/** Single-Activity entry point. Drive OAuth results use Application SharedFlow (rotation-safe). */
class MainActivity : FragmentActivity() {
    private lateinit var googleDriveAuth: GoogleDriveAuth

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
            app.emitDriveAuthResult(result)
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

    LaunchedEffect(Unit) {
        app.driveAuthResults.collect { result ->
            result.fold(
                onSuccess = {
                    Toast.makeText(context, "Google Drive connected", Toast.LENGTH_SHORT).show()
                },
                onFailure = { error ->
                    Toast.makeText(
                        context,
                        "Google Drive sign-in failed: ${error.message ?: "unknown"}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            )
        }
    }

    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route ?: TopLevelDestination.SEARCH.route

    val destinations = listOf(
        TopLevelDestination.SEARCH,
        TopLevelDestination.CLOUD,
        TopLevelDestination.OFFLINE,
        TopLevelDestination.SETTINGS
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
                VVFNavHost(
                    navController = navController,
                    app = app,
                    onGoogleDriveSignInRequested = onGoogleDriveSignInRequested,
                    onGoogleDriveSignOutRequested = onGoogleDriveSignOutRequested,
                    onExportDriveIndexRequested = onExportDriveIndexRequested,
                    onImportDriveIndexRequested = onImportDriveIndexRequested,
                    modifier = Modifier.fillMaxSize()
                )
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
    val searchActionScope = rememberCoroutineScope()
    NavHost(
        navController = navController,
        startDestination = TopLevelDestination.SEARCH.route,
        enterTransition = { fadeIn(animationSpec = tween(250)) + slideInHorizontally(animationSpec = tween(250), initialOffsetX = { it / 4 }) },
        exitTransition = { fadeOut(animationSpec = tween(200)) + slideOutHorizontally(animationSpec = tween(200), targetOffsetX = { -it / 4 }) },
        popEnterTransition = { fadeIn(animationSpec = tween(250)) + slideInHorizontally(animationSpec = tween(250), initialOffsetX = { -it / 4 }) },
        popExitTransition = { fadeOut(animationSpec = tween(200)) + slideOutHorizontally(animationSpec = tween(200), targetOffsetX = { it / 4 }) },
        modifier = modifier
    ) {
        composable(TopLevelDestination.SEARCH.route) {
            val searchViewModel: SearchViewModel = viewModel(
                factory = SearchViewModel.factory(app)
            )
            SearchScreen(viewModel = searchViewModel)
        }
        composable(TopLevelDestination.CLOUD.route) {
            val cloudViewModel: CloudViewModel = viewModel(
                factory = CloudViewModel.factory(app)
            )
            CloudRoute(
                viewModel = cloudViewModel,
                onGoogleDriveSignInRequested = onGoogleDriveSignInRequested,
                onGoogleDriveSignOutRequested = onGoogleDriveSignOutRequested
            )
        }
        composable(TopLevelDestination.OFFLINE.route) {
            Text("Offline index tools", modifier = Modifier.padding(16.dp))
        }
        composable(TopLevelDestination.SETTINGS.route) {
            SettingsScreen(
                onExportDriveIndexRequested = onExportDriveIndexRequested,
                onImportDriveIndexRequested = onImportDriveIndexRequested,
                onEmbeddingConsentChange = { enabled -> app.setEmbeddingConsentEnabled(enabled) },
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
