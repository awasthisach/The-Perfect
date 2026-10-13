package com.vvf.smartmanager.core.database.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.vvf.smartmanager.core.database.model.SemanticEmbeddingEntity

@Dao
interface SemanticEmbeddingDao {
    @Query("SELECT * FROM semantic_embeddings WHERE filePath = :filePath LIMIT 1")
    suspend fun getByPath(filePath: String): SemanticEmbeddingEntity?

    @Query("SELECT * FROM semantic_embeddings WHERE filePath IN (:filePaths)")
    suspend fun getByPaths(filePaths: List<String>): List<SemanticEmbeddingEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SemanticEmbeddingEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertAll(entities: List<SemanticEmbeddingEntity>)

    @Query("DELETE FROM semantic_embeddings WHERE filePath = :filePath")
    suspend fun deleteByPath(filePath: String)

    @Query("DELETE FROM semantic_embeddings")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM semantic_embeddings")
    suspend fun count(): Int
}
