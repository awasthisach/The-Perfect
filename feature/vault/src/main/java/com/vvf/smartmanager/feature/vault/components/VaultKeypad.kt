package com.vvf.smartmanager.feature.vault.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vvf.smartmanager.feature.vault.PinSetupStep
import com.vvf.smartmanager.feature.vault.VaultUiState

val BhagwaOrange = Color(0xFFF47B20)
val CosmicBlue = Color(0xFF102B52)
val SoftGold = Color(0xFFD4A95A)
val EmeraldGreen = Color(0xFF3FA34D)

@Composable
fun VaultAuthScreen(
    state: VaultUiState,
    onDigitClick: (Char) -> Unit,
    onBackspaceClick: () -> Unit,
    onClearClick: () -> Unit,
    onSubmitClick: () -> Unit,
    onBiometricClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxSize()
            .testTag("vault_auth_root"),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Branding & Title
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(CircleShape)
                        .background(CosmicBlue)
                        .border(2.dp, SoftGold, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(id = com.vvf.smartmanager.core.common.R.drawable.vvf_foundation_logo),
                        contentDescription = "VVF Logo",
                        modifier = Modifier.size(60.dp)
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = if (state.isSettingUpPin) "Set Up Secure Vault" else "VVF Secure Vault",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.testTag("vault_auth_title")
                )

                Spacer(modifier = Modifier.height(6.dp))

                val subtitle = when {
                    state.isSettingUpPin && state.pinSetupStep == PinSetupStep.ENTER_NEW ->
                        "Create a 4-6 digit Master PIN"
                    state.isSettingUpPin && state.pinSetupStep == PinSetupStep.CONFIRM_NEW ->
                        "Re-enter your Master PIN to confirm"
                    state.isLockedOut ->
                        "Locked out! Try again in ${state.lockoutRemainingSeconds}s"
                    else ->
                        "Enter Master PIN to decrypt and unlock"
                }

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (state.isLockedOut) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                // Error Banner
                AnimatedVisibility(
                    visible = state.errorMessage != null,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Card(
                        modifier = Modifier
                            .padding(top = 12.dp)
                            .fillMaxWidth(0.9f)
                            .testTag("vault_auth_error_card"),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = state.errorMessage ?: "",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                                .fillMaxWidth()
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // PIN Dots Indicator
                PinDotsIndicator(
                    pinLength = state.enteredPin.length,
                    maxDots = 6
                )
            }

            // Keypad
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
            ) {
                VaultKeypad(
                    onDigitClick = onDigitClick,
                    onBackspaceClick = onBackspaceClick,
                    onBiometricClick = onBiometricClick,
                    isBiometricEnabled = state.isBiometricEnabled && !state.isSettingUpPin,
                    isLockedOut = state.isLockedOut || state.isPinVerifying
                )

                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onSubmitClick,
                    enabled = state.enteredPin.length in 4..6 && !state.isLockedOut && !state.isPinVerifying,
                    modifier = Modifier
                        .fillMaxWidth(0.85f)
                        .testTag("vault_submit_pin_btn")
                ) {
                    Text(
                        text = when {
                            state.isPinVerifying -> "Verifying…"
                            state.isSettingUpPin || state.isChangingPin -> "Continue"
                            else -> "Unlock Vault"
                        }
                    )
                }

                if (state.isSettingUpPin) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "🔒 Zero-Knowledge: Keys are generated and stored strictly on-device using Android Keystore AES-256.",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PinDotsIndicator(
    pinLength: Int,
    maxDots: Int = 6,
    modifier: Modifier = Modifier
) {
    Row(
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.fillMaxWidth()
    ) {
        repeat(maxDots) { index ->
            val isFilled = index < pinLength
            Box(
                modifier = Modifier
                    .padding(horizontal = 8.dp)
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(
                        if (isFilled) BhagwaOrange else MaterialTheme.colorScheme.surfaceVariant
                    )
                    .border(
                        width = 1.5.dp,
                        color = if (isFilled) BhagwaOrange else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f),
                        shape = CircleShape
                    )
            )
        }
    }
}

@Composable
fun VaultKeypad(
    onDigitClick: (Char) -> Unit,
    onBackspaceClick: () -> Unit,
    onBiometricClick: () -> Unit,
    isBiometricEnabled: Boolean,
    isLockedOut: Boolean,
    modifier: Modifier = Modifier
) {
    val keys = listOf(
        listOf('1', '2', '3'),
        listOf('4', '5', '6'),
        listOf('7', '8', '9'),
        listOf('B', '0', '<')
    )

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        keys.forEach { row ->
            Row(
                modifier = Modifier.fillMaxWidth(0.85f),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                row.forEach { key ->
                    when (key) {
                        'B' -> {
                            if (isBiometricEnabled) {
                                Box(
                                    modifier = Modifier
                                        .size(68.dp)
                                        .clip(CircleShape)
                                        .background(CosmicBlue.copy(alpha = 0.1f))
                                        .clickable(
                                            enabled = !isLockedOut,
                                            onClick = onBiometricClick
                                        )
                                        .testTag("keypad_biometric_button"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Fingerprint,
                                        contentDescription = "Biometric Unlock",
                                        tint = BhagwaOrange,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(68.dp))
                            }
                        }
                        '<' -> {
                            Box(
                                modifier = Modifier
                                    .size(68.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                                    .clickable(
                                        enabled = !isLockedOut,
                                        onClick = onBackspaceClick
                                    )
                                    .testTag("keypad_backspace_button"),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Backspace,
                                    contentDescription = "Backspace",
                                    tint = MaterialTheme.colorScheme.onSurface,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                        else -> {
                            KeypadNumberButton(
                                digit = key,
                                enabled = !isLockedOut,
                                onClick = { onDigitClick(key) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun KeypadNumberButton(
    digit: Char,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .size(68.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .border(
                1.dp,
                MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f),
                CircleShape
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = ripple(bounded = true, radius = 34.dp),
                onClick = {
                    try {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    } catch (_: Exception) {}
                    onClick()
                }
            )
            .testTag("keypad_digit_$digit"),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = digit.toString(),
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
        )
    }
}
