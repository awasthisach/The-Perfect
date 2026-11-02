package com.vvf.smartmanager.di

import android.content.Context
import android.util.Log
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
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    @Provides
    @Singleton
    fun provideCryptoSecurityManager(@ApplicationContext context: Context): CryptoSecurityManager =
        CryptoSecurityManager(context)

    @Provides
    @Singleton
    @VaultDirectory
    fun provideVaultDirectory(@ApplicationContext context: Context): File =
        File(context.filesDir, "vvf_vault_encrypted")

    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context,
        cryptoSecurityManager: CryptoSecurityManager,
        @VaultDirectory vaultDirectory: File
    ): VVFDatabase {
        val jvmUnitTest = CryptoSecurityManager.isJvmUnitTestEnvironment(context)
        // Field injection runs inside Application.super.onCreate(). No Room instance or DAO
        // may be built before cleanup and interrupted-restore recovery have completed.
        AppStartupRecovery.prepare(context, cryptoSecurityManager, vaultDirectory, jvmUnitTest)
        if (jvmUnitTest) {
            Log.i("VVFApplication", "JVM unit-test environment: using in-memory Room database")
            return VVFDatabase.buildInMemoryDatabase(context)
        }

        System.loadLibrary("sqlcipher")
        val passphrase = cryptoSecurityManager.getOrCreateDatabasePassphrase()
        try {
            // The builder owns a copy for SQLCipher's lazy open; wipe the caller's buffer below.
            return VVFDatabase.buildEncryptedDatabase(context, passphrase)
        } catch (e: Exception) {
            throw IllegalStateException(
                "Secure database initialization failed: Failed to construct encrypted SQLCipher database",
                e
            )
        } finally {
            cryptoSecurityManager.wipeBuffer(passphrase)
        }
    }

    @Provides
    @Singleton
    fun provideFileDao(database: VVFDatabase): FileDao = database.fileDao()

    @Provides
    @Singleton
    fun provideSearchFtsDao(database: VVFDatabase): SearchFtsDao = database.searchFtsDao()

    @Provides
    @Singleton
    fun provideVaultDao(database: VVFDatabase): VaultDao = database.vaultDao()

    @Provides
    @Singleton
    fun provideVaultJournalDao(database: VVFDatabase): VaultJournalDao = database.vaultJournalDao()

    @Provides
    @Singleton
    fun provideStorageManager(
        @ApplicationContext context: Context,
        fileDao: FileDao
    ): StorageManager = StorageManager(context, fileDao)
}
