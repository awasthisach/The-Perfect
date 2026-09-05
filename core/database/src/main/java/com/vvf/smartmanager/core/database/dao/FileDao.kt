package com.vvf.smartmanager.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import kotlinx.coroutines.flow.Flow

data class DuplicateGroup(
    val sizeBytes: Long,
    val count: Int
)

data class DuplicateHashGroup(
    val md5Hash: String,
    val count: Int
)

data class StorageCategorySummary(
    val mimeTypePrefix: String,
    val totalBytes: Long,
    val fileCount: Int
)

@Dao
interface FileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(file: FileMetadataEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(files: List<FileMetadataEntity>)

    @Update
    suspend fun update(file: FileMetadataEntity)

    @Query("SELECT * FROM file_metadata WHERE path = :path LIMIT 1")
    suspend fun getByPath(path: String): FileMetadataEntity?

    /** One-shot path→row map for indexer; avoids N+1 getByPath during bulk upsert. */
    @Query("SELECT path, id, isFavorite, tags, md5Hash, originalPath, deletedTimestamp, operationState FROM file_metadata WHERE isTrash = 0")
    suspend fun getIndexedPathSnapshot(): List<FilePathSnapshot>

    @Query("SELECT * FROM file_metadata WHERE parentPath = :parentPath AND isTrash = 0 ORDER BY isDirectory DESC, name ASC")
    fun getFilesByDirectory(parentPath: String): Flow<List<FileMetadataEntity>>

    @Query("SELECT * FROM file_metadata WHERE isFavorite = 1 AND isTrash = 0 ORDER BY name ASC")
    fun getFavorites(): Flow<List<FileMetadataEntity>>

    @Query("SELECT * FROM file_metadata WHERE isTrash = 1 ORDER BY deletedTimestamp DESC, modifiedDate DESC")
    fun getTrashFiles(): Flow<List<FileMetadataEntity>>

    @Query("SELECT * FROM file_metadata WHERE mimeType LIKE :mimeTypePrefix || '%' AND isTrash = 0 ORDER BY modifiedDate DESC")
    fun getFilesByType(mimeTypePrefix: String): Flow<List<FileMetadataEntity>>

    @Query("SELECT * FROM file_metadata WHERE isTrash = 0 ORDER BY modifiedDate DESC LIMIT :limit")
    fun getRecentFiles(limit: Int = 50): Flow<List<FileMetadataEntity>>

    // Level 1 Duplicate Detection: Find files with identical sizeBytes (> 0)
    @Query("SELECT sizeBytes, COUNT(*) as count FROM file_metadata WHERE isDirectory = 0 AND isTrash = 0 AND sizeBytes > 0 GROUP BY sizeBytes HAVING count > 1 ORDER BY sizeBytes DESC")
    fun findPotentialDuplicateSizes(): Flow<List<DuplicateGroup>>

    @Query("SELECT * FROM file_metadata WHERE sizeBytes = :sizeBytes AND isDirectory = 0 AND isTrash = 0")
    suspend fun getFilesBySize(sizeBytes: Long): List<FileMetadataEntity>

    // Level 2 Duplicate Detection: Find files with identical MD5 hashes
    @Query("SELECT md5Hash, COUNT(*) as count FROM file_metadata WHERE md5Hash IS NOT NULL AND isDirectory = 0 AND isTrash = 0 GROUP BY md5Hash HAVING count > 1")
    fun findDuplicateHashes(): Flow<List<DuplicateHashGroup>>

    @Query("SELECT * FROM file_metadata WHERE md5Hash = :hash AND isDirectory = 0 AND isTrash = 0")
    suspend fun getFilesByHash(hash: String): List<FileMetadataEntity>

    @Query("SELECT * FROM file_metadata WHERE md5Hash IS NULL AND isDirectory = 0 AND isTrash = 0 AND sizeBytes > 0")
    suspend fun getFilesNeedingHash(): List<FileMetadataEntity>

    @Query("UPDATE file_metadata SET md5Hash = :hash WHERE id = :id")
    suspend fun updateFileHash(id: Long, hash: String)

    @Query("UPDATE file_metadata SET md5Hash = :hash WHERE path = :path")
    suspend fun updateFileHashByPath(path: String, hash: String)

    @Query("UPDATE file_metadata SET isTrash = :isTrash, originalPath = :originalPath, deletedTimestamp = :deletedTimestamp WHERE path = :path")
    suspend fun markAsTrash(path: String, isTrash: Boolean, originalPath: String?, deletedTimestamp: Long?)

    @Query("UPDATE file_metadata SET isTrash = 0, originalPath = NULL, deletedTimestamp = NULL WHERE path = :path")
    suspend fun restoreFromTrash(path: String)

    @Query("UPDATE file_metadata SET isFavorite = :isFavorite WHERE path = :path")
    suspend fun setFavoriteStatusByPath(path: String, isFavorite: Boolean)

    @Query("DELETE FROM file_metadata WHERE path = :path")
    suspend fun deleteByPath(path: String)

    @Query("DELETE FROM file_metadata WHERE isTrash = 1")
    suspend fun emptyTrash()

    @Query("SELECT COUNT(*) FROM file_metadata WHERE isTrash = 0")
    suspend fun getTotalFileCount(): Int

    @Query("SELECT SUM(sizeBytes) FROM file_metadata WHERE isTrash = 0")
    suspend fun getTotalStorageUsed(): Long?
}
