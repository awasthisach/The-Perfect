package com.vvf.smartmanager.di

import android.content.Context
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveService
import com.vvf.smartmanager.core.cloud.gdrive.GoogleDriveServiceImpl
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt foundation module.
 *
 * Phase A: bindings that are safe to introduce while [com.vvf.smartmanager.VVFApplication]
 * still performs manual DI. Expand in Phase B (see docs/HILT_MIGRATION.md).
 */
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /**
     * Placeholder binding for Google Drive. Application currently constructs its own instance;
     * once Phase C migrates consumers to `@Inject`, this becomes the single source of truth.
     */
    @Provides
    @Singleton
    fun provideGoogleDriveService(
        @ApplicationContext context: Context
    ): GoogleDriveService = GoogleDriveServiceImpl(context)
}
