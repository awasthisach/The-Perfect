package com.vvf.smartmanager.core.cloud.gdrive

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.activity.result.ActivityResultLauncher
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
 * OAuth / Credential Manager skeleton for Google Drive access.
 *
 * Two paths:
 * 1. [requestGoogleIdToken] — Credential Manager account picker (OpenID id_token).
 *    Useful for identifying the user; **not** a Drive access token by itself.
 * 2. [buildDriveSignInIntent] / [extractAccessTokenFromSignInResult] — classic Google Sign-In
 *    with `DRIVE_FILE` scope to obtain a usable access token for [GoogleDriveServiceImpl].
 *
 * Production checklist:
 * - Web client ID (OAuth) in Google Cloud Console → [serverClientId]
 * - Android OAuth client with package name + SHA-1
 * - Prefer scope `https://www.googleapis.com/auth/drive.file` (app-created files only)
 */
class GoogleDriveAuth(
    private val context: Context,
    private val serverClientId: String
) {

    private val credentialManager = CredentialManager.create(context)

    /**
     * Credential Manager: returns Google ID token (JWT), not Drive REST access token.
     */
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

    /**
     * Builds a Google Sign-In intent requesting Drive file scope + email/id.
     * Launch via [ActivityResultLauncher]; then [extractAccessTokenFromSignInResult].
     */
    fun buildDriveSignInIntent(): Intent {
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestEmail()
            .requestIdToken(serverClientId)
            .requestScopes(Scope(DRIVE_FILE_SCOPE))
            .build()
        return GoogleSignIn.getClient(context, gso).signInIntent
    }

    /**
     * After Sign-In activity result, returns an OAuth **access token** suitable for Drive v3 REST.
     */
    suspend fun extractAccessTokenFromSignInResult(data: Intent?): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                val task = GoogleSignIn.getSignedInAccountFromIntent(data)
                val account = task.await()
                val tokenTask = account.account?.let { acct ->
                    // Use GoogleAuthUtil path via silent permission — account must have Drive scope
                    com.google.android.gms.auth.GoogleAuthUtil.getToken(
                        context,
                        acct,
                        "oauth2:$DRIVE_FILE_SCOPE"
                    )
                }
                if (tokenTask.isNullOrBlank()) {
                    Result.failure(
                        IllegalStateException(
                            "No access token. Ensure Drive scope was granted and Google Play Services is available."
                        )
                    )
                } else {
                    Result.success(tokenTask)
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

        /**
         * Helper for Activity: run full connect → token → [GoogleDriveServiceImpl.setAccessToken].
         */
        fun registerSignInLauncher(
            activity: Activity,
            launcher: ActivityResultLauncher<Intent>,
            onIntentReady: (Intent) -> Unit
        ) {
            // Call site owns ActivityResultContracts.StartActivityForResult
            // Example in docs/GOOGLE_DRIVE_SETUP.md
            onIntentReady.invoke(
                // no-op placeholder — prefer instance.buildDriveSignInIntent()
                Intent()
            )
        }
    }
}
