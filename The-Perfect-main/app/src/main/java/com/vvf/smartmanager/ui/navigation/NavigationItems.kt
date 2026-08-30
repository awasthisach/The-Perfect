package com.vvf.smartmanager.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Widgets
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.ui.graphics.vector.ImageVector

enum class TopLevelDestination(
    val route: String,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val title: String,
    val testTag: String
) {
    EXPLORER(
        route = "explorer",
        selectedIcon = Icons.Filled.Folder,
        unselectedIcon = Icons.Outlined.Folder,
        title = "Files",
        testTag = "nav_item_explorer"
    ),
    VAULT(
        route = "vault",
        selectedIcon = Icons.Filled.Lock,
        unselectedIcon = Icons.Outlined.Lock,
        title = "Vault",
        testTag = "nav_item_vault"
    ),
    CLEANER(
        route = "cleaner",
        selectedIcon = Icons.Filled.CleaningServices,
        unselectedIcon = Icons.Outlined.CleaningServices,
        title = "Cleaner",
        testTag = "nav_item_cleaner"
    ),
    SEARCH(
        route = "search",
        selectedIcon = Icons.Filled.Search,
        unselectedIcon = Icons.Outlined.Search,
        title = "Search",
        testTag = "nav_item_search"
    ),
    CLOUD(
        route = "cloud",
        selectedIcon = Icons.Filled.Cloud,
        unselectedIcon = Icons.Outlined.Cloud,
        title = "Cloud",
        testTag = "nav_item_cloud"
    ),
    PLUGINS(
        route = "plugins",
        selectedIcon = Icons.Filled.Widgets,
        unselectedIcon = Icons.Outlined.Widgets,
        title = "Plugins",
        testTag = "nav_item_plugins"
    ),
    SETTINGS(
        route = "settings",
        selectedIcon = Icons.Filled.Settings,
        unselectedIcon = Icons.Outlined.Settings,
        title = "Settings",
        testTag = "nav_item_settings"
    )
}
