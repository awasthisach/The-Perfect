package com.vvf.smartmanager.core.cloud.gdrive

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
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
 * 2. [buildDriveSignInIntent] + [handleSignInActivityResult] — Drive access token
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

    /**
     * Handles the Activity result from [buildDriveSignInIntent].
     * Always inspects [data] for [ApiException] status codes so DEVELOPER_ERROR (10)
     * is not misreported as a user cancel.
     */
    suspend fun handleSignInActivityResult(resultCode: Int, data: Intent?): Result<String> =
        withContext(Dispatchers.IO) {
            // Prefer parsing the intent payload — Google often returns status here even when
            // resultCode is RESULT_CANCELED (e.g. DEVELOPER_ERROR / code 10).
            if (data != null) {
                try {
                    val account = GoogleSignIn.getSignedInAccountFromIntent(data).await()
                    return@withContext accessTokenForAccount(account.account)
                } catch (e: ApiException) {
                    return@withContext Result.failure(IllegalStateException(mapApiException(e), e))
                } catch (e: Exception) {
                    // Fall through to resultCode handling
                    if (resultCode == Activity.RESULT_OK) {
                        return@withContext Result.failure(e)
                    }
                }
            }

            if (resultCode != Activity.RESULT_OK) {
                return@withContext Result.failure(
                    IllegalStateException(
                        "Google sign-in did not complete (result=$resultCode). " +
                            "If you selected an account, this is often OAuth misconfiguration " +
                            "(package com.vvf.smartmanager + signing SHA-1 must be registered " +
                            "as an Android OAuth client in the same Google Cloud project as the web client ID)."
                    )
                )
            }

            extractAccessTokenFromSignInResult(data)
        }

    suspend fun extractAccessTokenFromSignInResult(data: Intent?): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val account = GoogleSignIn.getSignedInAccountFromIntent(data).await()
                accessTokenForAccount(account.account)
            } catch (e: ApiException) {
                Result.failure(IllegalStateException(mapApiException(e), e))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    private fun accessTokenForAccount(acct: android.accounts.Account?): Result<String> {
        if (acct == null) {
            return Result.failure(IllegalStateException("No Google account on sign-in result"))
        }
        return try {
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
