package com.financeapp.nativeapp.domain.model

data class Transaction(
    val id: String,
    val userId: String,
    val title: String,
    val amount: Double,
    val type: TransactionType,
    val category: String,
    val date: Long,
    val location: String? = null,
    val receiptLocalUri: String? = null,
    val receiptRemoteUrl: String? = null,
    val paidAt: Long? = null, // Despesas avulsas: confirmação sem mudar o vencimento original.
    val syncStatus: SyncStatus = SyncStatus.PENDENTE,
    val lastModifiedAt: Long = System.currentTimeMillis()
)
