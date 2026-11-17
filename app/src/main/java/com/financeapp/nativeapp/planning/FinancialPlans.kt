package com.financeapp.nativeapp.planning

import com.financeapp.nativeapp.data.local.entity.FinancePlanEntity
import com.financeapp.nativeapp.data.local.entity.FinancePlanPaymentEntity
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Calendar

/** Each due is derived from an immutable first due date, preventing 31 -> 28 -> 28 drift. */
data class DueEntry(
    val plan: FinancePlanEntity,
    val sequence: Int,
    val dueAt: Long,
    val amount: Double,
    val payment: FinancePlanPaymentEntity?
)

object FinancialPlans {
    fun monthAt(anchor: Long, months: Int): Long = Calendar.getInstance().run {
        timeInMillis = anchor
        val day = get(Calendar.DAY_OF_MONTH)
        set(Calendar.DAY_OF_MONTH, 1)
        add(Calendar.MONTH, months)
        val maxDay = getActualMaximum(Calendar.DAY_OF_MONTH)
        set(Calendar.DAY_OF_MONTH, minOf(day, maxDay))
        timeInMillis
    }

    fun amountFor(plan: FinancePlanEntity, index: Int): Double {
        if (plan.mode != "INSTALLMENTS") return plan.totalAmount
        val cents = BigDecimal.valueOf(plan.totalAmount).setScale(2, RoundingMode.HALF_UP)
            .movePointRight(2).longValueExact()
        val total = plan.installmentCount.coerceIn(1, 60)
        val base = cents / total
        val extra = cents % total
        return (base + if (index < extra) 1 else 0) / 100.0
    }

    fun occurrences(
        plan: FinancePlanEntity,
        payments: Map<Int, FinancePlanPaymentEntity>,
        now: Long = System.currentTimeMillis()
    ): List<DueEntry> {
        val max = if (plan.mode == "INSTALLMENTS") plan.installmentCount.coerceIn(1, 60)
            else 1200 // recurring calculated without persisted future transactions
        // Show paid history, all overdue months and three upcoming months.
        val until = maxOf(monthAt(now, 12), plan.startAt)
        return buildList {
            for (i in 0 until max) {
                val due = monthAt(plan.startAt, i)
                if (plan.mode != "INSTALLMENTS" && due > until) break
                add(DueEntry(plan, i, due, amountFor(plan, i), payments[i]))
            }
        }
    }

    fun transactionId(planId: String, sequence: Int) = "plan_${planId}_$sequence"
}
