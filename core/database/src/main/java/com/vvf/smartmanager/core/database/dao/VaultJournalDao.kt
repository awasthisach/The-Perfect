package com.vvf.smartmanager.core.database.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.vvf.smartmanager.core.database.model.VaultJournalEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface VaultJournalDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertJournal(journal: VaultJournalEntity): Long

    @Update
    suspend fun updateJournal(journal: VaultJournalEntity)

    @Delete
    suspend fun deleteJournal(journal: VaultJournalEntity)

    @Query("SELECT * FROM vault_journal WHERE status = :status ORDER BY createdAt ASC")
    suspend fun getPendingJournals(status: String = "PENDING"): List<VaultJournalEntity>

    @Query("SELECT * FROM vault_journal WHERE id = :id LIMIT 1")
    suspend fun getJournalById(id: Long): VaultJournalEntity?

    @Query("DELETE FROM vault_journal WHERE status = 'COMPLETED'")
    suspend fun purgeCompletedJournals()

    @Query("DELETE FROM vault_journal WHERE id = :id")
    suspend fun deleteJournalById(id: Long)
}
