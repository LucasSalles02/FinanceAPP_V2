package com.financeapp.nativeapp.data.remote

import com.financeapp.nativeapp.data.remote.dto.TransactionDto
import kotlinx.coroutines.delay

interface RemoteTransactionDataSource {
    suspend fun sendTransaction(dto: TransactionDto, uid: String): Result<Unit>
    suspend fun deleteTransaction(id: String, uid: String): Result<Unit> = Result.success(Unit)
    suspend fun fetchTransactions(uid: String): Result<List<TransactionDto>> = Result.failure(
        UnsupportedOperationException("Leitura remota não implementada"))
}

/** Fonte simulada usada apenas em testes; o aplicativo usa FirebaseRemoteDataSourceImpl. */
class MockRemoteDataSourceImpl : RemoteTransactionDataSource {
    override suspend fun sendTransaction(dto: TransactionDto, uid: String): Result<Unit> {
        delay(600)
        return Result.success(Unit)
    }
}
