package com.vvf.smartmanager.feature.cloud

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.vvf.smartmanager.core.model.CloudProviderType

/**
 * Entry composable for the Cloud tab: shows Grok-style Google login when Drive
 * is selected and not yet connected; otherwise the full Cloud Manager UI.
 */
@Composable
fun CloudRoute(
    viewModel: CloudViewModel,
    onGoogleDriveSignInRequested: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val googleAccount = uiState.accounts[CloudProviderType.GOOGLE_DRIVE]
    val driveSelected = uiState.selectedProvider == CloudProviderType.GOOGLE_DRIVE
    val driveConnected = googleAccount?.isConnected == true

    if (driveSelected && !driveConnected) {
        GrokStyleGoogleSignInScreen(
            isLoading = uiState.isLoading,
            statusMessage = uiState.statusMessage,
            onContinueWithGoogle = {
                viewModel.beginGoogleDriveSignIn()
                onGoogleDriveSignInRequested()
            },
            onSkipToProviders = {
                viewModel.selectProvider(CloudProviderType.ONE_DRIVE)
            },
            modifier = modifier
        )
        return
    }

    CloudScreen(
        viewModel = viewModel,
        onGoogleDriveSignInRequested = onGoogleDriveSignInRequested,
        modifier = modifier
    )
}
