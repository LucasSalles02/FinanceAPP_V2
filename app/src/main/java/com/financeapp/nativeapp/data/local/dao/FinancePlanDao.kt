package com.financeapp.nativeapp.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import com.financeapp.nativeapp.data.local.entity.FinancePlanEntity
import com.financeapp.nativeapp.data.local.entity.FinancePlanPaymentEntity
import com.financeapp.nativeapp.data.local.entity.PendingTransactionDeletionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FinancePlanDao {
    @Query("SELECT COUNT(*) FROM finance_plans WHERE ownerUid = :uid")
    suspend fun countPlans(uid: String): Int

    @Query("SELECT * FROM finance_plans WHERE ownerUid = :uid ORDER BY startAt DESC")
    fun observePlans(uid: String): Flow<List<FinancePlanEntity>>

    @Query("SELECT * FROM finance_plan_payments WHERE ownerUid = :uid")
    fun observePayments(uid: String): Flow<List<FinancePlanPaymentEntity>>

    @Query("SELECT * FROM finance_plans WHERE id = :id AND ownerUid = :uid LIMIT 1")
    suspend fun findPlan(id: String, uid: String): FinancePlanEntity?

    @Query("SELECT * FROM finance_plan_payments WHERE planId = :id AND sequence = :sequence AND ownerUid = :uid LIMIT 1")
    suspend fun findPayment(id: String, sequence: Int, uid: String): FinancePlanPaymentEntity?

    @Query("SELECT transactionId FROM finance_plan_payments WHERE planId = :id AND ownerUid = :uid")
    suspend fun linkedTransactionIds(id: String, uid: String): List<String>

    @Query("SELECT id FROM transactions WHERE ownerUid = :uid AND substr(id, 1, length(:prefix)) = :prefix")
    suspend fun transactionIdsWithPrefix(prefix: String, uid: String): List<String>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun queueRemoteDeletions(deletions: List<PendingTransactionDeletionEntity>)

    @Query("DELETE FROM transactions WHERE ownerUid = :uid AND id IN (:ids)")
    suspend fun deleteLinkedTransactions(ids: List<String>, uid: String)

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun addPlan(plan: FinancePlanEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addPayment(payment: FinancePlanPaymentEntity)

    @Query("DELETE FROM finance_plan_payments WHERE transactionId = :transactionId AND ownerUid = :uid")
    suspend fun removePaymentByTransactionId(transactionId: String, uid: String)

    @Query("DELETE FROM finance_plan_payments WHERE planId = :id AND sequence = :sequence AND ownerUid = :uid")
    suspend fun removePayment(id: String, sequence: Int, uid: String)

    @Query("SELECT COUNT(*) FROM finance_plan_payments WHERE planId = :id AND ownerUid = :uid")
    suspend fun paymentCount(id: String, uid: String): Int

    @Query("DELETE FROM finance_plans WHERE id = :id AND ownerUid = :uid")
    suspend fun deletePlan(id: String, uid: String)

    @Query("DELETE FROM finance_plan_payments WHERE planId = :id AND ownerUid = :uid")
    suspend fun removeAllPayments(id: String, uid: String)

    /** Exclusão atômica: não toca nas parcelas nem transações de outras contas. */
    @Transaction
    suspend fun deletePlanWithLinkedTransactions(id: String, uid: String) {
        check(findPlan(id, uid) != null) { "Programação não encontrada nesta conta." }
        val ids = (linkedTransactionIds(id, uid) + transactionIdsWithPrefix("plan_${id}_", uid)).distinct()
        if (ids.isNotEmpty()) {
            queueRemoteDeletions(ids.map { PendingTransactionDeletionEntity(it, ownerUid = uid) })
            deleteLinkedTransactions(ids, uid)
        }
        removeAllPayments(id, uid)
        deletePlan(id, uid)
    }
}
