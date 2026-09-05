package com.vvf.smartmanager.feature.vault

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.vvf.smartmanager.VVFApplication
import javax.crypto.Cipher

fun launchVaultBiometricUnlock(
    activity: FragmentActivity?,
    isBiometricEnabled: Boolean,
    onSuccess: () -> Unit,
    onError: (String) -> Unit
) {
    if (!isBiometricEnabled) {
        onError("Biometric authentication is disabled in settings")
        return
    }
    if (activity == null) {
        onError("Activity context unavailable for biometric prompt")
        return
    }
    val executor = ContextCompat.getMainExecutor(activity)
    val biometricManager = BiometricManager.from(activity)
    val canAuth = biometricManager.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
    if (canAuth != BiometricManager.BIOMETRIC_SUCCESS) {
        val errorMsg = when (canAuth) {
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE ->
                "No strong biometric hardware on device"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE ->
                "Biometric hardware unavailable"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "No strong biometric enrolled. Add fingerprint/face in device settings."
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                "Device security update required for biometric vault unlock"
            else -> "Strong biometric auth unavailable (code=$canAuth). Use PIN."
        }
        onError(errorMsg)
        return
    }
    val app = activity.application as? VVFApplication
    val cipher: Cipher? = try {
        app?.vaultAuthUseCase?.createVaultBiometricCipher()
    } catch (e: Exception) {
        onError("Vault key requires authentication setup: ${e.message ?: "unavailable"}")
        return
    }
    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            if (cipher == null || result.cryptoObject?.cipher != null) onSuccess()
            else onError("Biometric succeeded but vault key was not unlocked (missing CryptoObject)")
        }
        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
            ) onError("Biometric Error: $errString")
        }
        override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            onError("Biometric recognition failed")
        }
    }
    val prompt = BiometricPrompt(activity, executor, callback)
    if (cipher != null) {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock VVF Secure Vault")
            .setSubtitle("Authenticate to unlock the hardware-backed vault key")
            .setNegativeButtonText("Use PIN")
            .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
            .build()
        prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
    } else {
        val promptInfo = BiometricPrompt.PromptInfo.Builder()
            .setTitle("Unlock VVF Secure Vault")
            .setSubtitle("Confirm your identity to unlock the vault key")
            .setAllowedAuthenticators(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or
                    BiometricManager.Authenticators.DEVICE_CREDENTIAL
            )
            .build()
        prompt.authenticate(promptInfo)
    }
}
