package com.vvf.smartmanager.di

import com.vvf.smartmanager.core.data.FileManagerRepository
import com.vvf.smartmanager.core.data.SearchRepository
import com.vvf.smartmanager.core.data.repository.SecureVaultRepository
import com.vvf.smartmanager.core.domain.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DomainModule {
    @Provides @Singleton fun storageOverview(r: FileManagerRepository) = GetStorageOverviewUseCase(r)
    @Provides @Singleton fun directoryFiles(r: FileManagerRepository) = GetDirectoryFilesUseCase(r)
    @Provides @Singleton fun categorizedFiles(r: FileManagerRepository) = GetCategorizedFilesUseCase(r)
    @Provides @Singleton fun fileOps(r: FileManagerRepository) = FileOperationsUseCase(r)
    @Provides @Singleton fun recycleBin(r: FileManagerRepository) = RecycleBinUseCase(r)
    @Provides @Singleton fun duplicateCleaner(r: FileManagerRepository) = DuplicateCleanerUseCase(r)
    @Provides @Singleton fun junkCleaner(r: FileManagerRepository) = JunkCleanerUseCase(r)
    @Provides @Singleton fun searchFiles(r: SearchRepository) = SearchFilesUseCase(r)
    @Provides @Singleton fun searchHistory(r: SearchRepository) = SearchHistoryUseCase(r)
    @Provides @Singleton fun tagManagement(r: SearchRepository) = TagManagementUseCase(r)
    @Provides @Singleton fun searchIndex(r: SearchRepository) = SearchIndexManagementUseCase(r)
    @Provides @Singleton fun getVaultItems(r: SecureVaultRepository) = GetVaultItemsUseCase(r)
    @Provides @Singleton fun lockVault(r: SecureVaultRepository) = LockFileInVaultUseCase(r)
    @Provides @Singleton fun restoreVault(r: SecureVaultRepository) = RestoreVaultItemUseCase(r)
    @Provides @Singleton fun exportVault(r: SecureVaultRepository) = ExportVaultItemUseCase(r)
    @Provides @Singleton fun deleteVault(r: SecureVaultRepository) = DeleteVaultItemUseCase(r)
    @Provides @Singleton fun vaultAuth(r: SecureVaultRepository) = VaultAuthUseCase(r)
}
