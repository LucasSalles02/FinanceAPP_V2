package com.financeapp.nativeapp.reminders

import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Calendar

class ReminderRecurrenceTest {
    @Test fun monthlyReminderKeepsTheOriginalDayOfMonth() {
        val january = Calendar.getInstance().apply {
            set(2026, Calendar.JANUARY, 31, 16, 10, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val februaryTime = ReminderRecurrence.nextAfter(january, january)!!
        val february = Calendar.getInstance().apply { timeInMillis = februaryTime }
        assertEquals(28, february.get(Calendar.DAY_OF_MONTH))
        val march = Calendar.getInstance().apply {
            timeInMillis = ReminderRecurrence.nextAfter(january, februaryTime)!!
        }
        assertEquals(Calendar.MARCH, march.get(Calendar.MONTH))
        assertEquals(31, march.get(Calendar.DAY_OF_MONTH))
    }
}
