package com.vvf.smartmanager

import android.app.Activity
import android.content.Intent
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.biometric.BiometricManager
import androidx.lifecycle.lifecycleScope
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveAuth
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
import com.vvf.smartmanager.core.data.permission.StoragePermissionGate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
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

    private val googleDriveAuthorizationLauncher = registerForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult()
    ) { activityResult ->
        val result = if (activityResult.resultCode == Activity.RESULT_OK) {
            googleDriveAuth.handleAuthorizationActivityResult(activityResult.data)
        } else {
            Result.failure(IllegalStateException("Google Drive authorization was cancelled."))
        }
        completeGoogleDriveAuthorization(result)
    }

    private fun completeGoogleDriveAuthorization(result: Result<String>) {
        val app = application as VVFApplication
        val callback = pendingGoogleDriveSignInCallback
        pendingGoogleDriveSignInCallback = null
        app.pendingGoogleDriveSignInCallback = null
        result.onSuccess { token ->
            runCatching { app.googleDriveService.setAccessToken(token) }
        }
        callback?.invoke(result)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as VVFApplication
        googleDriveAuth = GoogleDriveAuth(
            context = this,
            serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID,
            networkAllowed = { !app.isOfflineOnlyModeEnabled() }
        )
        enableEdgeToEdge()
        setContent {
            VVFSmartManagerTheme {
                VVFAppContent(
                    onFirebaseIdentityChange = { connect ->
                        val firebaseAuth = FirebaseAuth.getInstance()
                        if (connect) {
                            // Exchange the Google ID token for a Firebase session. It is not
                            // a Firebase ID token and is never used as a Drive access token.
                            val googleIdToken = googleDriveAuth.requestGoogleIdToken().getOrThrow()
                            firebaseAuth.signInWithCredential(
                                GoogleAuthProvider.getCredential(googleIdToken, null)
                            ).await()
                            firebaseAuth.currentUser != null
                        } else {
                            firebaseAuth.signOut()
                            true
                        }
                    },
                    onGoogleDriveSignInRequested = { callback ->
                        val app = application as VVFApplication
                        pendingGoogleDriveSignInCallback = callback
                        app.pendingGoogleDriveSignInCallback = callback
                        lifecycleScope.launch {
                            try {
                                val authorization = googleDriveAuth.authorizeDriveAccess().await()
                                if (authorization.hasResolution()) {
                                    val pendingIntent = authorization.pendingIntent
                                        ?: throw IllegalStateException("Google Drive authorization did not provide a consent action.")
                                    googleDriveAuthorizationLauncher.launch(
                                        IntentSenderRequest.Builder(pendingIntent.intentSender).build()
                                    )
                                } else {
                                    completeGoogleDriveAuthorization(
                                        googleDriveAuth.accessTokenFromAuthorizationResult(authorization)
                                    )
                                }
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                completeGoogleDriveAuthorization(Result.failure(error))
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun VVFAppContent(
    onGoogleDriveSignInRequested: ((Result<String>) -> Unit) -> Unit,
    onFirebaseIdentityChange: suspend (Boolean) -> Boolean
) {
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val app = context.applicationContext as VVFApplication
    val startupState by app.startupReadiness.state.collectAsState()

    if (startupState != StartupState.Ready) {
        Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
            Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                when (val state = startupState) {
                    StartupState.Initializing -> {
                        CircularProgressIndicator()
                        Text("Preparing secure storage…", modifier = Modifier.padding(top = 16.dp))
                    }
                    StartupState.Recovering -> {
                        CircularProgressIndicator()
                        Text("Recovering vault and search metadata…", modifier = Modifier.padding(top = 16.dp))
                    }
                    is StartupState.Failed -> {
                        Text(
                            "Secure startup failed. Vault, search, and indexing remain disabled.",
                            color = MaterialTheme.colorScheme.error
                        )
                        Text(state.reason, modifier = Modifier.padding(top = 8.dp))
                    }
                    StartupState.Ready -> Unit
                }
            }
        }
        return
    }

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
                VVFNavHost(navController = navController, app = app, onGoogleDriveSignInRequested = onGoogleDriveSignInRequested, onFirebaseIdentityChange = onFirebaseIdentityChange, modifier = Modifier.fillMaxSize())
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
                    onFirebaseIdentityChange = onFirebaseIdentityChange,
                    modifier = Modifier.fillMaxSize().padding(innerPadding)
                )
            }
        }
    }
}
