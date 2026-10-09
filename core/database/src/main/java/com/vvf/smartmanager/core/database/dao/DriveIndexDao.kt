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

    @Query("SELECT * FROM drive_index_files WHERE indexStatus = 'METADATA_ONLY' ORDER BY modifiedTimeMs DESC LIMIT :limit")
    suspend fun getFilesNeedingText(limit: Int = 50): List<DriveIndexFileEntity>

    @Query("UPDATE drive_index_files SET indexStatus = :status, lastIndexedAtMs = :indexedAtMs WHERE driveFileId = :driveFileId")
    suspend fun updateIndexStatus(driveFileId: String, status: String, indexedAtMs: Long)

    @Query("UPDATE drive_index_files SET indexStatus = 'METADATA_ONLY' WHERE indexStatus = 'OCR_CONSENT_REQUIRED'")
    suspend fun requeueOcrConsentRequired()

    @Query("UPDATE drive_index_files SET extractedText = '', extractionSource = NULL, indexStatus = 'OCR_CONSENT_REQUIRED', lastIndexedAtMs = 0, embeddingVector = NULL, embeddingModel = NULL, embeddingVersion = NULL, embeddingDimension = NULL WHERE extractionSource = 'OCR'")
    suspend fun clearOcrIndexedText()

    @Query("SELECT * FROM drive_index_files WHERE indexStatus != 'REMOTE_REMOVED' ORDER BY modifiedTimeMs DESC LIMIT :limit")
    suspend fun getRecentFiles(limit: Int = 200): List<DriveIndexFileEntity>

    @Query("SELECT * FROM drive_index_files WHERE indexStatus != 'REMOTE_REMOVED' AND (name LIKE '%' || :query || '%' OR extractedText LIKE '%' || :query || '%') ORDER BY modifiedTimeMs DESC LIMIT :limit")
    suspend fun searchLocalText(query: String, limit: Int = 200): List<DriveIndexFileEntity>

    @Query("SELECT * FROM drive_index_files WHERE pinnedPath IS NOT NULL ORDER BY pinnedAtMs DESC")
    fun observePinnedFiles(): Flow<List<DriveIndexFileEntity>>

    @Query("SELECT name, sizeBytes, COUNT(*) AS count FROM drive_index_files WHERE indexStatus != 'REMOTE_REMOVED' AND sizeBytes > 0 GROUP BY sizeBytes, name HAVING COUNT(*) > 1 ORDER BY sizeBytes DESC")
    suspend fun findNameAndSizeDuplicateGroups(): List<DriveDuplicateGroup>

    @Query("SELECT * FROM drive_index_files WHERE indexStatus != 'REMOTE_REMOVED' AND name = :name AND sizeBytes = :sizeBytes ORDER BY modifiedTimeMs ASC")
    suspend fun getDuplicateGroupFiles(name: String, sizeBytes: Long): List<DriveIndexFileEntity>

    @Query("SELECT COUNT(*) FROM drive_index_files WHERE indexStatus != 'REMOTE_REMOVED'")
    suspend fun getIndexedFileCount(): Int

    @Query("UPDATE drive_index_files SET extractedText = :text, extractionSource = :source, indexStatus = :status, lastIndexedAtMs = :indexedAtMs WHERE driveFileId = :driveFileId")
    suspend fun updateExtractedText(driveFileId: String, text: String, source: String?, status: String, indexedAtMs: Long)

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

    @Query("DELETE FROM drive_index_files WHERE driveFileId = :driveFileId AND pinnedPath IS NULL")
    suspend fun removeUnpinnedStaleDriveMetadata(driveFileId: String)

    @Query("UPDATE drive_index_files SET indexStatus = 'REMOTE_REMOVED' WHERE driveFileId = :driveFileId AND pinnedPath IS NOT NULL")
    suspend fun markPinnedFileAsRemoteRemoved(driveFileId: String)

    @Query("SELECT * FROM drive_sync_state WHERE id = 1 LIMIT 1")
    suspend fun getSyncState(): DriveSyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSyncState(state: DriveSyncStateEntity)

}
