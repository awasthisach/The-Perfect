package com.vvf.smartmanager.core.cloud.gdrive

import android.content.Context
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

fun interface DriveAccessTokenProvider {
    suspend fun getAccessToken(): String
}

class GoogleAuthorizationDriveAccessTokenProvider(
    context: Context
) : DriveAccessTokenProvider {
    private val authorizationClient = Identity.getAuthorizationClient(context)

    override suspend fun getAccessToken(): String {
        val request = AuthorizationRequest.builder()
            .setRequestedScopes(listOf(Scope(GoogleDriveAuth.DRIVE_FILE_SCOPE)))
            .build()
        val result = authorizationClient.authorize(request).await()
        if (result.hasResolution()) {
            throw DriveAuthorizationRequiredException(
                "Google Drive authorization has expired or needs user approval. Open the app and reconnect Drive."
            )
        }
        if (GoogleDriveAuth.DRIVE_FILE_SCOPE !in result.grantedScopes.orEmpty()) {
            throw SecurityException("Google Drive access token is missing the required drive.file scope.")
        }
        return result.accessToken?.takeIf(String::isNotBlank)
            ?: throw IllegalStateException("Google Drive authorization returned an empty access token.")
    }
}

class DriveAuthorizationRequiredException(message: String) : IllegalStateException(message)
