package com.vvf.smartmanager.core.cloud.gdrive

import android.content.Context
import android.content.Intent
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.auth.api.identity.RevokeAccessRequest
import com.google.android.gms.tasks.Task
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * OAuth / Credential Manager for Google Drive access.
 *
 * Paths:
 * 1. [requestGoogleIdToken] — Credential Manager (OpenID id_token only).
 * 2. [authorizeDriveAccess] — Drive authorization through AuthorizationClient, separate from sign-in.
 */
class GoogleDriveAuth(
    private val context: Context,
    private val serverClientId: String
) {

    private val credentialManager = CredentialManager.create(context)

    suspend fun requestGoogleIdToken(
        filterByAuthorizedAccounts: Boolean = false
    ): Result<String> = withContext(Dispatchers.Main) {
        try {
            val googleIdOption = GetGoogleIdOption.Builder()
                .setFilterByAuthorizedAccounts(filterByAuthorizedAccounts)
                .setServerClientId(serverClientId)
                .setAutoSelectEnabled(false)
                .build()

            val request = GetCredentialRequest.Builder()
                .addCredentialOption(googleIdOption)
                .build()

            val response = credentialManager.getCredential(
                context = context,
                request = request
            )
            val googleId = GoogleIdTokenCredential.createFrom(response.credential.data)
            val token = googleId.idToken
            if (token.isNullOrBlank()) {
                Result.failure(IllegalStateException("Empty Google ID token"))
            } else {
                Result.success(token)
            }
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /**
     * Drive authorization is deliberately separate from identity authentication.
     * A PendingIntent is returned when user consent or account selection is required.
     */
    fun authorizeDriveAccess(): Task<AuthorizationResult> {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
            .build()
        return Identity.getAuthorizationClient(context).authorize(request)
    }

    fun accessTokenFromAuthorizationResult(result: AuthorizationResult): Result<String> {
        if (result.hasResolution()) {
            return Result.failure(
                DriveAuthorizationRequiredException("Google Drive permission needs user approval. Reconnect Drive in the app.")
            )
        }
        if (DRIVE_FILE_SCOPE !in result.grantedScopes.orEmpty()) {
            return Result.failure(SecurityException("Google Drive did not grant the required drive.file scope."))
        }
        val token = result.accessToken
        return if (token.isNullOrBlank()) {
            Result.failure(IllegalStateException("Google Drive authorization returned an empty access token."))
        } else Result.success(token)
    }

    fun handleAuthorizationActivityResult(data: Intent?): Result<String> {
        if (data == null) return Result.failure(IllegalStateException("Google Drive authorization returned no result data."))
        return try {
            accessTokenFromAuthorizationResult(
                Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
            )
        } catch (e: ApiException) {
            Result.failure(IllegalStateException(mapApiException(e), e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** AuthorizationClient owns its short-lived token cache and renews on later requests. */
    suspend fun requestDriveAccessTokenSilently(): String {
        return accessTokenFromAuthorizationResult(authorizeDriveAccess().await()).getOrThrow()
    }

    suspend fun revokeDriveAccess(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            Identity.getAuthorizationClient(context).revokeAccess(
                RevokeAccessRequest.builder().setScopes(listOf(Scope(DRIVE_FILE_SCOPE))).build()
            ).await()
            Result.success(Unit)
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"

        /** Common Google Sign-In [ApiException] status codes → actionable message. */
        fun mapApiException(e: ApiException): String = when (e.statusCode) {
            10 ->
                "Google Sign-In setup error (code 10 DEVELOPER_ERROR). " +
                    "Register package com.vvf.smartmanager and this APK's signing certificate SHA-1 " +
                    "in Google Cloud Console → APIs & Services → Credentials → Android OAuth client " +
                    "(same project as the Web client ID). Debug and release keystores have different SHA-1."
            12501 -> "Google sign-in was cancelled."
            12500 -> "Google sign-in failed (code 12500). Try again or update Google Play Services."
            7 -> "Network error during Google sign-in. Check internet and try again."
            8 -> "Google Play Services internal error. Update Play Services and retry."
            else -> "Google sign-in failed (code ${e.statusCode}): ${e.message ?: e.toString()}"
        }
    }
}
