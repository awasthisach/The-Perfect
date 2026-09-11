package com.vvf.smartmanager.feature.vault

import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.vvf.smartmanager.core.security.VaultBiometricCryptoHelper
import javax.crypto.Cipher

/**
 * Launches BiometricPrompt with an auth-per-use Keystore CryptoObject.
 * [createCipher] is supplied by the caller (typically ViewModel) so this module
 * never depends on :app.
 */
fun launchVaultBiometricUnlock(
    activity: FragmentActivity?,
    isBiometricEnabled: Boolean,
    createCipher: () -> Cipher?,
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
            BiometricManager.BIOMETRIC_ERROR_NO_HARDWARE -> "No strong biometric hardware on device"
            BiometricManager.BIOMETRIC_ERROR_HW_UNAVAILABLE -> "Biometric hardware unavailable"
            BiometricManager.BIOMETRIC_ERROR_NONE_ENROLLED ->
                "No strong biometric enrolled. Add fingerprint/face in device settings."
            BiometricManager.BIOMETRIC_ERROR_SECURITY_UPDATE_REQUIRED ->
                "Device security update required for biometric vault unlock"
            else -> "Strong biometric auth unavailable (code=$canAuth). Use PIN."
        }
        onError(errorMsg)
        return
    }

    val cipher = try {
        createCipher()
    } catch (e: Exception) {
        onError("Vault biometric key unavailable: ${e.message ?: "authentication setup failed"}")
        return
    }

    if (cipher == null) {
        onError("Vault biometric key could not be initialized. Use PIN.")
        return
    }

    val callback = object : BiometricPrompt.AuthenticationCallback() {
        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
            super.onAuthenticationSucceeded(result)
            val authenticatedCipher = result.cryptoObject?.cipher
            if (authenticatedCipher == null || !VaultBiometricCryptoHelper.proveAuthenticatedCipher(authenticatedCipher)) {
                onError("Biometric authentication succeeded but cryptographic proof failed")
                return
            }
            onSuccess()
        }

        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
            super.onAuthenticationError(errorCode, errString)
            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED &&
                errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON
            ) {
                onError("Biometric Error: $errString")
            }
        }

        override fun onAuthenticationFailed() {
            super.onAuthenticationFailed()
            onError("Biometric recognition failed")
        }
    }

    val prompt = BiometricPrompt(activity, executor, callback)
    val promptInfo = BiometricPrompt.PromptInfo.Builder()
        .setTitle("Unlock VVF Secure Vault")
        .setSubtitle("Authenticate with a hardware-backed biometric key")
        .setNegativeButtonText("Use PIN")
        .setAllowedAuthenticators(BiometricManager.Authenticators.BIOMETRIC_STRONG)
        .build()
    prompt.authenticate(promptInfo, BiometricPrompt.CryptoObject(cipher))
}
