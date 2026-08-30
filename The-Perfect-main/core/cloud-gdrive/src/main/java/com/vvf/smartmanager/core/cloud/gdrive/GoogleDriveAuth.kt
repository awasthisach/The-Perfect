package com.vvf.smartmanager.core.cloud.gdrive

import android.content.Context
import android.content.Intent
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.Scope
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

/**
 * OAuth / Credential Manager for Google Drive access.
 *
 * Paths:
 * 1. [requestGoogleIdToken] — Credential Manager (OpenID id_token only).
 * 2. [buildDriveSignInIntent] + [extractAccessTokenFromSignInResult] — Drive access token
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
                .setAutoSelectEnabled(true)
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
        } catch (e: GetCredentialException) {
            Result.failure(e)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun buildDriveSignInIntent(): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(serverClientId)
            .requestScopes(Scope(DRIVE_FILE_SCOPE))
            .build()
        return GoogleSignIn.getClient(context, gso).signInIntent
    }

    suspend fun extractAccessTokenFromSignInResult(data: Intent?): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(data).await()
                val acct = account.account
                    ?: return@withContext Result.failure(IllegalStateException("No Google account on result"))
                val token = com.google.android.gms.auth.GoogleAuthUtil.getToken(
                    context,
                    acct,
                    "oauth2:$DRIVE_FILE_SCOPE"
                )
                if (token.isNullOrBlank()) {
                    Result.failure(
                        IllegalStateException(
                            "No access token. Ensure Drive scope was granted and Play Services is available."
                        )
                    )
                } else {
                    Result.success(token)
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    suspend fun signOut() = withContext(Dispatchers.IO) {
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(serverClientId)
                .requestScopes(Scope(DRIVE_FILE_SCOPE))
                .build()
            GoogleSignIn.getClient(context, gso).signOut().await()
        } catch (_: Exception) {
            // best-effort
        }
    }

    companion object {
        const val DRIVE_FILE_SCOPE = "https://www.googleapis.com/auth/drive.file"
    }
}
