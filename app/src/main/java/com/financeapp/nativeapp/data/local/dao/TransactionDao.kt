package com.financeapp.nativeapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.financeapp.nativeapp.data.local.entity.TransactionEntity
import com.financeapp.nativeapp.domain.model.SyncStatus
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT COUNT(*) FROM transactions WHERE ownerUid = :uid")
    suspend fun countTransactions(uid: String): Int

    /** Detecta um ID já pertencente a outra conta antes de importar da nuvem. */
    @Query("SELECT ownerUid FROM transactions WHERE id = :id LIMIT 1")
    suspend fun ownerOfId(id: String): String?

    @Query("SELECT * FROM transactions WHERE id = :id AND ownerUid = :uid LIMIT 1")
    suspend fun findById(id: String, uid: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE ownerUid = :uid ORDER BY date DESC")
    fun getAllTransactions(uid: String): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE ownerUid = :uid AND syncStatus = :status ORDER BY lastModifiedAt ASC")
    suspend fun getPendingTransactions(uid: String, status: SyncStatus = SyncStatus.PENDENTE): List<TransactionEntity>

    @Query("UPDATE transactions SET syncStatus = 'PENDENTE' WHERE ownerUid = :uid AND syncStatus != 'PENDENTE'")
    suspend fun requeueAllForNewCloud(uid: String)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(entity: TransactionEntity)

    @Query("UPDATE transactions SET syncStatus = :status WHERE id = :id AND ownerUid = :uid")
    suspend fun updateSyncStatus(id: String, uid: String, status: SyncStatus)

    @Query("DELETE FROM transactions WHERE id = :id AND ownerUid = :uid")
    suspend fun deleteById(id: String, uid: String)
}
