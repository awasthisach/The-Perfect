package com.vvf.smartmanager

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveAuth
import com.vvf.smartmanager.ui.navigation.VVFNavHost
import com.vvf.smartmanager.ui.navigation.VVFDestinations
import com.vvf.smartmanager.ui.theme.BhagwaOrange
import com.vvf.smartmanager.ui.theme.CosmicBlue
import com.vvf.smartmanager.ui.theme.VVFSmartManagerTheme
import kotlinx.coroutines.launch
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector

/** Single-Activity Entry Point for VVF Smart Manager. */
class MainActivity : FragmentActivity() {
    private lateinit var googleDriveAuth: GoogleDriveAuth

    /**
     * Activity-scoped OAuth completion handler (not Application-held), so config changes
     * do not leave a stale Application lambda. On success we always apply the token to
     * [VVFApplication.googleDriveService] even if the UI callback was cleared.
     */
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
        val app = application as VVFApplication
        googleDriveAuth = GoogleDriveAuth(this, BuildConfig.GOOGLE_WEB_CLIENT_ID, app.googleDriveService)
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
                    }
                )
            }
        }
    }
}

@Composable
private fun VVFAppContent(
    onGoogleDriveSignInRequested: ((Result<String>) -> Unit) -> Unit
) {
    val app = androidx.compose.ui.platform.LocalContext.current.applicationContext as VVFApplication
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            VVFBottomBar(
                currentRoute = currentRoute,
                onNavigate = { route ->
                    navController.navigate(route) {
                        popUpTo(navController.graph.findStartDestination().id) {
                            saveState = true
                        }
                        launchSingleTop = true
                        restoreState = true
                    }
                }
            )
        }
    ) { innerPadding ->
        VVFNavHost(
            navController = navController,
            app = app,
            onGoogleDriveSignInRequested = onGoogleDriveSignInRequested,
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        )
    }
}

private data class BottomDest(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun VVFBottomBar(
    currentRoute: String?,
    onNavigate: (String) -> Unit
) {
    val items = listOf(
        BottomDest(VVFDestinations.HOME, "Home", Icons.Filled.Home),
        BottomDest(VVFDestinations.EXPLORER, "Files", Icons.AutoMirrored.Filled.List),
        BottomDest(VVFDestinations.VAULT, "Vault", Icons.Filled.Lock),
        BottomDest(VVFDestinations.SEARCH, "Search", Icons.Filled.Search),
        BottomDest(VVFDestinations.CLOUD, "Cloud", Icons.Filled.Cloud),
        BottomDest(VVFDestinations.SETTINGS, "Settings", Icons.Filled.Settings),
    )
    NavigationBar {
        items.forEach { dest ->
            val selected = currentRoute == dest.route ||
                (dest.route == VVFDestinations.HOME && currentRoute == null)
            NavigationBarItem(
                selected = selected,
                onClick = { onNavigate(dest.route) },
                icon = { Icon(dest.icon, contentDescription = dest.label) },
                label = { Text(dest.label) },
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = BhagwaOrange,
                    selectedTextColor = BhagwaOrange,
                    indicatorColor = CosmicBlue.copy(alpha = 0.25f)
                )
            )
        }
    }
}
