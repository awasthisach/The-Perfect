package com.vvf.smartmanager.feature.vault

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.vvf.smartmanager.feature.vault.components.VaultAuthScreen

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
            createCipher = { viewModel.createBiometricUnlockCipher() },
            onSuccess = { viewModel.onBiometricAuthSuccess() },
            onError = { viewModel.onBiometricAuthError(it) }
        )
    }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
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
