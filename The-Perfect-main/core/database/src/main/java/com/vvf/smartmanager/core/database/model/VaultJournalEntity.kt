package com.vvf.smartmanager.core.database.model

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "vault_journal")
data class VaultJournalEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val operationType: String, // "ENCRYPT", "DECRYPT", "SHRED"
    val originalPath: String,
    val vaultPath: String,
    val status: String, // "PENDING", "IN_PROGRESS", "COMPLETED", "FAILED"
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
