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

/**
 * Single-Activity Entry Point for VVF Smart Manager.
 */
class MainActivity : FragmentActivity() {
    private lateinit var googleDriveAuth: GoogleDriveAuth

    /**
     * Activity Result callback survives configuration changes by storing the pending
     * continuation on [VVFApplication] (process-scoped), not on this Activity instance.
     * The launcher is always re-registered in the new Activity; the Application holds the callback.
     */
    private val googleDriveSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { activityResult ->
        val app = application as VVFApplication
        val callback = app.pendingGoogleDriveSignInCallback ?: return@registerForActivityResult
        app.pendingGoogleDriveSignInCallback = null
        if (activityResult.resultCode != Activity.RESULT_OK) {
            callback(Result.failure(IllegalStateException("Google sign-in was cancelled or did not complete.")))
        } else {
            lifecycleScope.launch {
                callback(googleDriveAuth.extractAccessTokenFromSignInResult(activityResult.data))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        googleDriveAuth = GoogleDriveAuth(this, BuildConfig.GOOGLE_WEB_CLIENT_ID)
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
                            (application as VVFApplication).pendingGoogleDriveSignInCallback = callback
                            googleDriveSignInLauncher.launch(googleDriveAuth.buildDriveSignInIntent())
                        }
                    }
                )
            }
        }
    }
}
