package com.vvf.smartmanager

import android.content.Context
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveAuth
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ActivityComponent
import dagger.hilt.android.qualifiers.ActivityContext
import dagger.hilt.android.scopes.ActivityScoped

/** Transitional Hilt binding for Activity-owned Google OAuth; the remaining app graph migration is still required. */
@Module
@InstallIn(ActivityComponent::class)
object GoogleDriveAuthModule {
    @Provides
    @ActivityScoped
    fun provideGoogleDriveAuth(
        @ActivityContext context: Context
    ): GoogleDriveAuth = GoogleDriveAuth(
        context = context,
        serverClientId = BuildConfig.GOOGLE_WEB_CLIENT_ID
    )
}
