package com.financeapp.nativeapp.planning

import com.financeapp.nativeapp.data.local.dao.FinancePlanDao
import com.financeapp.nativeapp.data.remote.AccountScope
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import com.financeapp.nativeapp.data.local.entity.FinancePlanEntity
import com.financeapp.nativeapp.data.local.entity.FinancePlanPaymentEntity
import com.financeapp.nativeapp.domain.model.Transaction
import com.financeapp.nativeapp.domain.model.TransactionType
import com.financeapp.nativeapp.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import java.util.UUID
import java.math.BigDecimal
import java.math.RoundingMode

class FinancialPlanRepository(
    private val dao: FinancePlanDao,
    private val transactions: TransactionRepository,
    private val onDeletionQueued: () -> Unit = {}
) {
    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun plans(): Flow<List<FinancePlanEntity>> = AccountScope.uid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else dao.observePlans(uid)
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    fun payments(): Flow<List<FinancePlanPaymentEntity>> = AccountScope.uid.flatMapLatest { uid ->
        if (uid == null) flowOf(emptyList()) else dao.observePayments(uid)
    }

    suspend fun add(
        title: String,
        amount: Double,
        type: TransactionType,
        category: String,
        firstDueAt: Long,
        mode: String,
        installments: Int
    ): Result<Unit> = runCatching {
        require(title.isNotBlank() && category.isNotBlank() && amount > 0 && amount.isFinite())
        require(mode == "MONTHLY" || (mode == "INSTALLMENTS" && type == TransactionType.DESPESA && installments in 2..60))
        if (mode == "INSTALLMENTS") {
            val cents = BigDecimal.valueOf(amount).setScale(2, RoundingMode.HALF_UP)
                .movePointRight(2).longValueExact()
            require(cents >= installments) { "O valor total deve permitir ao menos R$ 0,01 por parcela." }
        }
        val uid = AccountScope.requireUid()
        dao.addPlan(FinancePlanEntity(
            id = UUID.randomUUID().toString(),
            title = title.trim(), totalAmount = amount,
            type = type.name, category = category.trim(),
            startAt = firstDueAt, mode = mode,
            installmentCount = if (mode == "INSTALLMENTS") installments else 1,
            ownerUid = uid
        ))
    }

    suspend fun setPaid(entry: DueEntry, paid: Boolean): Result<Unit> = runCatching {
        val uid = AccountScope.requireUid()
        val plan = dao.findPlan(entry.plan.id, uid) ?: error("Conta não encontrada")
        if (plan.mode == "INSTALLMENTS" && entry.sequence !in 0 until plan.installmentCount) error("Parcela inválida")
        val current = dao.findPayment(plan.id, entry.sequence, uid)
        val id = FinancialPlans.transactionId(plan.id, entry.sequence)
        if (paid && current == null) {
            // A data real do pagamento/recebimento é única para o histórico e o extrato.
            val paidAt = System.currentTimeMillis()
            val tx = Transaction(
                id = id, userId = uid,
                title = if (plan.mode == "INSTALLMENTS") "${plan.title} (${entry.sequence + 1}/${plan.installmentCount})" else plan.title,
                amount = FinancialPlans.amountFor(plan, entry.sequence),
                type = TransactionType.valueOf(plan.type),
                category = plan.category,
                date = paidAt,
                paidAt = paidAt
            )
            transactions.addTransaction(tx).getOrThrow()
            try {
                check(AccountScope.requireUid() == uid) { "Conta alterada durante o pagamento." }
                dao.addPayment(FinancePlanPaymentEntity(plan.id, entry.sequence, paidAt, id, ownerUid = uid))
            } catch (e: Exception) {
                if (AccountScope.uid.value == uid) transactions.deleteTransaction(id)
                throw e
            }
        } else if (!paid && current != null) {
            transactions.deleteTransaction(id).getOrThrow()
            dao.removePayment(plan.id, entry.sequence, uid)
        }
    }

    /** Exclui a programação e seus lançamentos confirmados, incluindo a fila de exclusão remota. */
    suspend fun deletePlan(id: String): Result<Unit> = runCatching {
        dao.deletePlanWithLinkedTransactions(id, AccountScope.requireUid())
        // O saldo atualiza imediatamente pelo Room. A limpeza no Firebase será
        // feita em segundo plano, inclusive se a exclusão ocorreu sem internet.
        // Eventual falha ao enfileirar sincronização não desfaz a exclusão local.
        runCatching { onDeletionQueued() }
    }
}
