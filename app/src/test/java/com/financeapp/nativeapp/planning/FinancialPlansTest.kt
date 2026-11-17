package com.financeapp.nativeapp.planning

import com.financeapp.nativeapp.data.local.entity.FinancePlanEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class FinancialPlansTest {
    private val anchor = Calendar.getInstance().apply {
        set(2026, Calendar.JANUARY, 31, 9, 0, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun plan(count: Int, total: Double = 100.0) = FinancePlanEntity(
        id = "test", title = "Compra", totalAmount = total,
        type = "DESPESA", category = "Compras", startAt = anchor,
        mode = "INSTALLMENTS", installmentCount = count
    )

    @Test fun installmentsPreserveTotalCents() {
        val p = plan(3)
        assertEquals(33.34, FinancialPlans.amountFor(p, 0), 0.00001)
        assertEquals(33.33, FinancialPlans.amountFor(p, 1), 0.00001)
        assertEquals(100.0, (0..2).sumOf { FinancialPlans.amountFor(p, it) }, 0.00001)
        val p60 = plan(60, 1234.56)
        assertEquals(1234.56, (0 until 60).sumOf { FinancialPlans.amountFor(p60, it) }, 0.00001)
    }

    @Test fun monthEndDoesNotDrift() {
        val february = Calendar.getInstance().apply { timeInMillis = FinancialPlans.monthAt(anchor, 1) }
        val march = Calendar.getInstance().apply { timeInMillis = FinancialPlans.monthAt(anchor, 2) }
        assertEquals(Calendar.FEBRUARY, february.get(Calendar.MONTH))
        assertEquals(28, february.get(Calendar.DAY_OF_MONTH))
        assertEquals(31, march.get(Calendar.DAY_OF_MONTH))
    }

    @Test fun sixtyInstallmentsAppearIndividually() {
        val p60 = plan(60)
        val list = FinancialPlans.occurrences(p60, emptyMap(), anchor)
        assertEquals(60, list.size)
        assertEquals(59, list.last().sequence)
    }

    @Test fun recurringNeverCreatesStoredFutureIncomeAutomatically() {
        val p = plan(1).copy(mode = "MONTHLY", type = "RECEITA")
        val list = FinancialPlans.occurrences(p, emptyMap(), anchor)
        assertTrue(list.size >= 12)
        assertTrue(list.all { it.payment == null })
    }
}
