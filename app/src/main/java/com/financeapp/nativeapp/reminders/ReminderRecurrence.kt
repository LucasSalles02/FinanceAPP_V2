package com.financeapp.nativeapp.reminders

import java.util.Calendar

object ReminderRecurrence {
    /** Keeps the anchor day/month, clamps 29/30/31 to the shorter month's last day. */
    fun nextAfter(anchor: Long, after: Long): Long? {
        for (months in 1..2400) {
            val cal = Calendar.getInstance().apply {
                timeInMillis = anchor
                val day = get(Calendar.DAY_OF_MONTH)
                set(Calendar.DAY_OF_MONTH, 1)
                add(Calendar.MONTH, months)
                set(Calendar.DAY_OF_MONTH, minOf(day, getActualMaximum(Calendar.DAY_OF_MONTH)))
            }
            if (cal.timeInMillis > after) return cal.timeInMillis
        }
        return null
    }

    /** The triggered instance remains in history, the next gets a new ID. */
    fun createNext(context: android.content.Context, old: FinanceReminder, now: Long = System.currentTimeMillis()) {
        if (!old.repeatMonthly) return
        val nextAt = nextAfter(old.repeatAnchorAt, now) ?: return
        val id = ReminderStore.createId(context) ?: return
        val next = old.copy(id = id, scheduledAt = nextAt, status = ReminderStatus.SCHEDULED, lastEventAt = null)
        if (ReminderStore.add(context, next)) ReminderScheduler.schedule(context, next)
    }
}
