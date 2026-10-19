package com.vvf.smartmanager.di

import android.content.Context
import com.vvf.smartmanager.core.data.FileManagerRepository
import com.vvf.smartmanager.core.data.SearchRepository
import com.vvf.smartmanager.core.data.permission.StoragePermissionGate
import com.vvf.smartmanager.core.data.repository.OfflineFileManagerRepository
import com.vvf.smartmanager.core.data.repository.OfflineSearchRepository
import com.vvf.smartmanager.core.data.repository.SecureVaultRepository
import com.vvf.smartmanager.core.data.storage.StorageManager
import com.vvf.smartmanager.core.database.VVFDatabase
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
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides @Singleton
    fun crypto(@ApplicationContext ctx: Context) = CryptoSecurityManager(ctx)

    @Provides @Singleton
    fun db(@ApplicationContext ctx: Context, crypto: CryptoSecurityManager): VVFDatabase {
        if (CryptoSecurityManager.isJvmUnitTestEnvironment(ctx)) {
            return VVFDatabase.buildInMemoryDatabase(ctx)
        }
        System.loadLibrary("sqlcipher")
        val pass = crypto.getOrCreateDatabasePassphrase()
        return try {
            VVFDatabase.buildEncryptedDatabase(ctx, pass)
        } finally {
            crypto.wipeBuffer(pass)
        }
    }

    @Provides @Singleton fun fileDao(db: VVFDatabase): FileDao = db.fileDao()
    @Provides @Singleton fun searchFtsDao(db: VVFDatabase): SearchFtsDao = db.searchFtsDao()
    @Provides @Singleton fun vaultDao(db: VVFDatabase): VaultDao = db.vaultDao()
    @Provides @Singleton fun vaultJournalDao(db: VVFDatabase): VaultJournalDao = db.vaultJournalDao()

    @Provides @Singleton
    fun storageManager(@ApplicationContext ctx: Context, fileDao: FileDao) =
        StorageManager(ctx, fileDao)

    @Provides @Singleton
    fun storagePermissionGate(@ApplicationContext ctx: Context) = StoragePermissionGate(ctx)

    @Provides @Singleton
    fun fileManagerRepo(
        storage: StorageManager, fileDao: FileDao, searchFtsDao: SearchFtsDao, gate: StoragePermissionGate
    ): FileManagerRepository = OfflineFileManagerRepository(storage, fileDao, searchFtsDao, gate)

    @Provides @Singleton
    fun searchRepo(
        @ApplicationContext ctx: Context, searchFtsDao: SearchFtsDao, fileDao: FileDao,
        storage: StorageManager, crypto: CryptoSecurityManager
    ): SearchRepository = OfflineSearchRepository(ctx, searchFtsDao, fileDao, storage, crypto)

    @Provides @Singleton @Named("vaultDir")
    fun vaultDir(@ApplicationContext ctx: Context) = File(ctx.filesDir, "vvf_vault_encrypted")

    @Provides @Singleton
    fun vaultRepo(
        vaultDao: VaultDao, crypto: CryptoSecurityManager,
        @Named("vaultDir") dir: File, journal: VaultJournalDao
    ) = SecureVaultRepository(vaultDao, crypto, dir, journal)
}
