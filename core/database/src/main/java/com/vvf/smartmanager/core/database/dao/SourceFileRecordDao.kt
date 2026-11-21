package com.vvf.smartmanager.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vvf.smartmanager.core.database.model.SourceFileRecordEntity

/**
 * Persistence API for source-aware indexing. Access-state updates preserve indexed history;
 * this DAO intentionally exposes no operation that deletes or moves source files.
 */
@Dao
interface SourceFileRecordDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: SourceFileRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(records: List<SourceFileRecordEntity>)

    @Query("SELECT * FROM source_file_records WHERE identityKey = :identityKey LIMIT 1")
    suspend fun getByIdentityKey(identityKey: String): SourceFileRecordEntity?

    @Query("""
        SELECT * FROM source_file_records
        WHERE sourceType = :sourceType AND sourceInstanceId = :sourceInstanceId
        ORDER BY displayName COLLATE NOCASE ASC
    """)
    suspend fun getBySourceInstance(
        sourceType: String,
        sourceInstanceId: String
    ): List<SourceFileRecordEntity>

    @Query("SELECT * FROM source_file_records ORDER BY sourceType, sourceInstanceId, displayName COLLATE NOCASE")
    suspend fun getAll(): List<SourceFileRecordEntity>

    @Query("UPDATE source_file_records SET accessState = :accessState WHERE identityKey = :identityKey")
    suspend fun updateAccessState(identityKey: String, accessState: String): Int
}
