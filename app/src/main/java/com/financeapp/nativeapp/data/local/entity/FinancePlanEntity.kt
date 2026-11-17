package com.financeapp.nativeapp.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "finance_plans")
data class FinancePlanEntity(
    @PrimaryKey val id: String,
    val title: String,
    val totalAmount: Double,
    val type: String,
    val category: String,
    val startAt: Long,
    val mode: String, // MONTHLY or INSTALLMENTS
    val installmentCount: Int = 1,
    val createdAt: Long = System.currentTimeMillis(),
    @androidx.room.ColumnInfo(defaultValue = "''") val ownerUid: String = ""
)

@Entity(tableName = "finance_plan_payments", primaryKeys = ["planId", "sequence"])
data class FinancePlanPaymentEntity(
    val planId: String,
    val sequence: Int,
    val paidAt: Long,
    val transactionId: String,
    @androidx.room.ColumnInfo(defaultValue = "''") val ownerUid: String = ""
)

@Entity(tableName = "pending_transaction_deletions")
data class PendingTransactionDeletionEntity(
    @PrimaryKey val id: String,
    val queuedAt: Long = System.currentTimeMillis(),
    @androidx.room.ColumnInfo(defaultValue = "''") val ownerUid: String = ""
)
