package com.vvf.smartmanager.core.cloud.gdrive

import android.content.Context

fun interface DriveAccessTokenProvider {
    suspend fun getAccessToken(): String
    suspend fun revokeAccess() = Unit
}

class GoogleAuthorizationDriveAccessTokenProvider(
    context: Context
) : DriveAccessTokenProvider {
    private val driveAuth = GoogleDriveAuth(context, serverClientId = "")

    override suspend fun getAccessToken(): String = driveAuth.requestDriveAccessTokenSilently()
    override suspend fun revokeAccess() {
        driveAuth.revokeDriveAccess().getOrThrow()
    }
}
