package com.vvf.smartmanager.core.cloud.gdrive

import android.app.Activity
import android.content.Context
import android.content.Intent
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialException
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.GoogleAuthProvider
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
 *    with scope [DRIVE_SCOPE] for [GoogleDriveServiceImpl.setAccessToken].
 */
class GoogleDriveAuth(
    private val context: Context,
    private val serverClientId: String,
    private val driveService: GoogleDriveService? = null
) {

    private val credentialManager = CredentialManager.create(context)
    private val firebaseAuth: FirebaseAuth by lazy { FirebaseAuth.getInstance() }

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
            .requestScopes(Scope(DRIVE_SCOPE))
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
                    val linked = linkFirebaseAccount(account)
                    if (linked.isFailure) return@withContext Result.failure(linked.exceptionOrNull()!!)
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
                val linked = linkFirebaseAccount(account)
                if (linked.isFailure) Result.failure(linked.exceptionOrNull()!!)
                else accessTokenForAccount(account.account)
            } catch (e: ApiException) {
                Result.failure(IllegalStateException(mapApiException(e), e))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    /** Link Firebase Auth to the exact Google account granted Drive access. */
    private suspend fun linkFirebaseAccount(account: GoogleSignInAccount): Result<Unit> {
        val googleEmail = account.email?.trim()?.lowercase()
            ?: return Result.failure(IllegalStateException("Google did not return an account email"))
        val googleIdToken = account.idToken?.takeIf { it.isNotBlank() }
            ?: return Result.failure(IllegalStateException("Google ID token is missing; Firebase sign-in cannot proceed"))
        return try {
            val credential = GoogleAuthProvider.getCredential(googleIdToken, null)
            val result = firebaseAuth.signInWithCredential(credential).await()
            val firebaseEmail = result.user?.email?.trim()?.lowercase()
            if (firebaseEmail.isNullOrBlank() || firebaseEmail != googleEmail) {
                firebaseAuth.signOut()
                Result.failure(IllegalStateException("Google Drive and Firebase accounts did not match; Firebase session cleared"))
            } else Result.success(Unit)
        } catch (e: Exception) {
            firebaseAuth.signOut()
            Result.failure(IllegalStateException("Firebase sign-in failed; no linked session was kept", e))
        }
    }

    /**
     * Refresh Drive access silently only when both an existing Google session and a matching
     * Firebase session exist. This method never starts interactive account selection.
     */
    suspend fun silentRefreshDriveAccessToken(): Result<String> = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: return@withContext Result.failure(IllegalStateException("No existing Google session; interactive sign-in required"))
        val googleEmail = account.email?.trim()?.lowercase()
        val firebaseEmail = firebaseAuth.currentUser?.email?.trim()?.lowercase()
        if (googleEmail.isNullOrBlank() || firebaseEmail.isNullOrBlank() || googleEmail != firebaseEmail) {
            firebaseAuth.signOut()
            return@withContext Result.failure(IllegalStateException("Google and Firebase sessions do not match; sign in again"))
        }
        if (account.account == null) {
            return@withContext Result.failure(IllegalStateException("Existing Google account has no token account"))
        }
        accessTokenForAccount(account.account)
    }

    /** Returns the verified email shared by the existing Google and Firebase sessions. */
    suspend fun getMatchingAccountEmail(): Result<String> = withContext(Dispatchers.IO) {
        val account = GoogleSignIn.getLastSignedInAccount(context)
            ?: return@withContext Result.failure(IllegalStateException("No existing Google account"))
        val googleEmail = account.email?.trim()?.lowercase()
            ?: return@withContext Result.failure(IllegalStateException("Google account email is unavailable"))
        val firebaseEmail = firebaseAuth.currentUser?.email?.trim()?.lowercase()
        if (firebaseEmail.isNullOrBlank() || firebaseEmail != googleEmail) {
            return@withContext Result.failure(IllegalStateException("Google and Firebase accounts do not match"))
        }
        Result.success(googleEmail)
    }

    /** Firebase ID token for backend calls. Never substitute the Drive access token. */
    suspend fun getFirebaseIdToken(): Result<String> {
        return try {
            val user = firebaseAuth.currentUser
                ?: return Result.failure(IllegalStateException("Firebase session is not linked"))
            val token = user.getIdToken(false).await().token
            if (token.isNullOrBlank()) Result.failure(IllegalStateException("Firebase ID token unavailable"))
            else Result.success(token)
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
                "oauth2:$DRIVE_SCOPE"
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
        // Clear the in-memory Drive bearer before ending either identity session.
        driveService?.setAccessToken(null)
        firebaseAuth.signOut()
        try {
            val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestIdToken(serverClientId)
                .requestScopes(Scope(DRIVE_SCOPE))
                .build()
            GoogleSignIn.getClient(context, gso).signOut().await()
        } catch (_: Exception) {
            // best-effort
        }
    }

    companion object {
        const val DRIVE_SCOPE = "https://www.googleapis.com/auth/drive"

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
