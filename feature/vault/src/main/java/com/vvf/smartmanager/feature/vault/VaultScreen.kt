package com.vvf.smartmanager.feature.vault

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.ViewList
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vvf.smartmanager.core.common.FormatUtils
import com.vvf.smartmanager.feature.vault.components.BhagwaOrange
import com.vvf.smartmanager.feature.vault.components.CosmicBlue
import com.vvf.smartmanager.feature.vault.components.SoftGold
import com.vvf.smartmanager.feature.vault.components.VaultAddFileDialog
import com.vvf.smartmanager.feature.vault.components.VaultAuthScreen
import com.vvf.smartmanager.feature.vault.components.VaultCategoryFilterChips
import com.vvf.smartmanager.feature.vault.components.VaultChangePinDialog
import com.vvf.smartmanager.feature.vault.components.VaultConfirmDeleteDialog
import com.vvf.smartmanager.feature.vault.components.VaultEmptyState
import com.vvf.smartmanager.feature.vault.components.EmeraldGreen
import com.vvf.smartmanager.feature.vault.components.VaultItemCard
import com.vvf.smartmanager.feature.vault.components.VaultItemDetailDialog
import com.vvf.smartmanager.feature.vault.components.VaultItemGridCard
import com.vvf.smartmanager.feature.vault.components.VaultProcessingDialog
import com.vvf.smartmanager.feature.vault.components.VaultSettingsDialog
import com.vvf.smartmanager.feature.vault.components.VaultSetupDecoyDialog
import java.io.File

@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current

    val activity = remember(context) {
        context as? androidx.fragment.app.FragmentActivity
            ?: (context as? android.content.ContextWrapper)?.baseContext as? androidx.fragment.app.FragmentActivity
    }

    val triggerBiometricPrompt = {
        launchVaultBiometricUnlock(
            activity = activity,
            isBiometricEnabled = uiState.isBiometricEnabled,
            onSuccess = { viewModel.onBiometricAuthSuccess() },
            onError = { viewModel.onBiometricAuthError(it) }
        )
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_STOP) {
                viewModel.onAppBackgrounded()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    if (!uiState.isUnlocked) {
        VaultAuthScreen(
            state = uiState,
            onDigitClick = { viewModel.onPinDigit(it) },
            onBackspaceClick = { viewModel.onPinBackspace() },
            onClearClick = { viewModel.onPinClear() },
            onSubmitClick = { viewModel.submitPin() },
            onBiometricClick = { triggerBiometricPrompt() },
            modifier = modifier
        )
    } else {
        VaultUnlockedContent(
            uiState = uiState,
            viewModel = viewModel,
            snackbarHostState = snackbarHostState,
            modifier = modifier
        )
    }
}
