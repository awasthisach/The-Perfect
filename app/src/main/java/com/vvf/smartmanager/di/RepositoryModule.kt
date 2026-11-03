package com.vvf.smartmanager.di

import android.content.Context
import com.vvf.smartmanager.core.data.permission.StoragePermissionGate
import com.vvf.smartmanager.core.data.repository.OfflineFileManagerRepository
import com.vvf.smartmanager.core.data.repository.OfflineSearchRepository
import com.vvf.smartmanager.core.data.repository.SecureVaultRepository
import com.vvf.smartmanager.core.data.storage.StorageManager
import com.vvf.smartmanager.core.database.dao.FileDao
import com.vvf.smartmanager.core.database.dao.SearchFtsDao
import com.vvf.smartmanager.core.database.dao.VaultDao
import com.vvf.smartmanager.core.database.dao.VaultJournalDao
import com.vvf.smartmanager.core.security.CryptoSecurityManager
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.io.File
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object RepositoryModule {
    @Provides
    @Singleton
    fun provideStoragePermissionGate(@ApplicationContext context: Context): StoragePermissionGate =
        StoragePermissionGate(context)

    @Provides
    @Singleton
    fun provideFileManagerRepository(
        storageManager: StorageManager,
        fileDao: FileDao,
        searchFtsDao: SearchFtsDao,
        storagePermissionGate: StoragePermissionGate
    ): OfflineFileManagerRepository = OfflineFileManagerRepository(
        storageManager = storageManager,
        fileDao = fileDao,
        searchFtsDao = searchFtsDao,
        storagePermissionGate = storagePermissionGate
    )

    @Provides
    @Singleton
    fun provideVaultRepository(
        vaultDao: VaultDao,
        cryptoSecurityManager: CryptoSecurityManager,
        @VaultDirectory vaultDirectory: File,
        vaultJournalDao: VaultJournalDao
    ): SecureVaultRepository = SecureVaultRepository(
        vaultDao = vaultDao,
        cryptoManager = cryptoSecurityManager,
        vaultDirectory = vaultDirectory,
        vaultJournalDao = vaultJournalDao
    )

    @Provides
    @Singleton
    fun provideSearchRepository(
        @ApplicationContext context: Context,
        searchFtsDao: SearchFtsDao,
        fileDao: FileDao,
        storageManager: StorageManager,
        cryptoSecurityManager: CryptoSecurityManager
    ): OfflineSearchRepository = OfflineSearchRepository(
        context = context,
        searchFtsDao = searchFtsDao,
        fileDao = fileDao,
        storageManager = storageManager,
        cryptoSecurityManager = cryptoSecurityManager
    )
}
