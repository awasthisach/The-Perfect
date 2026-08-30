package com.vvf.smartmanager.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vvf.smartmanager.core.database.model.VaultItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertVaultItem(item: VaultItemEntity)

    @Update
    suspend fun updateVaultItem(item: VaultItemEntity)

    @Delete
    suspend fun deleteVaultItem(item: VaultItemEntity)

    @Query("SELECT * FROM vault_items WHERE id = :id LIMIT 1")
    suspend fun getVaultItemById(id: String): VaultItemEntity?

    @Query("SELECT * FROM vault_items WHERE encryptedFileName = :encryptedFileName LIMIT 1")
    suspend fun getVaultItemByEncryptedName(encryptedFileName: String): VaultItemEntity?

    @Query("SELECT * FROM vault_items WHERE isDecoy = :isDecoy ORDER BY createdAt DESC")
    fun getAllVaultItems(isDecoy: Boolean = false): Flow<List<VaultItemEntity>>

    @Query("SELECT * FROM vault_items WHERE category = :category AND isDecoy = :isDecoy ORDER BY createdAt DESC")
    fun getVaultItemsByCategory(category: String, isDecoy: Boolean = false): Flow<List<VaultItemEntity>>

    @Query("SELECT COUNT(*) FROM vault_items WHERE isDecoy = :isDecoy")
    fun getVaultItemCount(isDecoy: Boolean = false): Flow<Int>

    @Query("SELECT SUM(sizeBytes) FROM vault_items WHERE isDecoy = :isDecoy")
    fun getVaultTotalBytes(isDecoy: Boolean = false): Flow<Long?>

    @Query("DELETE FROM vault_items WHERE id = :id")
    suspend fun deleteById(id: String)
}
