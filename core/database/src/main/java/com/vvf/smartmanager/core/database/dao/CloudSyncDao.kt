package com.vvf.smartmanager.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vvf.smartmanager.core.database.model.CloudSyncEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface CloudSyncDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(record: CloudSyncEntity): Long

    @Update
    suspend fun update(record: CloudSyncEntity)

    @Query("SELECT * FROM cloud_sync_records WHERE localPath = :localPath AND provider = :provider LIMIT 1")
    suspend fun getRecord(localPath: String, provider: String): CloudSyncEntity?

    @Query("SELECT * FROM cloud_sync_records WHERE provider = :provider ORDER BY lastSyncedAt DESC")
    fun getRecordsByProvider(provider: String): Flow<List<CloudSyncEntity>>

    @Query("SELECT * FROM cloud_sync_records WHERE status = :status")
    suspend fun getRecordsByStatus(status: String): List<CloudSyncEntity>

    @Query("DELETE FROM cloud_sync_records WHERE localPath = :localPath")
    suspend fun deleteByLocalPath(localPath: String)
}
