package com.vvf.smartmanager.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vvf.smartmanager.core.database.model.DriveIndexFileEntity
import com.vvf.smartmanager.core.database.model.DriveSyncStateEntity
import kotlinx.coroutines.flow.Flow

data class DriveDuplicateGroup(
    val name: String,
    val sizeBytes: Long,
    val count: Int
)

@Dao
interface DriveIndexDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFile(file: DriveIndexFileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFiles(files: List<DriveIndexFileEntity>): List<Long>

    @Query("SELECT * FROM drive_index_files WHERE driveFileId = :driveFileId LIMIT 1")
    suspend fun getByDriveId(driveFileId: String): DriveIndexFileEntity?

    @Query("SELECT * FROM drive_index_files ORDER BY modifiedTimeMs DESC LIMIT :limit")
    suspend fun getRecentFiles(limit: Int = 200): List<DriveIndexFileEntity>

    @Query("SELECT * FROM drive_index_files WHERE name LIKE '%' || :query || '%' OR extractedText LIKE '%' || :query || '%' ORDER BY modifiedTimeMs DESC LIMIT :limit")
    suspend fun searchLocalText(query: String, limit: Int = 200): List<DriveIndexFileEntity>

    @Query("SELECT * FROM drive_index_files WHERE pinnedPath IS NOT NULL ORDER BY pinnedAtMs DESC")
    fun observePinnedFiles(): Flow<List<DriveIndexFileEntity>>

    @Query("SELECT name, sizeBytes, COUNT(*) AS count FROM drive_index_files WHERE sizeBytes > 0 GROUP BY sizeBytes, name HAVING COUNT(*) > 1 ORDER BY sizeBytes DESC")
    suspend fun findNameAndSizeDuplicateGroups(): List<DriveDuplicateGroup>

    @Query("SELECT * FROM drive_index_files WHERE name = :name AND sizeBytes = :sizeBytes ORDER BY modifiedTimeMs ASC")
    suspend fun getDuplicateGroupFiles(name: String, sizeBytes: Long): List<DriveIndexFileEntity>

    @Query("SELECT COUNT(*) FROM drive_index_files")
    suspend fun getIndexedFileCount(): Int

    @Query("UPDATE drive_index_files SET extractedText = :text, indexStatus = :status, lastIndexedAtMs = :indexedAtMs WHERE driveFileId = :driveFileId")
    suspend fun updateExtractedText(driveFileId: String, text: String, status: String, indexedAtMs: Long)

    @Query("UPDATE drive_index_files SET contentSha256 = :sha256 WHERE driveFileId = :driveFileId")
    suspend fun updateContentSha256(driveFileId: String, sha256: String)

    @Query("UPDATE drive_index_files SET embeddingVector = :vector, embeddingModel = :model, embeddingVersion = :version, embeddingDimension = :dimension WHERE driveFileId = :driveFileId")
    suspend fun updateEmbedding(driveFileId: String, vector: ByteArray, model: String, version: Int, dimension: Int)

    @Query("UPDATE drive_index_files SET embeddingVector = NULL, embeddingModel = NULL, embeddingVersion = NULL, embeddingDimension = NULL")
    suspend fun clearAllEmbeddings()

    @Query("UPDATE drive_index_files SET pinnedPath = :path, pinnedAtMs = :pinnedAtMs WHERE driveFileId = :driveFileId")
    suspend fun markPinned(driveFileId: String, path: String, pinnedAtMs: Long)

    @Query("UPDATE drive_index_files SET pinnedPath = NULL, pinnedAtMs = NULL WHERE driveFileId = :driveFileId")
    suspend fun unpin(driveFileId: String)

    @Query("DELETE FROM drive_index_files WHERE driveFileId = :driveFileId")
    suspend fun removeStaleDriveMetadata(driveFileId: String)

    @Query("SELECT * FROM drive_sync_state WHERE id = 1 LIMIT 1")
    suspend fun getSyncState(): DriveSyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSyncState(state: DriveSyncStateEntity)

}
