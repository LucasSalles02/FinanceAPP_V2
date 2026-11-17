package com.financeapp.nativeapp.data.remote

import com.financeapp.nativeapp.data.remote.dto.TransactionDto
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

/** Cada operação recebe o UID original e não utiliza o UID de outra sessão. */
class FirebaseRemoteDataSourceImpl : RemoteTransactionDataSource {
    private val db = FirebaseFirestore.getInstance()

    private fun collection(uid: String) = db.collection("users").document(uid).collection("transactions")

    private fun assertSession(uid: String) {
        check(FirebaseAuth.getInstance().currentUser?.uid == uid) {
            "Conta alterada durante o acesso ao Firebase."
        }
    }

    override suspend fun fetchTransactions(uid: String): Result<List<TransactionDto>> = try {
        assertSession(uid)
        val snapshots = collection(uid).get().await()
        assertSession(uid)
        val rows = snapshots.documents.mapNotNull { doc ->
            val amount = doc.getDouble("amount") ?: return@mapNotNull null
            val date = doc.getLong("date") ?: return@mapNotNull null
            val type = doc.getString("type") ?: return@mapNotNull null
            TransactionDto(
                id = doc.id, userId = uid,
                title = doc.getString("title") ?: return@mapNotNull null,
                amount = amount, type = type,
                category = doc.getString("category") ?: "Outros", date = date,
                location = doc.getString("location"),
                receiptRemoteUrl = doc.getString("receiptRemoteUrl"),
                paidAt = doc.getLong("paidAt"),
                lastModifiedAt = doc.getLong("lastModifiedAt") ?: 0L
            )
        }
        Result.success(rows)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun deleteTransaction(id: String, uid: String): Result<Unit> = try {
        assertSession(uid)
        collection(uid).document(id).delete().await()
        assertSession(uid)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }

    override suspend fun sendTransaction(dto: TransactionDto, uid: String): Result<Unit> = try {
        assertSession(uid)
        val payload = mapOf(
            "id" to dto.id, "userId" to uid, "title" to dto.title,
            "amount" to dto.amount, "type" to dto.type, "category" to dto.category,
            "date" to dto.date, "location" to dto.location,
            "receiptRemoteUrl" to dto.receiptRemoteUrl,
            "paidAt" to dto.paidAt,
            "lastModifiedAt" to dto.lastModifiedAt
        )
        collection(uid).document(dto.id).set(payload).await()
        assertSession(uid)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.failure(e)
    }
}
