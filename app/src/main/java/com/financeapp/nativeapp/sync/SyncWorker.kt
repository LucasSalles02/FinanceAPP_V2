package com.financeapp.nativeapp.sync

import android.content.Context
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.financeapp.nativeapp.data.local.AppDatabase
import com.financeapp.nativeapp.data.local.entity.TransactionEntity
import com.financeapp.nativeapp.data.remote.AccountScope
import com.financeapp.nativeapp.data.remote.FirebaseRemoteDataSourceImpl
import com.financeapp.nativeapp.data.repository.TransactionRepositoryImpl
import com.financeapp.nativeapp.domain.model.SyncStatus
import com.financeapp.nativeapp.domain.model.TransactionType
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth

class SyncWorker(appContext: Context, workerParams: WorkerParameters) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        if (OfflineMode.isEnabled(applicationContext)) return Result.success()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: return Result.success()
        if (AccountScope.uid.value != uid) return Result.success()
        val database = AppDatabase.getDatabase(applicationContext)
        val dao = database.transactionDao()
        val remote = FirebaseRemoteDataSourceImpl()
        val repo = TransactionRepositoryImpl(dao, remote, database.pendingDeletionDao(), database.financePlanDao())
        return try {
            val project = FirebaseApp.getInstance().options.projectId ?: error("Projeto Firebase ausente")
            val prefs = applicationContext.getSharedPreferences("financeapp_cloud_migration", Context.MODE_PRIVATE)
            val marker = "uploaded_${project}_$uid"
            if (!prefs.getBoolean(marker, false)) dao.requeueAllForNewCloud(uid)
            if (OfflineMode.isEnabled(applicationContext)) return Result.success()
            if (FirebaseAuth.getInstance().currentUser?.uid != uid) return Result.success()
            repo.syncPending().getOrElse { throw it }
            if (FirebaseAuth.getInstance().currentUser?.uid != uid) return Result.success()
            if (!prefs.getBoolean(marker, false)) {
                check(prefs.edit().putBoolean(marker, true).commit()) { "Falha no checkpoint da sincronização" }
            }
            // Baixa apenas o conjunto de documentos desta conta; não toca no cache da outra.
            val fetched = remote.fetchTransactions(uid).getOrElse { throw it }
            if (FirebaseAuth.getInstance().currentUser?.uid != uid) return Result.success()
            for (dto in fetched) {
                if (FirebaseAuth.getInstance().currentUser?.uid != uid) return Result.success()
                // V11 podia ter enviado o cache de uma conta à nuvem de outra.
                // Nunca importar um documento cujo ID já pertence a outro UID local.
                val knownOwner = dao.ownerOfId(dto.id)
                if (knownOwner != null && knownOwner != uid) continue
                val local = dao.findById(dto.id, uid)
                // Never overwrite unsent local edits with an older Firestore snapshot.
                if (local != null && (
                        local.syncStatus != SyncStatus.SINCRONIZADO ||
                        dto.lastModifiedAt <= 0L || dto.lastModifiedAt <= local.lastModifiedAt
                    )) continue
                val type = runCatching { TransactionType.valueOf(dto.type) }.getOrNull() ?: continue
                dao.insertOrUpdate(TransactionEntity(
                    id = dto.id, userId = uid, ownerUid = uid, title = dto.title,
                    amount = dto.amount, type = type, category = dto.category,
                    date = dto.date, location = dto.location, receiptRemoteUrl = dto.receiptRemoteUrl,
                    paidAt = dto.paidAt, syncStatus = SyncStatus.SINCRONIZADO,
                    lastModifiedAt = dto.lastModifiedAt.takeIf { it > 0L } ?: System.currentTimeMillis()
                ))
            }
            Result.success()
        } catch (e: Exception) {
            Log.w("FinanceAppSync", "Sincronização adiada para conta ativa", e)
            Result.retry()
        }
    }

    companion object { const val WORK_NAME = "FinanceAppSyncWorker" }
}
