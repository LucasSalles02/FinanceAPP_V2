package com.financeapp.nativeapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.financeapp.nativeapp.data.local.entity.PendingTransactionDeletionEntity

@Dao
interface PendingDeletionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun queue(deletion: PendingTransactionDeletionEntity)

    @Query("SELECT * FROM pending_transaction_deletions WHERE ownerUid = :uid ORDER BY queuedAt")
    suspend fun pending(uid: String): List<PendingTransactionDeletionEntity>

    @Query("DELETE FROM pending_transaction_deletions WHERE id = :id AND ownerUid = :uid")
    suspend fun clear(id: String, uid: String)
}
