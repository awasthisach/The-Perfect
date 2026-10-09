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

data class DriveVectorRecord(
    val driveFileId: String,
    val mimeType: String,
    val embeddingVector: ByteArray?
)

@Dao
interface DriveIndexDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFile(file: DriveIndexFileEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFiles(files: List<DriveIndexFileEntity>): List<Long>

    @Query("SELECT * FROM drive_index_files WHERE driveFileId = :driveFileId LIMIT 1")
    suspend fun getByDriveId(driveFileId: String): DriveIndexFileEntity?

    @Query("SELECT * FROM drive_index_files WHERE indexStatus IN ('METADATA_ONLY', 'DOWNLOAD_FAILED', 'EXTRACTION_FAILED') AND indexAttempts < 3 ORDER BY modifiedTimeMs DESC LIMIT :limit")
    suspend fun getFilesNeedingText(limit: Int = 50): List<DriveIndexFileEntity>

    @Query("UPDATE drive_index_files SET indexStatus = :status, lastIndexedAtMs = :indexedAtMs, indexAttempts = CASE WHEN :status IN ('DOWNLOAD_FAILED', 'EXTRACTION_FAILED') THEN indexAttempts + 1 WHEN :status = 'METADATA_ONLY' THEN 0 ELSE indexAttempts END WHERE driveFileId = :driveFileId")
    suspend fun updateIndexStatus(driveFileId: String, status: String, indexedAtMs: Long)

    @Query("UPDATE drive_index_files SET indexStatus = 'METADATA_ONLY' WHERE indexStatus IN ('OCR_CONSENT_REQUIRED', 'OCR_DISABLED_BY_USER')")
    suspend fun requeueOcrConsentRequired()

    @Query("UPDATE drive_index_files SET extractedText = '', extractionSource = NULL, indexStatus = 'OCR_CONSENT_REQUIRED', lastIndexedAtMs = 0, embeddingVector = NULL, embeddingModel = NULL, embeddingVersion = NULL, embeddingDimension = NULL WHERE extractionSource = 'OCR'")
    suspend fun clearOcrIndexedText()

    @Query("SELECT * FROM drive_index_files WHERE indexStatus != 'REMOTE_REMOVED' ORDER BY modifiedTimeMs DESC LIMIT :limit")
    suspend fun getRecentFiles(limit: Int = 200): List<DriveIndexFileEntity>

    @Query("SELECT * FROM drive_index_files WHERE indexStatus != 'REMOTE_REMOVED' AND (name LIKE '%' || :query || '%' OR extractedText LIKE '%' || :query || '%') ORDER BY modifiedTimeMs DESC LIMIT :limit")
    suspend fun searchLocalText(query: String, limit: Int = 200): List<DriveIndexFileEntity>

    @Query("SELECT * FROM drive_index_files WHERE mimeType = 'application/vnd.google-apps.folder' AND indexStatus != 'REMOTE_REMOVED' ORDER BY name COLLATE NOCASE")
    suspend fun getDriveFolders(): List<DriveIndexFileEntity>

    @Query("UPDATE drive_index_files SET starred = :starred WHERE driveFileId = :driveFileId")
    suspend fun updateStarred(driveFileId: String, starred: Boolean)

    @Query("SELECT * FROM drive_index_files WHERE pinnedPath IS NOT NULL ORDER BY pinnedAtMs DESC")
    fun observePinnedFiles(): Flow<List<DriveIndexFileEntity>>

    @Query("SELECT * FROM drive_index_files WHERE pinnedPath IS NOT NULL ORDER BY pinnedAtMs ASC")
    suspend fun getPinnedFilesOldestFirst(): List<DriveIndexFileEntity>

    @Query("SELECT name, sizeBytes, COUNT(*) AS count FROM drive_index_files WHERE indexStatus != 'REMOTE_REMOVED' AND sizeBytes > 0 GROUP BY sizeBytes, name HAVING COUNT(*) > 1 ORDER BY sizeBytes DESC")
    suspend fun findNameAndSizeDuplicateGroups(): List<DriveDuplicateGroup>

    @Query("SELECT * FROM drive_index_files WHERE indexStatus != 'REMOTE_REMOVED' AND name = :name AND sizeBytes = :sizeBytes ORDER BY modifiedTimeMs ASC")
    suspend fun getDuplicateGroupFiles(name: String, sizeBytes: Long): List<DriveIndexFileEntity>

    @Query("SELECT COUNT(*) FROM drive_index_files WHERE indexStatus != 'REMOTE_REMOVED'")
    suspend fun getIndexedFileCount(): Int

    @Query("SELECT COUNT(*) FROM drive_index_files WHERE indexStatus IN ('TEXT_INDEXED', 'OCR_INDEXED')")
    suspend fun getContentIndexedCount(): Int

    @Query("SELECT COUNT(*) FROM drive_index_files WHERE indexStatus IN ('METADATA_ONLY', 'DOWNLOAD_FAILED', 'EXTRACTION_FAILED') AND indexAttempts < 3")
    suspend fun getPendingContentCount(): Int

    @Query("UPDATE drive_index_files SET extractedText = :text, extractionSource = :source, indexStatus = :status, indexAttempts = 0, lastIndexedAtMs = :indexedAtMs WHERE driveFileId = :driveFileId")
    suspend fun updateExtractedText(driveFileId: String, text: String, source: String?, status: String, indexedAtMs: Long)

    @Query("UPDATE drive_index_files SET contentSha256 = :sha256 WHERE driveFileId = :driveFileId")
    suspend fun updateContentSha256(driveFileId: String, sha256: String)

    @Query("UPDATE drive_index_files SET embeddingVector = :vector, embeddingModel = :model, embeddingVersion = :version, embeddingDimension = :dimension WHERE driveFileId = :driveFileId")
    suspend fun updateEmbeddingVector(driveFileId: String, model: String, version: Int, dimension: Int, vector: ByteArray)

    @Query("SELECT * FROM drive_index_files WHERE extractedText != '' AND indexStatus IN ('TEXT_INDEXED', 'OCR_INDEXED') AND (embeddingVector IS NULL OR embeddingModel != :model OR embeddingVersion != :version OR embeddingDimension != :dimension) ORDER BY modifiedTimeMs DESC LIMIT :limit")
    suspend fun getFilesNeedingEmbeddings(model: String, version: Int, dimension: Int, limit: Int): List<DriveIndexFileEntity>

    @Query("SELECT COUNT(*) FROM drive_index_files WHERE embeddingVector IS NOT NULL AND indexStatus != 'REMOTE_REMOVED'")
    suspend fun getEmbeddingVectorCount(): Int

    @Query("SELECT driveFileId, mimeType, embeddingVector FROM drive_index_files WHERE embeddingVector IS NOT NULL AND indexStatus != 'REMOTE_REMOVED' ORDER BY modifiedTimeMs DESC LIMIT :limit OFFSET :offset")
    suspend fun getEmbeddingVectorsPage(limit: Int, offset: Int): List<DriveVectorRecord>

    @Query("SELECT * FROM drive_index_files WHERE driveFileId IN (:driveFileIds) AND indexStatus != 'REMOTE_REMOVED'")
    suspend fun getFilesByDriveIds(driveFileIds: List<String>): List<DriveIndexFileEntity>

    @Query("UPDATE drive_index_files SET embeddingVector = :vector, embeddingModel = :model, embeddingVersion = :version, embeddingDimension = :dimension WHERE driveFileId = :driveFileId")
    suspend fun updateEmbedding(driveFileId: String, vector: ByteArray, model: String, version: Int, dimension: Int)

    @Query("UPDATE drive_index_files SET embeddingVector = NULL, embeddingModel = NULL, embeddingVersion = NULL, embeddingDimension = NULL")
    suspend fun clearAllEmbeddings()

    @Query("UPDATE drive_index_files SET pinnedPath = :path, pinnedAtMs = :pinnedAtMs, pinnedModifiedTimeMs = :pinnedModifiedTimeMs WHERE driveFileId = :driveFileId")
    suspend fun markPinned(driveFileId: String, path: String, pinnedAtMs: Long, pinnedModifiedTimeMs: Long)

    @Query("UPDATE drive_index_files SET pinnedPath = NULL, pinnedAtMs = NULL, pinnedModifiedTimeMs = NULL WHERE driveFileId = :driveFileId")
    suspend fun unpin(driveFileId: String)

    @Query("DELETE FROM drive_index_files WHERE driveFileId = :driveFileId AND pinnedPath IS NULL")
    suspend fun removeUnpinnedStaleDriveMetadata(driveFileId: String)

    @Query("UPDATE drive_index_files SET indexStatus = 'REMOTE_REMOVED' WHERE driveFileId = :driveFileId AND pinnedPath IS NOT NULL")
    suspend fun markPinnedFileAsRemoteRemoved(driveFileId: String)

    @Query("DELETE FROM drive_index_files WHERE pinnedPath IS NULL AND (lastSeenFullSyncAtMs IS NULL OR lastSeenFullSyncAtMs != :generation)")
    suspend fun deleteUnseenAfterFullSync(generation: Long)

    @Query("UPDATE drive_index_files SET indexStatus = 'REMOTE_REMOVED', extractedText = '', extractionSource = NULL, embeddingVector = NULL, embeddingModel = NULL, embeddingVersion = NULL, embeddingDimension = NULL WHERE pinnedPath IS NOT NULL AND (lastSeenFullSyncAtMs IS NULL OR lastSeenFullSyncAtMs != :generation)")
    suspend fun markUnseenPinsAfterFullSync(generation: Long)

    @Query("DELETE FROM drive_index_files WHERE pinnedPath IS NULL")
    suspend fun clearUnpinnedDriveRecords()

    @Query("UPDATE drive_index_files SET indexStatus = 'REMOTE_REMOVED', extractedText = '', extractionSource = NULL, embeddingVector = NULL, embeddingModel = NULL, embeddingVersion = NULL, embeddingDimension = NULL WHERE pinnedPath IS NOT NULL")
    suspend fun markPinsFromPreviousAccount()

    @Query("SELECT * FROM drive_sync_state WHERE id = 1 LIMIT 1")
    suspend fun getSyncState(): DriveSyncStateEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun saveSyncState(state: DriveSyncStateEntity)

}
