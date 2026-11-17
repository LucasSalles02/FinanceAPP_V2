package com.financeapp.nativeapp.domain.model

import com.financeapp.nativeapp.data.remote.dto.toDto
import org.junit.Assert.*
import org.junit.Test

class TransactionPaymentStatusTest {
    private val original = Transaction(
        id = "test-1", userId = "owner-a", title = "Conta de luz",
        amount = 185.0, type = TransactionType.DESPESA,
        category = "Moradia", date = 1780000000000L
    )

    @Test fun paidAndUndonePreserveAmountAndDate() {
        assertNull(original.paidAt)
        val paid = original.copy(paidAt = 1780080000000L)
        assertEquals(1780080000000L, paid.paidAt)
        assertEquals(original.date, paid.date)
        assertEquals(original.amount, paid.amount, 0.0)
        val undone = paid.copy(paidAt = null)
        assertNull(undone.paidAt)
        assertEquals("owner-a", undone.userId)
    }

    @Test fun firestoreDtoIncludesPaymentStatus() {
        val dto = original.copy(paidAt = 1780080000000L).toDto()
        assertEquals("owner-a", dto.userId)
        assertEquals(1780080000000L, dto.paidAt)
    }
}
