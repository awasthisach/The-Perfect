package com.vvf.smartmanager.core.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.vvf.smartmanager.core.database.model.FileMetadataEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchFtsDao {

    /**
     * Sub-millisecond FTS4 match query joining virtual table file_fts with file_metadata.
     */
    @Query("""
        SELECT m.* FROM file_metadata m
        JOIN file_fts f ON m.id = f.rowid
        WHERE file_fts MATCH :searchQuery AND m.isTrash = 0
        ORDER BY m.modifiedDate DESC
    """)
    fun searchFilesFts(searchQuery: String): Flow<List<FileMetadataEntity>>

    /**
     * Standard LIKE fallback query for partial substring matches.
     */
    @Query("""
        SELECT * FROM file_metadata 
        WHERE (name LIKE '%' || :query || '%' OR tags LIKE '%' || :query || '%') 
        AND isTrash = 0 
        ORDER BY isDirectory DESC, name ASC
    """)
    fun searchFilesFallback(query: String): Flow<List<FileMetadataEntity>>

    /**
     * Tag-based filtering query.
     */
    @Query("""
        SELECT * FROM file_metadata 
        WHERE tags LIKE '%' || :tag || '%' AND isTrash = 0 
        ORDER BY modifiedDate DESC
    """)
    fun searchByTag(tag: String): Flow<List<FileMetadataEntity>>

    /**
     * Retrieve all unique tag strings.
     */
    @Query("SELECT DISTINCT tags FROM file_metadata WHERE tags != '' AND isTrash = 0")
    fun getAllTags(): Flow<List<String>>

    /**
     * Update tags for a specific file.
     */
    @Query("UPDATE file_metadata SET tags = :tags WHERE path = :path")
    suspend fun updateTagsByPath(path: String, tags: String)

    /**
     * Rebuild FTS index directly from file_metadata content table.
     */
    @Query("INSERT INTO file_fts(file_fts) VALUES('rebuild')")
    suspend fun rebuildFtsIndex()

    /**
     * Get live stream of total indexed non-trashed files.
     */
    @Query("SELECT COUNT(*) FROM file_metadata WHERE isTrash = 0")
    fun getTotalIndexedCount(): Flow<Int>
}
