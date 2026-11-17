package com.financeapp.nativeapp.data.repository

import com.financeapp.nativeapp.data.local.dao.TransactionDao
import com.financeapp.nativeapp.data.local.dao.PendingDeletionDao
import com.financeapp.nativeapp.data.local.dao.FinancePlanDao
import com.financeapp.nativeapp.data.local.entity.PendingTransactionDeletionEntity
import com.financeapp.nativeapp.data.local.entity.toDomain
import com.financeapp.nativeapp.data.local.entity.toEntity
import com.financeapp.nativeapp.data.remote.AccountScope
import com.financeapp.nativeapp.data.remote.RemoteTransactionDataSource
import com.financeapp.nativeapp.data.remote.dto.toDto
import com.financeapp.nativeapp.domain.model.SyncStatus
import com.financeapp.nativeapp.domain.model.Transaction
import com.financeapp.nativeapp.domain.repository.TransactionRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

class TransactionRepositoryImpl(
    private val dao: TransactionDao,
    private val remoteSource: RemoteTransactionDataSource,
    private val deletionDao: PendingDeletionDao,
    private val planDao: FinancePlanDao,
    private val onLocalChange: () -> Unit = {}
) : TransactionRepository {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun getTransactions(): Flow<List<Transaction>> = AccountScope.uid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList())
        else dao.getAllTransactions(uid).map { list -> list.map { it.toDomain() } }
    }

    override suspend fun addTransaction(transaction: Transaction): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = AccountScope.requireUid()
            // Nunca permitir que userId fixo de versões antigas determine o dono.
            val pending = transaction.copy(userId = uid, syncStatus = SyncStatus.PENDENTE)
            dao.insertOrUpdate(pending.toEntity().copy(ownerUid = uid))
            runCatching { onLocalChange() }
            Unit
        }
    }

    override suspend fun updateTransaction(transaction: Transaction): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = AccountScope.requireUid()
            require(dao.findById(transaction.id, uid) != null) { "Transação não pertence à conta atual." }
            val pending = transaction.copy(userId = uid, syncStatus = SyncStatus.PENDENTE,
                lastModifiedAt = System.currentTimeMillis())
            dao.insertOrUpdate(pending.toEntity().copy(ownerUid = uid))
            runCatching { onLocalChange() }
            Unit
        }
    }

    override suspend fun deleteTransaction(id: String): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = AccountScope.requireUid()
            require(dao.findById(id, uid) != null) { "Transação não pertence à conta atual." }
            deletionDao.queue(PendingTransactionDeletionEntity(id, ownerUid = uid))
            dao.deleteById(id, uid)
            planDao.removePaymentByTransactionId(id, uid)
            runCatching { onLocalChange() }
            Unit
        }
    }

    override suspend fun syncPending(): Result<Int> = withContext(Dispatchers.IO) {
        runCatching {
            val uid = AccountScope.requireUid()
            var successCount = 0
            var failures = false
            for (item in deletionDao.pending(uid)) {
                check(AccountScope.requireUid() == uid) { "Conta alterada durante a sincronização." }
                if (remoteSource.deleteTransaction(item.id, uid).isSuccess) {
                    deletionDao.clear(item.id, uid)
                    successCount++
                } else failures = true
            }
            for (entity in dao.getPendingTransactions(uid)) {
                check(AccountScope.requireUid() == uid) { "Conta alterada durante a sincronização." }
                dao.updateSyncStatus(entity.id, uid, SyncStatus.SINCRONIZANDO)
                val result = remoteSource.sendTransaction(entity.toDomain().toDto(), uid)
                if (result.isSuccess) {
                    dao.updateSyncStatus(entity.id, uid, SyncStatus.SINCRONIZADO)
                    successCount++
                } else {
                    dao.updateSyncStatus(entity.id, uid, SyncStatus.PENDENTE)
                    failures = true
                }
            }
            if (failures) error("Ainda existem operações pendentes no Firebase")
            successCount
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    override fun getPendingCount(): Flow<Int> = AccountScope.uid.flatMapLatest { uid ->
        if (uid == null) flowOf(0)
        else dao.getAllTransactions(uid).map { list -> list.count { it.syncStatus == SyncStatus.PENDENTE } }
    }
}
