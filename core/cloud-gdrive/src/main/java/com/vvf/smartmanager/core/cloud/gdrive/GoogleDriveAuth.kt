package com.vvf.smartmanager.core.cloud.gdrive

import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.credentials.ClearCredentialStateRequest
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.Scope
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
 * 2. [authorizeDrive] + [handleSignInActivityResult] — Drive access token
 *    with scope [DRIVE_FILE_SCOPE] for [GoogleDriveServiceImpl.setAccessToken].
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
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    sealed interface DriveAuthorization {
        data class Authorized(val accessToken: String) : DriveAuthorization
        data class NeedsConsent(val pendingIntent: PendingIntent) : DriveAuthorization
    }

    /** Select an account first so authorization uses the same account. */
    suspend fun authorizeDrive(): Result<DriveAuthorization> {
        val identity = requestGoogleIdToken()
        identity.exceptionOrNull()?.let { return Result.failure(it) }
        return try {
            val request = AuthorizationRequest.builder()
                .setRequestedScopes(listOf(Scope(DRIVE_FILE_SCOPE)))
                .build()
            val result = Identity.getAuthorizationClient(context).authorize(request).await()
            if (result.hasResolution()) {
                val pendingIntent = result.pendingIntent
                    ?: return Result.failure(IllegalStateException("Missing Drive authorization resolution"))
                Result.success(DriveAuthorization.NeedsConsent(pendingIntent))
            } else {
                accessTokenResult(result.accessToken).map { DriveAuthorization.Authorized(it) }
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: ApiException) {
            Result.failure(IllegalStateException(mapApiException(e), e))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    /** Parse errors in the result intent even when the activity reports cancellation. */
    fun handleSignInActivityResult(resultCode: Int, data: Intent?): Result<String> {
        if (data != null) {
            try {
                val result = Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)
                return accessTokenResult(result.accessToken)
            } catch (e: ApiException) {
                return Result.failure(IllegalStateException(mapApiException(e), e))
            } catch (e: Exception) {
                if (resultCode == Activity.RESULT_OK) return Result.failure(e)
            }
        }
        return Result.failure(IllegalStateException("Google Drive authorization did not complete (result=$resultCode)."))
    }

    suspend fun signOut() {
        try {
            credentialManager.clearCredentialState(ClearCredentialStateRequest())
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // best-effort
        }
    }

    companion object {
        internal fun accessTokenResult(token: String?): Result<String> =
            if (token.isNullOrBlank()) {
                Result.failure(IllegalStateException("No access token. Ensure Drive scope was granted and Play Services is available."))
            } else {
                Result.success(token)
            }

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
